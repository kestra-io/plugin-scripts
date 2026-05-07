package io.kestra.plugins.scripts.perl;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.perl.Commands;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        logQueue.addListener(logs::add);

        Commands commands = Commands.builder()
            .id("perl-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("perl -e 'print \"Test OK\";'")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, commands, ImmutableMap.of());
        ScriptOutput run = commands.run(runContext);
        assertThat(run.getExitCode(), is(0));

        String expectedLog = "Test OK";
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(expectedLog));
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains(expectedLog)), is(true));
    }
}