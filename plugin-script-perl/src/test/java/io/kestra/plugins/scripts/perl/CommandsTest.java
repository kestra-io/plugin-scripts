package io.kestra.plugins.scripts.perl;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.plugin.scripts.perl.Commands;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

@KestraTest
public class CommandsTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void task() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Commands commands = Commands.builder()
            .id(IdUtils.create())
            .type(Commands.class.getName())
            .commands(Property.ofValue(List.of("perl -e 'print \"Test OK\";'")))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, commands, ImmutableMap.of());
        ScriptOutput run = commands.run(runContext);
        assertThat(run.getExitCode(), is(0));

        String expectedLog = "Test OK";
        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains(expectedLog));
        receive.blockLast();
        assertThat(logs.stream().anyMatch(log -> log.getMessage() != null && log.getMessage().contains(expectedLog)), is(true));
    }
}