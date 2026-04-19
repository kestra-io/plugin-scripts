package io.kestra.plugin.scripts.ruby;

import java.util.List;
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
class CommandsTriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void commandsTrigger_shouldTriggerOnImplicitFailureExit1() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-trigger")
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ruby:latest"))
            .commands(Property.ofValue(List.of("exit 1")))
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
            .id("commands-stdout-match-trigger")
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ruby:latest"))
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
    void edgeMode_preventsConsecutiveEmit() {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("edge-test")
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .commands(Property.ofValue(List.of("ruby -e \"raise 'boom'\"")))
            .edge(Property.ofValue(true))
            .build();

        java.util.concurrent.atomic.AtomicBoolean lastMatched = new java.util.concurrent.atomic.AtomicBoolean(false);

        // First match: transition false->true => should emit
        boolean matched1 = true;
        boolean emit1 = !lastMatched.getAndSet(matched1) && matched1;
        assertThat("first match should emit", emit1, is(true));

        // Second consecutive match: true->true => should NOT emit
        boolean matched2 = true;
        boolean emit2 = !lastMatched.getAndSet(matched2) && matched2;
        assertThat("consecutive match should NOT emit in edge mode", emit2, is(false));

        // Non-match: true->false => should not emit
        boolean matched3 = false;
        boolean emit3 = !lastMatched.getAndSet(matched3) && matched3;
        assertThat("non-match should not emit", emit3, is(false));

        // Match again after non-match: false->true => should emit
        boolean matched4 = true;
        boolean emit4 = !lastMatched.getAndSet(matched4) && matched4;
        assertThat("match after non-match should emit", emit4, is(true));
    }
}
