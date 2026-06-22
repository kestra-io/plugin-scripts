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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void script() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script dotnetScript = Script.builder()
            .id("dotnet-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("Console.WriteLine(\"Hello from Kestra!\");"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetScript, ImmutableMap.of());
        ScriptOutput run = dotnetScript.run(runContext);

        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Hello from Kestra!"));
        receive.blockLast();
        assertThat(
            List.copyOf(logs).stream()
                .filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("Hello from Kestra!"))
                .count(),
            is(1L)
        );
    }

    @Test
    void scriptWithNugetDependency() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script dotnetScript = Script.builder()
            .id("dotnet-nuget-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("""
                #r "nuget:Newtonsoft.Json,13.0.3"
                using Newtonsoft.Json;
                var data = new { message = "Hello from NuGet" };
                Console.WriteLine(JsonConvert.SerializeObject(data));
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetScript, ImmutableMap.of());
        ScriptOutput run = dotnetScript.run(runContext);

        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("Hello from NuGet"));
        receive.blockLast();
        assertThat(
            List.copyOf(logs).stream()
                .filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("Hello from NuGet"))
                .count(),
            is(1L)
        );
    }

    @Test
    void scriptWithOutputFile() throws Exception {
        Script dotnetScript = Script.builder()
            .id("dotnet-output-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("File.WriteAllText(\"result.txt\", \"hello from dotnet\");"))
            .outputFiles(Property.ofValue(List.of("result.txt")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetScript, ImmutableMap.of());
        ScriptOutput run = dotnetScript.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getOutputFiles().containsKey("result.txt"), is(true));
        assertThat(run.getOutputFiles().get("result.txt").toString(), startsWith("kestra://"));
    }

    @Test
    void scriptFailure() {
        Script dotnetScript = Script.builder()
            .id("dotnet-fail-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("throw new Exception(\"deliberate failure\");"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, dotnetScript, ImmutableMap.of());
        Assertions.assertThrows(RunnableTaskException.class, () -> dotnetScript.run(runContext));
    }
}
