package io.kestra.plugin.scripts.julia;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/tests/scripts/julia/get.yml"),
            IOUtils.toInputStream(
                "@info \"hello there!\";",
                StandardCharsets.UTF_8
            )
        );

        Commands juliaCommands = Commands.builder()
            .id("julia-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("julia " + put.toString())))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, juliaCommands, ImmutableMap.of());
        ScriptOutput run = juliaCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(0));
        assertThat(run.getStdErrLineCount(), is(1));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(put.getPath()));
        assertThat(List.copyOf(logs).stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("hello there!")).count(), is(1L));
    }
}
