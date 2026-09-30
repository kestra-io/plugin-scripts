package io.kestra.plugin.scripts.jbang;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.conditions.ConditionContext;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class ScriptTriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void matchesConditionChecksExitErrorAndOutput() {
        ScriptTrigger exitTrigger = ScriptTrigger.builder()
            .id("script-trigger")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .script(Property.ofValue("System.exit(1);"))
            .build();

        ScriptTrigger.Output errorOutput = new ScriptTrigger.Output(
            Instant.now(),
            "exit 1",
            1,
            Map.of("error", "Execution failed with exit 1")
        );
        assertThat(exitTrigger.matchesCondition(errorOutput), is(true));

        ScriptTrigger nonMatchingTrigger = ScriptTrigger.builder()
            .id("script-trigger-0")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 0"))
            .script(Property.ofValue("System.exit(1);"))
            .build();
        ScriptTrigger.Output nonMatchingOutput = new ScriptTrigger.Output(
            Instant.now(),
            "exit 0",
            1,
            Map.of("error", "Execution failed with exit 1")
        );
        assertThat(nonMatchingTrigger.matchesCondition(nonMatchingOutput), is(false));

        ScriptTrigger varsTrigger = ScriptTrigger.builder()
            .id("script-trigger-vars")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("READY"))
            .script(Property.ofValue("System.out.println(\"READY\");"))
            .build();
        ScriptTrigger.Output varsOutput = new ScriptTrigger.Output(
            Instant.now(),
            "READY",
            0,
            Map.of("status", "READY")
        );
        assertThat(varsTrigger.matchesCondition(varsOutput), is(true));
    }

    @Test
    void triggerFiresOnImplicitExitCodeCondition() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-trigger-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(true))
            .exitCondition(Property.ofValue("exit 1"))
            .script(Property.ofValue("""
                //usr/bin/env jbang "$0" "$@" ; exit $?
                class TriggerCheck {
                    public static void main(String[] args) {
                        System.exit(1);
                    }
                }
                """))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));
        assertThat(execution.get().getTrigger().getVariables().get("exitCode"), is(1));

        Optional<Execution> secondExecution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(secondExecution.isPresent(), is(false));
    }

    @Test
    void triggerFiresOnRenderedOutputCondition() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-vars-trigger-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(false))
            .exitCondition(Property.ofValue("READY"))
            .script(Property.ofValue("""
                //usr/bin/env jbang "$0" "$@" ; exit $?
                class TriggerVars {
                    public static void main(String[] args) {
                        System.out.println("::{\\"outputs\\":{\\"status\\":\\"READY\\"}}::");
                    }
                }
                """))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));

        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) execution.get().getTrigger().getVariables().get("vars");
        assertThat(vars, is(notNullValue()));
        assertThat(vars.get("status"), is("READY"));
    }

    @Test
    void kotlinScriptRunsWithExtension() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("kotlin-trigger-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(false))
            .extension(Property.ofValue(".kt"))
            .exitCondition(Property.ofValue("exit 42"))
            .script(Property.ofValue("""
                import kotlin.system.exitProcess
                fun main() {
                    exitProcess(42)
                }
                """))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));
        assertThat(execution.get().getTrigger().getVariables().get("exitCode"), is(42));
    }
}