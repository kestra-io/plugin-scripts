package io.kestra.plugin.scripts;

import com.google.common.collect.ImmutableMap;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import org.slf4j.event.Level;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@MicronautTest
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
        List<LogEntry> logs = new ArrayList<>();
        workerTaskLogQueue.receive(logs::add);

        Eval task = this.task();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Eval.Output runOutput = task.run(runContext);
        Thread.sleep(100);

        assertThat(logs.get(0).getLevel(), is(Level.INFO));
        assertThat(logs.get(0).getMessage(), is("executionId: " + ((Map<String, String>) runContext.getVariables().get("execution")).get("id")));

        AbstractMetricEntry<?> metric = runContext.metrics().get(0);
        assertThat(metric.getValue(), is(666D));
        assertThat(metric.getName(), is("total"));
        assertThat(metric.getTags(), is(ImmutableMap.of("name", "bla")));

        assertThat(((Map<String, String>) runOutput.getOutputs().get("map")).get("test"), is("here"));
        assertThat(((URI) runOutput.getOutputs().get("out")).toString(), startsWith("kestra://"));
    }
}
