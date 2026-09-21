package io.kestra.plugin.scripts.powershell;

import java.util.List;
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
            .containerImage(Property.ofValue("ghcr.io/kestra-io/powershell:latest"))
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
            .id("commands-stdout-match-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("toto"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ghcr.io/kestra-io/powershell:latest"))
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
    void commandsTrigger_edgeModeShouldSuppressSecondEmission() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-edge-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ghcr.io/kestra-io/powershell:latest"))
            .commands(Property.ofValue(List.of("exit 1")))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> first = trigger.evaluate(context.getKey(), context.getValue());
        assertThat("First evaluation should fire", first.isPresent(), is(true));

        // The second poll runs on a copy that went through the worker's serialize/deserialize round trip.
        CommandsTrigger nextPoll = JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(trigger), CommandsTrigger.class);
        context = TestsUtils.mockTrigger(runContextFactory, nextPoll);
        Optional<Execution> second = nextPoll.evaluate(context.getKey(), context.getValue());
        assertThat("Edge mode should suppress repeated emission", second.isPresent(), is(false));
    }

    @Test
    void commandsTrigger_shouldMatchRegexAgainstStructuredOutputs() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-regex-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("status=\\w+"))
            .edge(Property.ofValue(true))
            .containerImage(Property.ofValue("ghcr.io/kestra-io/powershell:latest"))
            .commands(Property.ofValue(List.of("echo '::{\"outputs\":{\"status\":\"status=ready\"}}::'")))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat("Regex condition should match", execution.isPresent(), is(true));

        Map<String, Object> triggerVars = execution.get().getTrigger().getVariables();
        assertThat("exitCode should be 0", triggerVars.get("exitCode"), is(0));
        assertThat("vars should be present", triggerVars.get("vars"), notNullValue());
    }
}
