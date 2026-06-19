package io.kestra.plugin.scripts.dotnet;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Commands dotnetCommands = Commands.builder()
            .id("dotnet-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("dotnet --version")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetCommands, ImmutableMap.of());
        ScriptOutput run = dotnetCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), greaterThanOrEqualTo(1));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().matches("\\d+\\.\\d+\\.\\d+.*"));
        receive.blockLast();
    }

    @Test
    void shouldExitOnFirstError() {
        Commands dotnetCommands = Commands.builder()
            .id("dotnet-should-exit-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("dotnet nonexistent-command", "echo \"This should not run\"")))
            .failFast(Property.ofValue(true))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetCommands, ImmutableMap.of());
        Assertions.assertThrows(RunnableTaskException.class, () -> dotnetCommands.run(runContext));
    }

    @Test
    void shouldNotExitOnFirstError() throws Exception {
        Commands dotnetCommands = Commands.builder()
            .id("dotnet-should-not-exit-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("dotnet nonexistent-command", "echo \"This should still run\"")))
            .failFast(Property.ofValue(false))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetCommands, ImmutableMap.of());
        ScriptOutput run = dotnetCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));
        // dotnet nonexistent-command writes several error lines to stdout before the echo runs
        assertThat(run.getStdOutLineCount(), greaterThanOrEqualTo(1));
    }
}
