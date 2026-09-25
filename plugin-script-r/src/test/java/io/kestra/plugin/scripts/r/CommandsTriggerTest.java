package io.kestra.plugin.scripts.r;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@KestraTest
class CommandsTriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void commandsTrigger_shouldTriggerOnImplicitFailureExit1() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("r-base"))
            .commands(Property.ofValue(List.of("Rscript -e 'quit(status = 1)'")))
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
    void commandsTrigger_shouldTriggerOnStdoutMatchUsingStructuredOutputs() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-stdout-match-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("r-base"))
            .commands(Property.ofValue(List.of("echo '::{\"outputs\":{\"listing\":\"toto\"}}::'")))
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

    @Test
    void commandsTrigger_shouldNotEmitWhenConditionDoesNotMatch() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-no-match-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("r-base"))
            .commands(Property.ofValue(List.of("Rscript -e 'quit(status = 0)'")))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat("successful run should not match 'exit 1'", execution.isPresent(), is(false));
    }

    // Drives the real evaluate() wiring rather than the edge arithmetic on its own, so a
    // regression in how evaluate() consults the stored state is caught here.
    @Test
    void edgeMode_preventsConsecutiveEmit() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-edge-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("r-base"))
            .commands(Property.ofValue(List.of("Rscript -e 'quit(status = 1)'")))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        assertThat(
            "first matching poll should emit",
            trigger.evaluate(context.getKey(), context.getValue()).isPresent(),
            is(true)
        );
        assertThat(
            "a condition that stays matched should not emit again in edge mode",
            trigger.evaluate(context.getKey(), context.getValue()).isPresent(),
            is(false)
        );
    }

    @Test
    void edgeDisabled_emitsOnEveryMatchingPoll() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-noedge-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(false))
            .containerImage(Property.ofValue("r-base"))
            .commands(Property.ofValue(List.of("Rscript -e 'quit(status = 1)'")))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);

        assertThat(trigger.evaluate(context.getKey(), context.getValue()).isPresent(), is(true));
        assertThat(
            "edge=false should emit on every matching poll",
            trigger.evaluate(context.getKey(), context.getValue()).isPresent(),
            is(true)
        );
    }
}
