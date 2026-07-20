package io.kestra.plugin.scripts.jvm;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import com.google.common.collect.ImmutableMap;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.AbstractMetricEntry;
import io.kestra.core.models.executions.LogEntry;
import io.kestra.core.queues.DispatchQueueInterface;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;

@KestraTest
abstract public class EvalTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Inject
    private DispatchQueueInterface<LogEntry> workerTaskLogQueue;

    abstract protected Eval task();

    @SuppressWarnings("unchecked")
    @Test
    void run() throws Exception {
        List<LogEntry> logs = new CopyOnWriteArrayList<>();
        workerTaskLogQueue.addListener(logs::add);

        Eval task = this.task();

        RunContext runContext = TestsUtils.mockRunContext(runContextFactory, task, ImmutableMap.of());

        Eval.Output runOutput = task.run(runContext);
        Thread.sleep(500);

        LogEntry firstLog = logs.get(0);
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
