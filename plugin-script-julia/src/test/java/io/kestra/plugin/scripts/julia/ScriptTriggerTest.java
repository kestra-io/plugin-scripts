package io.kestra.plugin.scripts.julia;

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
    void matchesConditionChecksExitAndVars() {
        ScriptTrigger exitTrigger = ScriptTrigger.builder()
            .id("julia-script-trigger")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 42"))
            .script(Property.ofValue("exit(42)"))
            .build();

        assertThat(exitTrigger.matchesCondition(new ScriptTrigger.Output(Instant.now(), "exit 42", 42, null)), is(true));
        assertThat(exitTrigger.matchesCondition(new ScriptTrigger.Output(Instant.now(), "exit 42", 0, null)), is(false));

        ScriptTrigger varsTrigger = ScriptTrigger.builder()
            .id("julia-script-trigger-vars")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("READY"))
            .script(Property.ofValue("println(\"::{\\\"outputs\\\":{\\\"status\\\":\\\"READY\\\"}}::\")"))
            .build();

        assertThat(varsTrigger.matchesCondition(new ScriptTrigger.Output(Instant.now(), "READY", 0, Map.of("status", "READY"))), is(true));
    }

    @Test
    void triggerFiresOnExitConditionAndEdgeSuppression() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("julia-script-trigger-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(true))
            .exitCondition(Property.ofValue("exit 42"))
            .script(Property.ofValue("exit(42)"))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));
        assertThat(execution.get().getTrigger().getVariables().get("exitCode"), is(42));

        Optional<Execution> secondExecution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(secondExecution.isPresent(), is(false));
    }

    @Test
    void triggerFiresOnVarsCondition() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("julia-script-vars-trigger-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(false))
            .exitCondition(Property.ofValue("READY"))
            .script(Property.ofValue("println(\"::{\\\"outputs\\\":{\\\"status\\\":\\\"READY\\\"}}::\")"))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));

        @SuppressWarnings("unchecked")
        Map<String, Object> vars = (Map<String, Object>) execution.get().getTrigger().getVariables().get("vars");
        assertThat(vars, is(notNullValue()));
        assertThat(vars.get("status"), is("READY"));
    }
}
