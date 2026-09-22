package io.kestra.plugin.scripts.bun;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Runs the real ScriptTrigger#evaluate end to end, which needs Docker like the other script tests.
 */
@KestraTest
class ScriptTriggerEvaluateTest {
    private static final String IMAGE = "oven/bun";

    @Inject
    private RunContextFactory runContextFactory;

    private static ScriptTrigger trigger(String prefix, String script) {
        return ScriptTrigger.builder()
            .id(prefix + "-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue(IMAGE))
            .script(Property.ofValue(script))
            .build();
    }

    @Test
    void scriptTrigger_shouldEmitWhenExitCodeMatches() throws Exception {
        ScriptTrigger trigger = trigger("script-exit1", "process.exit(1);");

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(execution.isPresent(), is(true));

        Map<String, Object> triggerVars = execution.get().getTrigger().getVariables();
        assertThat(triggerVars.get("condition"), is("exit 1"));
        assertThat(triggerVars.get("exitCode"), is(1));
        assertThat(triggerVars.get("timestamp"), notNullValue());
    }

    @Test
    void scriptTrigger_shouldStayQuietWhenConditionDoesNotMatch() throws Exception {
        ScriptTrigger trigger = trigger("script-exit0", "console.log(\"ok\");");

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(execution.isPresent(), is(false));
    }

    @Test
    void scriptTrigger_edgeModeShouldSuppressSecondEmissionAcrossFreshInstances() throws Exception {
        ScriptTrigger trigger = trigger("script-edge", "process.exit(1);");

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> first = trigger.evaluate(context.getKey(), context.getValue());
        assertThat("first poll should fire", first.isPresent(), is(true));

        // The next poll runs on a copy that went through the worker's serialize/deserialize round trip.
        ScriptTrigger nextPoll = JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(trigger), ScriptTrigger.class);
        context = TestsUtils.mockTrigger(runContextFactory, nextPoll);
        Optional<Execution> second = nextPoll.evaluate(context.getKey(), context.getValue());
        assertThat("edge mode should suppress the repeat", second.isPresent(), is(false));
    }
}
