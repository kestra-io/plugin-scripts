package io.kestra.plugin.scripts.python;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
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
    void scriptTrigger_shouldTriggerOnImplicitFailureExit1() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-trigger")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("python:latest"))
            .script(Property.ofValue("import sys; sys.exit(1)"))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(execution.isPresent(), is(true));

        Map<String, Object> triggerVars = execution.get().getTrigger().getVariables();
        assertThat("condition should be present", triggerVars.get("condition"), is("exit 1"));
        assertThat("exitCode should be present", triggerVars.get("exitCode"), notNullValue());
        assertThat("exitCode should be 1", triggerVars.get("exitCode"), is(1));
        assertThat("timestamp should be present", triggerVars.get("timestamp"), notNullValue());
    }

    @Test
    void scriptTrigger_shouldTriggerOnStdoutMatchViaOutputsConvention() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("script-stdout-match-trigger")
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("python:latest"))
            .script(Property.ofValue("print('::{\"outputs\":{\"listing\":\"toto\"}}::')"))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(execution.isPresent(), is(true));

        Map<String, Object> triggerVars = execution.get().getTrigger().getVariables();
        assertThat("condition should be present", triggerVars.get("condition"), is("toto"));
        assertThat("exitCode should be present", triggerVars.get("exitCode"), notNullValue());
        assertThat("exitCode should be 0", triggerVars.get("exitCode"), is(0));
        assertThat("timestamp should be present", triggerVars.get("timestamp"), notNullValue());
        assertThat("vars should be present", triggerVars.get("vars"), notNullValue());
    }
}
