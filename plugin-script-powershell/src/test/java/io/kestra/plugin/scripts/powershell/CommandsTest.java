package io.kestra.plugin.scripts.powershell;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.storages.StorageInterface;
import io.kestra.core.tenant.TenantService;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@KestraTest
class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    StorageInterface storageInterface;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        URI put = storageInterface.put(
            TenantService.MAIN_TENANT,
            null,
            new URI("/file/storage/get.yml"),
            IOUtils.toInputStream(
                "Get-ChildItem | Format-List",
                StandardCharsets.UTF_8
            )
        );

        Commands powershellCommands = Commands.builder()
            .id("powershell-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.of(List.of("pwsh " + put.toString())))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, powershellCommands, ImmutableMap.of());
        ScriptOutput run = powershellCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), greaterThan(1));
        assertThat(run.getStdErrLineCount(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(put.getPath()));
        receive.blockLast();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("FileVersion:")).count(), is(1L));
    }

    @Test
    void shouldExitOnFirstError() {
        Commands powershellCommands = Commands.builder()
            .id("powershell-should-exit-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.of(List.of("Get-ChildItem -Path \"NonexistentPath\"", "echo \"This is a message\"")))
            .failFast(Property.of(true))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, powershellCommands, ImmutableMap.of());
        Assertions.assertThrows(RunnableTaskException.class, () ->  powershellCommands.run(runContext));
    }

    @Test
    void shouldNotExitOnFirstError() throws Exception {
        Commands powershellCommands = Commands.builder()
            .id("powershell-should-not-exit-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.of(List.of("Get-ChildItem -Path \"NonexistentPath\"", "echo \"This is a message\"")))
            .failFast(Property.of(false))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, powershellCommands, ImmutableMap.of());
        ScriptOutput run = powershellCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), is(1));
        assertThat(run.getStdErrLineCount(), is(1));
    }
}
