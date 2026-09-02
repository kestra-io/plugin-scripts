package io.kestra.plugins.scripts.deno;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.deno.Script;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import reactor.core.publisher.Flux;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class ScriptTest {

    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void script() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script script = Script.builder()
            .id("deno-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("console.log('Hello from deno script.');"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, ImmutableMap.of());
        ScriptOutput run = script.run(runContext);

        assertThat(run.getExitCode(), is(0));

        String expectedLog = "Hello from deno script.";
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(expectedLog));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains(expectedLog)), is(true));
    }

    @Test
    void envInputAndOutputFiles() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script script = Script.builder()
            .id("deno-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .env(Property.ofValue(Map.of("MY_VAR", "hello")))
            .inputFiles(Map.of("in.txt", "world"))
            .outputFiles(Property.ofValue(List.of("out.txt")))
            .script(Property.ofValue("""
                console.log("ENV=" + Deno.env.get("MY_VAR"));
                console.log("INPUT=" + Deno.readTextFileSync("in.txt").trim());
                Deno.writeTextFileSync("out.txt", "output content");
                """))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, script, ImmutableMap.of());
        ScriptOutput run = script.run(runContext);

        assertThat(run.getExitCode(), is(0));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("ENV=hello")), is(true));
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("INPUT=world")), is(true));
        assertThat(run.getOutputFiles().get("out.txt").toString(), org.hamcrest.Matchers.startsWith("kestra://"));
    }
}