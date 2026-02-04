package io.kestra.plugin.scripts.node;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.flows.Flow;
import io.kestra.core.models.flows.FlowWithSource;
import io.kestra.core.models.property.Property;
import io.kestra.core.queues.QueueFactoryInterface;
import io.kestra.core.queues.QueueInterface;
import io.kestra.core.runners.FlowListeners;
import io.kestra.core.utils.Await;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;
import io.kestra.jdbc.runner.JdbcScheduler;
import io.kestra.plugin.core.debug.Return;
import io.kestra.scheduler.AbstractScheduler;
import io.kestra.worker.DefaultWorker;
import io.micronaut.context.ApplicationContext;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

@KestraTest
class CommandsTriggerTest {
    @Inject
    ApplicationContext applicationContext;

    @Inject
    FlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    QueueInterface<Execution> executionQueue;

    @Test
    void commandsTrigger_shouldTriggerOnImplicitFailureExit1() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-trigger")
            .type(CommandsTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .commands(Property.ofValue(List.of(
                "node -e \"throw new Error('boom')\""
            )))
            .build();

        Flow flow = Flow.builder()
            .id("commands-trigger-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .triggers(List.of(trigger))
            .tasks(List.of(Return.builder()
                .id("log")
                .type(Return.class.getName())
                .format(Property.ofValue("exit={{ trigger.exitCode }}"))
                .build()))
            .build();

        Execution execution = run(flow);
        Map<String, Object> vars = execution.getTrigger().getVariables();

        assertThat(vars.get("condition"), is("exit 1"));
        assertThat(vars.get("exitCode"), is(1));
        assertThat(vars.get("timestamp"), notNullValue());
    }

    @Test
    void commandsTrigger_shouldTriggerOnStdoutMatchUsingStructuredOutputs() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-stdout-trigger")
            .type(CommandsTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .commands(Property.ofValue(List.of(
                "node -e \"console.log('::{\\\"outputs\\\":{\\\"key\\\":\\\"toto\\\"}}::')\""
            )))
            .build();

        Flow flow = Flow.builder()
            .id("commands-stdout-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .triggers(List.of(trigger))
            .tasks(List.of(Return.builder()
                .id("log")
                .type(Return.class.getName())
                .format(Property.ofValue("ok"))
                .build()))
            .build();

        Execution execution = run(flow);
        Map<String, Object> vars = execution.getTrigger().getVariables();

        assertThat(vars.get("condition"), is("toto"));
        assertThat(vars.get("exitCode"), is(0));
        assertThat(vars.get("vars"), notNullValue());
    }

    private Execution run(Flow flow) throws Exception {
        FlowListeners spy = org.mockito.Mockito.spy(flowListenersService);
        org.mockito.Mockito.doReturn(List.of(FlowWithSource.of(flow, null))).when(spy).flows();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Execution> result = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, e -> {
            if (e.getLeft().getFlowId().equals(flow.getId())) {
                result.set(e.getLeft());
                latch.countDown();
            }
        });

        DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        AbstractScheduler scheduler = new JdbcScheduler(applicationContext, spy);

        worker.run();
        scheduler.run();

        latch.await(10, TimeUnit.SECONDS);
        Await.until(() -> result.get() != null, Duration.ofMillis(100), Duration.ofSeconds(2));

        worker.shutdown();
        scheduler.close();
        receive.blockLast();

        return result.get();
    }
}
