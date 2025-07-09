package io.kestra.plugin.scripts.jvm;

import com.google.common.collect.ImmutableMap;
import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;
import reactor.core.publisher.Flux;

import java.net.URI;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@KestraTest
abstract public class EvalTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    @Named(QueueFactoryInterface.WORKERTASKLOG_NAMED)
    private QueueInterface<LogEntry> workerTaskLogQueue;

    abstract protected Eval task();

    @SuppressWarnings("unchecked")
    @Test
    void run() throws Exception {
        Flux<LogEntry> receive = TestsUtils.receive(workerTaskLogQueue);

        Eval task = this.task();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Eval.Output runOutput = task.run(runContext);
        Thread.sleep(500);

        LogEntry firstLog = receive.blockFirst();
        assertThat(firstLog.getLevel(), is(Level.INFO));
        assertThat(firstLog.getMessage(), is("executionId: " + ((Map<String, String>) runContext.getVariables().get("execution")).get("id")));

        AbstractMetricEntry<?> metric = runContext.metrics().get(0);
        assertThat(metric.getValue(), is(666D));
        assertThat(metric.getName(), is("total"));
        assertThat(metric.getTags(), is(ImmutableMap.of("name", "bla")));

        assertThat(((Map<String, String>) runOutput.getOutputs().get("map")).get("test"), is("here"));
        assertThat(((URI) runOutput.getOutputs().get("out")).toString(), startsWith("kestra://"));
    }
}
