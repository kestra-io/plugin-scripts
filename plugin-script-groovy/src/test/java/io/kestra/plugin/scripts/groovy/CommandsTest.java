package io.kestra.plugin.scripts.groovy;

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

        var groovyCommands = Commands.builder()
            .id("groovy-commands-" + UUID.randomUUID())
            .type(Commands.class.getName())
            .allowWarning(true)
            .commands(Property.ofValue(List.of("groovy -e \"println 'I love Kestra!'\"")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, groovyCommands, ImmutableMap.of());
        var run = groovyCommands.run(runContext);

        assertThat(run.getExitCode(), is(0));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("I love Kestra!"));
        assertThat(List.copyOf(logs).stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains("I love Kestra!")), is(true));
    }
}