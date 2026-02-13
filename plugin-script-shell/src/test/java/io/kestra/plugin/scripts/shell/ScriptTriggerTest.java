package io.kestra.plugin.scripts.shell;

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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
            .id("script-realtime-trigger")
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ubuntu"))
            .script(Property.ofValue("""
                # This command fails because the file doesn't exist (implicit non-zero exit code).
                cat /path/that/does/not/exist
                """))
            .build();

        Flow testFlow = Flow.builder()
            .id("script-realtime-trigger-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("log-trigger-vars")
                .type(Return.class.getName())
                .format(Property.ofValue("exitCode={{ trigger.exitCode }}, condition={{ trigger.condition }}"))
                .build()))
            .triggers(Collections.singletonList(trigger))
            .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("script-realtime-trigger-flow")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        AbstractScheduler scheduler = new JdbcScheduler(applicationContext, flowListenersServiceSpy);

        try {
            worker.run();
            scheduler.run();

            Thread.sleep(Duration.ofSeconds(2).toMillis());

            boolean await = queueCount.await(20, TimeUnit.SECONDS);
            assertThat("ScriptTrigger should execute", await, is(true));

            try {
                Await.until(
                    () -> lastExecution.get() != null,
                    Duration.ofMillis(100),
                    Duration.ofSeconds(2)
                );
            } catch (TimeoutException e) {
                throw new AssertionError("Execution was not captured within 2 seconds", e);
            }

            Execution execution = lastExecution.get();
            assertThat(execution, notNullValue());

            Map<String, Object> triggerVars = execution.getTrigger().getVariables();
            assertThat("condition should be present", triggerVars.get("condition"), is("exit 1"));
            assertThat("exitCode should be present", triggerVars.get("exitCode"), notNullValue());
            assertThat("exitCode should be 1", triggerVars.get("exitCode"), is(1));
            assertThat("timestamp should be present", triggerVars.get("timestamp"), notNullValue());
        } finally {
            try {
                worker.shutdown();
            } catch (Exception ignored) {
            }
            try {
                scheduler.close();
            } catch (Exception ignored) {
            }
            try {
                receive.blockLast();
            } catch (Exception ignored) {
            }
        }
    }

    @Test
    void scriptTrigger_shouldTriggerOnStdoutMatchViaOutputsConvention() throws Exception {
        FlowListeners flowListenersServiceSpy = spy(this.flowListenersService);

        // We intentionally emit a structured output via the ::{"outputs":...}:: convention
        // so the trigger can match on "toto" without relying on raw stdout capture.
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-stdout-match-trigger")
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(1))
            // "toto" is a non-exit condition => treated as regex/substring against vars/logs (per trigger implementation)
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ubuntu"))
            .script(Property.ofValue("""
                set -e
                echo "toto" > toto.txt
                ls -l
                # Emit the value in outputs so the trigger can evaluate it reliably
                echo '::{"outputs":{"listing":"toto"}}::'
                """))
            .build();

        Flow testFlow = Flow.builder()
            .id("script-stdout-match-flow")
            .namespace("io.kestra.tests")
            .revision(1)
            .tasks(Collections.singletonList(Return.builder()
                .id("log-trigger-vars")
                .type(Return.class.getName())
                .format(Property.ofValue("exitCode={{ trigger.exitCode }}, condition={{ trigger.condition }}"))
                .build()))
            .triggers(Collections.singletonList(trigger))
            .build();

        FlowWithSource flow = FlowWithSource.of(testFlow, null);
        doReturn(List.of(flow)).when(flowListenersServiceSpy).flows();

        CountDownLatch queueCount = new CountDownLatch(1);
        AtomicReference<Execution> lastExecution = new AtomicReference<>();

        Flux<Execution> receive = TestsUtils.receive(executionQueue, execution -> {
            if (execution.getLeft().getFlowId().equals("script-stdout-match-flow")) {
                lastExecution.set(execution.getLeft());
                queueCount.countDown();
            }
        });

        DefaultWorker worker = applicationContext.createBean(DefaultWorker.class, IdUtils.create(), 8, null);
        AbstractScheduler scheduler = new JdbcScheduler(applicationContext, flowListenersServiceSpy);

        try {
            worker.run();
            scheduler.run();

            Thread.sleep(Duration.ofSeconds(2).toMillis());

            boolean await = queueCount.await(20, TimeUnit.SECONDS);
            assertThat("ScriptTrigger should execute", await, is(true));

            try {
                Await.until(
                    () -> lastExecution.get() != null,
                    Duration.ofMillis(100),
                    Duration.ofSeconds(2)
                );
            } catch (TimeoutException e) {
                throw new AssertionError("Execution was not captured within 2 seconds", e);
            }

            Execution execution = lastExecution.get();
            assertThat(execution, notNullValue());

            Map<String, Object> triggerVars = execution.getTrigger().getVariables();
            assertThat("condition should be present", triggerVars.get("condition"), is("toto"));
            assertThat("exitCode should be present", triggerVars.get("exitCode"), notNullValue());
            assertThat("exitCode should be 0", triggerVars.get("exitCode"), is(0));
            assertThat("timestamp should be present", triggerVars.get("timestamp"), notNullValue());

            // If your trigger output exposes "vars", we can assert it as well.
            // Note: this depends on your ScriptTrigger.Output fields being included in Trigger variables.
            Object vars = triggerVars.get("vars");
            assertThat("vars should be present (best effort)", vars, notNullValue());
        } finally {
            try {
                worker.shutdown();
            } catch (Exception ignored) {
            }
            try {
                scheduler.close();
            } catch (Exception ignored) {
            }
            try {
                receive.blockLast();
            } catch (Exception ignored) {
            }
        }
    }
}
