package io.kestra.plugins.scripts.lua;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.lua.Script;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

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

        Script luaScript = Script.builder()
            .id("lua-script-" + UUID.randomUUID())
            .type(Script.class.getName())
            .script(Property.ofValue("print(\"kestra task ran successfully.\")"))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, luaScript, ImmutableMap.of());
        ScriptOutput run = luaScript.run(runContext);

        assertThat(run.getExitCode(), is(0));

        String expectedLog = "kestra task ran successfully.";
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(expectedLog));
        receive.blockLast();
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains(expectedLog)), is(true));
    }
}