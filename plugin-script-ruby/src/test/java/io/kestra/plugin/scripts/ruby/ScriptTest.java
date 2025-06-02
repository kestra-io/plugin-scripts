package io.kestra.plugin.scripts.ruby;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;
import io.kestra.core.junit.annotations.KestraTest;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

@KestraTest
class ScriptTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> logQueue;

    @Test
    void script() throws Exception {
        List<LogEntry> logs = new ArrayList<>();
        Flux<LogEntry> receive = TestsUtils.receive(logQueue, l -> logs.add(l.getLeft()));

        Script bash;
        bash = Script.builder()
            .id("unit-test")
            .type(Script.class.getName())
            .beforeCommands(Property.ofValue(List.of(
                "gem install date"
            )))
            .script(Property.ofValue("""
                require 'date'
                puts Date.new(2012,12,25).strftime('%F')
                STDERR.puts Date.jd(2451944).strftime('%F')
                """
            ))
            .build();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, bash, ImmutableMap.of());
        ScriptOutput run = bash.run(runContext);

        assertThat(run.getExitCode(), is(0));
        assertThat(run.getStdOutLineCount(), greaterThan(1));
        assertThat(run.getStdErrLineCount(), is(1));

        TestsUtils.awaitLog(logs, log -> log.getMessage() != null && log.getMessage().contains("2001-02-03"));
        receive.blockLast();
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("2012-12-25")).count(), is(1L));
        assertThat(logs.stream().filter(logEntry -> logEntry.getMessage() != null && logEntry.getMessage().contains("2001-02-03")).count(), is(1L));
    }
}
