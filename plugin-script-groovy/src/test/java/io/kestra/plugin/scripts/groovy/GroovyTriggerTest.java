package io.kestra.plugin.scripts.groovy;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.tasks.RunnableTaskException;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.utils.TestsUtils;
import io.kestra.plugin.scripts.exec.scripts.models.ScriptOutput;

import jakarta.inject.Inject;
import jakarta.validation.Validator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@KestraTest
class GroovyTriggerTest {
    @Inject
    RunContextFactory runContextFactory;

    @Inject
    Validator validator;

    private AbstractGroovyTrigger trigger(boolean commands, String id, String script, String condition, boolean edge) {
        if (commands) {
            return CommandsTrigger.builder()
                .id(id).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .commands(Property.ofValue(List.of("groovy -e '" + script.replace("'", "'\"'\"'") + "'")))
                .exitCondition(Property.ofValue(condition)).edge(Property.ofValue(edge)).build();
        }
        return ScriptTrigger.builder()
            .id(id).type(ScriptTrigger.class.getName())
            .containerImage(Property.ofValue("groovy:jdk21"))
            .script(Property.ofValue(script))
            .exitCondition(Property.ofValue(condition)).edge(Property.ofValue(edge)).build();
    }

    private String id() {
        return "poll-" + UUID.randomUUID();
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void successfulExitCreatesExecutionFromTriggerContext(boolean commands) throws Exception {
        var trigger = trigger(commands, id(), "println 'ok'", "exit 0", true);
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        assertFalse(context.getKey().getRunContext().getVariables().containsKey("task"));
        assertFalse(context.getKey().getRunContext().getVariables().containsKey("execution"));
        var execution = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow();
        var variables = execution.getTrigger().getVariables();
        assertEquals("exit 0", variables.get("condition"));
        assertEquals(0, variables.get("exitCode"));
        assertNotNull(variables.get("timestamp"));
        assertEquals(trigger.getId(), execution.getTrigger().getId());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void failingProcessCanTriggerAndPreservesVars(boolean commands) throws Exception {
        var trigger = trigger(commands, id(), "println '::{\"outputs\":{\"status\":\"ready\",\"count\":3}}::'; System.exit(1)", "exit 1", true);
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var variables = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables();
        assertEquals(1, variables.get("exitCode"));
        assertEquals("exit 1", variables.get("condition"));
        assertEquals("ready", ((Map<?, ?>) variables.get("vars")).get("status"));
        assertEquals(3, ((Number) ((Map<?, ?>) variables.get("vars")).get("count")).intValue());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void exitMismatchDoesNotCreateExecution(boolean commands) throws Exception {
        var trigger = trigger(commands, id(), "System.exit(1)", "exit 0", true);
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void structuredVarsMatchAndNonmatch(boolean commands) throws Exception {
        var trigger = trigger(commands, id(), "println '::{\"outputs\":{\"status\":\"ready\"}}::'", "status=ready", true);
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var variables = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables();
        assertEquals(Map.of("status", "ready"), variables.get("vars"));
        var nonmatch = trigger(commands, id(), "println '::{\"outputs\":{\"status\":\"waiting\"}}::'", "status=ready", true);
        assertTrue(nonmatch.evaluate(context.getKey(), context.getValue().toBuilder().triggerId(nonmatch.getId()).build()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void edgeStateSurvivesNewTriggerInstancesAndResetsOnNonmatch(boolean commands) throws Exception {
        var id = id();
        var first = trigger(commands, id, "println 'ok'", "exit 0", true);
        var context = TestsUtils.mockTrigger(runContextFactory, first);
        assertTrue(first.evaluate(context.getKey(), context.getValue()).isPresent());
        var rehydrated = trigger(commands, id, "println 'ok'", "exit 0", true);
        assertTrue(rehydrated.evaluate(context.getKey(), context.getValue()).isEmpty());
        var nonmatch = trigger(commands, id, "System.exit(1)", "exit 0", true);
        assertTrue(nonmatch.evaluate(context.getKey(), context.getValue()).isEmpty());
        assertTrue(rehydrated.evaluate(context.getKey(), context.getValue()).isPresent());
        assertTrue(rehydrated.evaluate(context.getKey(), context.getValue()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void edgeFalseEmitsOnEachFreshRun(boolean commands) throws Exception {
        var trigger = trigger(commands, id(), "println 'ok'", "exit 0", false);
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isPresent());
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isPresent());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rendersPropertiesFromSchedulerVariables(boolean commands) throws Exception {
        var id = id();
        AbstractGroovyTrigger trigger;
        if (commands) {
            trigger = CommandsTrigger.builder().id(id).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofExpression("{{ 'groovy:jdk21' }}"))
                .commands(Property.ofExpression("{{ [\"groovy -e 'println \\\"\" ~ flow.id ~ \"\\\"'\"] }}"))
                .exitCondition(Property.ofExpression("exit {{ 0 }}"))
                .edge(Property.ofExpression("{{ false }}")).build();
        } else {
            trigger = ScriptTrigger.builder().id(id).type(ScriptTrigger.class.getName())
                .containerImage(Property.ofExpression("{{ 'groovy:jdk21' }}"))
                .script(Property.ofExpression("println '{{ flow.id }}'"))
                .exitCondition(Property.ofExpression("exit {{ 0 }}"))
                .edge(Property.ofExpression("{{ false }}")).build();
        }
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        assertEquals("exit 0", trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables().get("condition"));
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isPresent());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void invalidImageCannotMatchSuccessfulExit(boolean commands) throws Exception {
        var id = id();
        AbstractGroovyTrigger trigger = commands
            ? CommandsTrigger.builder().id(id).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofValue("INVALID IMAGE"))
                .commands(Property.ofValue(List.of("groovy -e 'println 1'"))).exitCondition(Property.ofValue("exit 0")).build()
            : ScriptTrigger.builder().id(id).type(ScriptTrigger.class.getName())
                .containerImage(Property.ofValue("INVALID IMAGE"))
                .script(Property.ofValue("println 1")).exitCondition(Property.ofValue("exit 0")).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void defaultsAndRequiredInputValidation(boolean commands) throws Exception {
        AbstractGroovyTrigger trigger = commands ? CommandsTrigger.builder().build() : ScriptTrigger.builder().build();
        assertEquals(Duration.ofSeconds(60), trigger.getInterval());
        var runContext = runContextFactory.of();
        assertEquals("groovy", runContext.render(trigger.getContainerImage()).as(String.class).orElseThrow());
        assertTrue(runContext.render(trigger.getEdge()).as(Boolean.class).orElseThrow());
        var missingFields = validator.validate(trigger).stream().map(violation -> violation.getPropertyPath().toString()).toList();
        assertTrue(missingFields.contains("exitCondition"));
        assertTrue(missingFields.contains(commands ? "commands" : "script"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void rejectsEmptyAndOverflowingConditionsBeforeRunning(boolean commands) {
        for (var condition : List.of(" ", "exit 999999999999999999999999")) {
            var trigger = trigger(commands, id(), "println 'ok'", condition, true);
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);
            assertThrows(IllegalArgumentException.class, () -> trigger.evaluate(context.getKey(), context.getValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void unknownExitAndSetupExceptionsDoNotMatch(boolean commands) throws Exception {
        var trigger = spy(trigger(commands, id(), "println 'ok'", "exit 0", true));
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        doReturn(null).when(trigger).executeTask(any());
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
        var misleadingOutput = ScriptOutput.builder().exitCode(0).build();
        doThrow(new RunnableTaskException("setup failed", misleadingOutput))
            .when(trigger).executeTask(any());
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
        doThrow(new IllegalStateException("Docker unavailable"))
            .when(trigger).executeTask(any());
        assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
    }

    @Test
    void multipleCommandsPreserveFailureAndEarlierVars() throws Exception {
        var trigger = CommandsTrigger.builder().id(id()).type(CommandsTrigger.class.getName())
            .containerImage(Property.ofValue("groovy:jdk21"))
            .commands(
                Property.ofValue(
                    List.of(
                        "groovy -e 'println \"::{\\\"outputs\\\":{\\\"status\\\":\\\"ready\\\"}}::\"'",
                        "groovy -e 'System.exit(1)'",
                        "groovy -e 'println \"::{\\\"outputs\\\":{\\\"unexpected\\\":true}}::\"'"
                    )
                )
            )
            .exitCondition(Property.ofValue("exit 1")).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var variables = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables();
        assertEquals(1, variables.get("exitCode"));
        assertEquals(Map.of("status", "ready"), variables.get("vars"));
    }
}
