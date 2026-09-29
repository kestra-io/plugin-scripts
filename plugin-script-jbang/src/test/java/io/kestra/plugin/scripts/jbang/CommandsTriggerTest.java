package io.kestra.plugin.scripts.jbang;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
class CommandsTriggerTest {
    @Inject
    private RunContextFactory runContextFactory;

    @Test
    void matchesConditionChecksExitErrorAndOutput() {
        CommandsTrigger exitTrigger = CommandsTrigger.builder()
            .id("commands-trigger")
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .commands(Property.ofValue(List.of("exit 1")))
            .build();

        CommandsTrigger.Output errorOutput = new CommandsTrigger.Output(
            Instant.now(),
            "exit 1",
            1,
            Map.of("error", "Execution failed with exit 1")
        );
        assertThat(exitTrigger.matchesCondition(errorOutput), is(true));

        CommandsTrigger varsTrigger = CommandsTrigger.builder()
            .id("commands-trigger-vars")
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("READY"))
            .commands(Property.ofValue(List.of("jbang --version")))
            .build();
        CommandsTrigger.Output varsOutput = new CommandsTrigger.Output(
            Instant.now(),
            "READY",
            0,
            Map.of("status", "READY")
        );
        assertThat(varsTrigger.matchesCondition(varsOutput), is(true));
    }

    @Test
    void triggerFiresOnExitConditionAndEdgeSuppression() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(true))
            .exitCondition(Property.ofValue("exit 1"))
            .commands(Property.ofValue(List.of("exit 1")))
            .build();

        Map.Entry<ConditionContext, Trigger> context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(execution.isPresent(), is(true));
        assertThat(execution.get().getTrigger().getVariables().get("exitCode"), is(1));

        Optional<Execution> secondExecution = trigger.evaluate(context.getKey(), context.getValue());
        assertThat(secondExecution.isPresent(), is(false));
    }

    @Test
    void triggerFiresOnCommandOutputVars() throws Exception {
        CommandsTrigger trigger = CommandsTrigger.builder()
            .id("commands-vars-trigger-" + IdUtils.create())
            .type(CommandsTrigger.class.getName())
            .interval(Duration.ofSeconds(5))
            .edge(Property.ofValue(false))
            .exitCondition(Property.ofValue("READY"))
            .commands(
                Property.ofValue(
                    List.of(
                        "jbang --version",
                        "echo '::{\"outputs\":{\"status\":\"READY\"}}::'"
                    )
                )
            )
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