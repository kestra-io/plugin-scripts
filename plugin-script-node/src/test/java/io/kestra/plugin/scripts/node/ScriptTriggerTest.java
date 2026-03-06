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
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

@KestraTest
class ScriptTriggerTest {

    @Inject
    protected ApplicationContext applicationContext;

    @Inject
    protected FlowListeners flowListenersService;

    @Inject
    @Named(QueueFactoryInterface.EXECUTION_NAMED)
    protected QueueInterface<Execution> executionQueue;

    @Test
    void scriptTrigger_shouldTriggerOnImplicitFailureExit1() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-trigger")
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .script(Property.ofValue("""
                throw new Error("boom");
                """))
            .build();

        Flow testFlow = Flow.builder()
            .id("script-trigger-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("log")
                .type(Return.class.getName())
                .format(Property.ofValue(
                    "exitCode={{ trigger.exitCode }}, condition={{ trigger.condition }}"
                ))
                .build()))
            .triggers(Collections.singletonList(trigger))
            .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("script-trigger-flow")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        DefaultWorker worker =
            applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        AbstractScheduler scheduler =
            new JdbcScheduler(applicationContext, flowListenersServiceSpy);

        try {
            worker.run();
            scheduler.run();

            Thread.sleep(2000);

            boolean await = queueCount.await(20, TimeUnit.SECONDS);
            assertThat("ScriptTrigger should execute", await, is(true));

            Await.until(
                () -> lastExecution.get() != null,
                Duration.ofMillis(100),
                Duration.ofSeconds(2)
            );

            Execution execution = lastExecution.get();
            assertThat(execution, notNullValue());

            Map<String, Object> triggerVars =
                execution.getTrigger().getVariables();

            assertThat(triggerVars.get("condition"), is("exit 1"));
            assertThat(triggerVars.get("exitCode"), is(1));
            assertThat(triggerVars.get("timestamp"), notNullValue());
        } finally {
            worker.shutdown();
            scheduler.close();
            receive.blockLast();
        }
    }

    @Test
    void scriptTrigger_shouldTriggerOnStdoutMatchViaOutputsConvention() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-stdout-trigger")
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .script(Property.ofValue("""
                console.log('::{"outputs":{"value":"toto"}}::');
                """))
            .build();

        Flow testFlow = Flow.builder()
            .id("script-stdout-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("log")
                .type(Return.class.getName())
                .format(Property.ofValue(
                    "exitCode={{ trigger.exitCode }}, condition={{ trigger.condition }}"
                ))
                .build()))
            .triggers(Collections.singletonList(trigger))
            .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("script-stdout-flow")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        DefaultWorker worker =
            applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        AbstractScheduler scheduler =
            new JdbcScheduler(applicationContext, flowListenersServiceSpy);

        try {
            worker.run();
            scheduler.run();

            Thread.sleep(2000);

            boolean await = queueCount.await(20, TimeUnit.SECONDS);
            assertThat("ScriptTrigger should execute", await, is(true));

            Await.until(
                () -> lastExecution.get() != null,
                Duration.ofMillis(100),
                Duration.ofSeconds(2)
            );

            Execution execution = lastExecution.get();
            assertThat(execution, notNullValue());

            Map<String, Object> triggerVars =
                execution.getTrigger().getVariables();

            assertThat(triggerVars.get("condition"), is("toto"));
            assertThat(triggerVars.get("exitCode"), is(0));
            assertThat(triggerVars.get("timestamp"), notNullValue());
            assertThat(triggerVars.get("vars"), notNullValue());
        } finally {
            worker.shutdown();
            scheduler.close();
            receive.blockLast();
        }
    }
}
