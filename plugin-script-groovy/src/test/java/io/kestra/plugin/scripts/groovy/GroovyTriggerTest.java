package io.kestra.plugin.scripts.groovy;

import java.io.IOException;
import java.nio.file.Path;
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
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.storages.kv.KVValueAndMetadata;
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

    @ParameterizedTest
    @ValueSource(strings = { "script", "list", "expression" })
    void workingDirRendersInRunnerContext(String input) throws Exception {
        String script = """
            def dir = new File('{{ workingDir }}')
            assert dir.isAbsolute() && dir.isDirectory()
            def probe = new File(dir, 'probe.txt')
            probe.text = 'usable'
            assert probe.text == 'usable'
            println '::{"outputs":{"path":"{{ workingDir }}","flow":"{{ flow.id }}"}}::'
            """;
        AbstractGroovyTrigger trigger;
        if (input.equals("script")) {
            trigger = ScriptTrigger.builder().id(id()).type(ScriptTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .script(Property.ofExpression(script)).exitCondition(Property.ofValue("exit 0")).build();
        } else {
            String command = "groovy -e '" + script.strip().replace("\n", "; ").replace("'", "'\"'\"'") + "'";
            Property<List<String>> commands = input.equals("list")
                ? TestsUtils.propertyFromList(List.of(command))
                : Property.ofExpression(
                    "{{ [" + JacksonMapper.ofJson().writeValueAsString(command)
                        .replace("{{ workingDir }}", "\" ~ workingDir ~ \"")
                        .replace("{{ flow.id }}", "\" ~ flow.id ~ \"") + "] }}"
                );
            // Exercise the same serializer/deserializer used for YAML flow properties.
            commands = JacksonMapper.ofYaml().readValue(
                JacksonMapper.ofYaml().writeValueAsString(commands),
                JacksonMapper.ofYaml().getTypeFactory().constructParametricType(Property.class, List.class)
            );
            trigger = CommandsTrigger.builder().id(id()).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .commands(commands).exitCondition(Property.ofValue("exit 0")).build();
        }
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var runContext = context.getKey().getRunContext();
        for (String absent : List.of("workingDir", "task", "execution")) {
            assertFalse(runContext.getVariables().containsKey(absent), absent);
        }
        var variables = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables();
        var vars = (Map<?, ?>) variables.get("vars");
        String path = (String) vars.get("path");
        assertNotNull(path);
        assertFalse(path.isBlank());
        assertTrue(Path.of(path).isAbsolute());
        assertFalse(path.contains("{{"));
        assertEquals(((Map<?, ?>) runContext.getVariables().get("flow")).get("id"), vars.get("flow"));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void writesOnlyTransitionsEvenWithEdgeDisabled(boolean commands) throws Exception {
        var original = trigger(commands, id(), "unused", "exit 0", true);
        var context = TestsUtils.mockTrigger(runContextFactory, original);
        var runContext = spy(context.getKey().getRunContext());
        KVStore store = spy(runContext.namespaceKv(context.getValue().getNamespace()));
        doReturn(store).when(runContext).namespaceKv(context.getValue().getNamespace());
        var conditionContext = context.getKey().withRunContext(runContext);
        boolean[] matched = { false, true, true, false, false, true, true, true };
        boolean[] edges = { true, true, true, true, true, false, false, true };
        boolean[] emits = { false, true, false, false, false, true, true, false };
        int[] writes = { 0, 1, 1, 2, 2, 3, 3, 3 };
        for (int i = 0; i < matched.length; i++) {
            var fresh = spy(trigger(commands, original.getId(), "unused", "exit 0", edges[i]));
            doReturn(ScriptOutput.builder().exitCode(matched[i] ? 0 : 1).build()).when(fresh).executeTask(any());
            assertEquals(emits[i], fresh.evaluate(conditionContext, context.getValue()).isPresent(), "poll " + i);
            verify(store, times(writes[i])).put(anyString(), any(KVValueAndMetadata.class));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void missingAndInvalidInputsPreserveMatchingEdge(boolean commands) throws Exception {
        var original = trigger(commands, id(), "println 'ok'", "exit 0", true);
        var context = TestsUtils.mockTrigger(runContextFactory, original);
        var store = context.getKey().getRunContext().namespaceKv(context.getValue().getNamespace());
        var key = AbstractGroovyTrigger.edgeStateKey(context.getValue().getFlowId(), context.getValue().getTriggerId());
        store.put(key, new KVValueAndMetadata(null, true));
        AbstractGroovyTrigger missing = commands
            ? CommandsTrigger.builder().id(original.getId()).type(CommandsTrigger.class.getName())
                .exitCondition(Property.ofValue("exit 0")).build()
            : ScriptTrigger.builder().id(original.getId()).type(ScriptTrigger.class.getName())
                .exitCondition(Property.ofValue("exit 0")).build();
        assertTrue(missing.evaluate(context.getKey(), context.getValue()).isEmpty());
        assertEquals(true, store.getValue(key).orElseThrow().value());
        var invalid = commands
            ? CommandsTrigger.builder().id(original.getId()).type(CommandsTrigger.class.getName())
                .commands(TestsUtils.propertyFromList(List.of("echo {{ absent }}")))
                .exitCondition(Property.ofValue("exit 0")).build()
            : ScriptTrigger.builder().id(original.getId()).type(ScriptTrigger.class.getName())
                .script(Property.ofExpression("println '{{ absent }}'"))
                .exitCondition(Property.ofValue("exit 0")).build();
        assertTrue(invalid.evaluate(context.getKey(), context.getValue()).isEmpty());
        assertEquals(true, store.getValue(key).orElseThrow().value());
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void missingAndInvalidInputsCannotEmitWithoutEdgeSuppression(boolean commands) throws Exception {
        var original = trigger(commands, id(), "unused", "exit 0", false);
        var context = TestsUtils.mockTrigger(runContextFactory, original);
        var store = context.getKey().getRunContext().namespaceKv(context.getValue().getNamespace());
        var key = AbstractGroovyTrigger.edgeStateKey(context.getValue().getFlowId(), context.getValue().getTriggerId());
        assertTrue(store.getValue(key).isEmpty());
        AbstractGroovyTrigger missing = commands
            ? CommandsTrigger.builder().id(original.getId()).type(CommandsTrigger.class.getName())
                .exitCondition(Property.ofValue("exit 0")).edge(Property.ofValue(false)).build()
            : ScriptTrigger.builder().id(original.getId()).type(ScriptTrigger.class.getName())
                .exitCondition(Property.ofValue("exit 0")).edge(Property.ofValue(false)).build();
        assertTrue(missing.evaluate(context.getKey(), context.getValue()).isEmpty());
        assertTrue(store.getValue(key).isEmpty());
        AbstractGroovyTrigger invalid = commands
            ? CommandsTrigger.builder().id(original.getId()).type(CommandsTrigger.class.getName())
                .commands(TestsUtils.propertyFromList(List.of("echo {{ absent }}")))
                .exitCondition(Property.ofValue("exit 0")).edge(Property.ofValue(false)).build()
            : ScriptTrigger.builder().id(original.getId()).type(ScriptTrigger.class.getName())
                .script(Property.ofExpression("println '{{ absent }}'"))
                .exitCondition(Property.ofValue("exit 0")).edge(Property.ofValue(false)).build();
        assertTrue(invalid.evaluate(context.getKey(), context.getValue()).isEmpty());
        assertTrue(store.getValue(key).isEmpty());
    }

    @Test
    void persistedStateIsIsolatedByFlowTriggerAndNamespaceAndSurvivesInfrastructureFailure() throws Exception {
        var trigger = spy(trigger(false, id(), "unused", "exit 0", true));
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var base = context.getValue();
        var scopes = List.of(
            base,
            io.kestra.core.models.triggers.Trigger.builder().tenantId(base.getTenantId())
                .namespace(base.getNamespace()).flowId(base.getFlowId() + "-other").triggerId(base.getTriggerId())
                .date(java.time.ZonedDateTime.now()).build(),
            io.kestra.core.models.triggers.Trigger.builder().tenantId(base.getTenantId())
                .namespace(base.getNamespace()).flowId(base.getFlowId()).triggerId(base.getTriggerId() + "-other")
                .date(java.time.ZonedDateTime.now()).build(),
            io.kestra.core.models.triggers.Trigger.builder().tenantId(base.getTenantId())
                .namespace(base.getNamespace() + ".other").flowId(base.getFlowId()).triggerId(base.getTriggerId())
                .date(java.time.ZonedDateTime.now()).build()
        );
        var runContext = spy(context.getKey().getRunContext());
        var otherNamespaceStore = mock(KVStore.class);
        when(otherNamespaceStore.getValue(anyString())).thenReturn(java.util.Optional.empty())
            .thenReturn(java.util.Optional.of(new io.kestra.core.storages.kv.KVValue(true)));
        doReturn(otherNamespaceStore).when(runContext).namespaceKv(base.getNamespace() + ".other");
        var conditionContext = context.getKey().withRunContext(runContext);
        for (var scope : scopes) {
            doReturn(ScriptOutput.builder().exitCode(0).build()).when(trigger).executeTask(any());
            assertTrue(trigger.evaluate(conditionContext, scope).isPresent());
            assertTrue(trigger.evaluate(conditionContext, scope).isEmpty());
            doThrow(new IOException("Docker unavailable")).when(trigger).executeTask(any());
            assertTrue(trigger.evaluate(conditionContext, scope).isEmpty());
            doReturn(ScriptOutput.builder().exitCode(0).build()).when(trigger).executeTask(any());
            assertTrue(trigger.evaluate(conditionContext, scope).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void kvFailuresPropagate(boolean writeFailure) throws Exception {
        var trigger = spy(trigger(false, id(), "unused", "exit 0", true));
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        var runContext = spy(context.getKey().getRunContext());
        var store = mock(KVStore.class);
        doReturn(store).when(runContext).namespaceKv(anyString());
        doReturn(ScriptOutput.builder().exitCode(0).build()).when(trigger).executeTask(any());
        if (writeFailure) {
            doThrow(new IOException("KV write unavailable")).when(store).put(anyString(), any(KVValueAndMetadata.class));
        } else {
            doThrow(new IOException("KV read unavailable")).when(store).getValue(anyString());
        }
        assertThrows(IOException.class, () -> trigger.evaluate(context.getKey().withRunContext(runContext), context.getValue()));
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void templateDelimitersKeepUnderlyingTaskSemantics(boolean commands) throws Exception {
        String script = "println '::{\"outputs\":{\"literal\":\"{{ '{{ absent }}' }}\"}}::'";
        var trigger = commands
            ? CommandsTrigger.builder().id(id()).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .commands(TestsUtils.propertyFromList(List.of("groovy -e \"" + script.replace("\"", "\\\"") + "\"")))
                .exitCondition(Property.ofValue("exit 0")).build()
            : ScriptTrigger.builder().id(id()).type(ScriptTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .script(Property.ofExpression(script)).exitCondition(Property.ofValue("exit 0")).build();
        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        if (commands) {
            var embedded = (CommandsTrigger) trigger;
            var task = Commands.builder().id(id()).type(Commands.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21")).commands(embedded.getCommands()).build();
            var output = task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()));
            assertEquals(Map.of("literal", "{{ absent }}"), output.getVars());
            var vars = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables().get("vars");
            assertEquals(output.getVars(), vars);
        } else {
            // Script's input-file handling renders the script again in Kestra 1.3.39.
            // Preserve the task's existing behavior rather than adding a trigger-specific escape.
            var task = Script.builder().id(id()).type(Script.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21")).script(Property.ofExpression(script)).build();
            assertThrows(
                io.kestra.core.exceptions.IllegalVariableEvaluationException.class,
                () -> task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()))
            );
            assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void literalValuePropertiesKeepUnderlyingTaskSemantics(boolean commands) throws Exception {
        String script = "println '::{\"outputs\":{\"literal\":\"{{ absent }}\"}}::'";
        String command = "groovy -e \"" + script.replace("\"", "\\\"") + "\"";
        if (commands) {
            var task = Commands.builder().id(id()).type(Commands.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .commands(Property.ofValue(List.of(command))).build();
            var output = task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()));
            assertEquals(Map.of("literal", "{{ absent }}"), output.getVars());
            var trigger = CommandsTrigger.builder().id(id()).type(CommandsTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .commands(Property.ofValue(List.of(command))).exitCondition(Property.ofValue("exit 0")).build();
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);
            var vars = trigger.evaluate(context.getKey(), context.getValue()).orElseThrow().getTrigger().getVariables().get("vars");
            assertEquals(output.getVars(), vars);
        } else {
            // Even literal Script values pass through the task's input-file rendering.
            var task = Script.builder().id(id()).type(Script.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21")).script(Property.ofValue(script)).build();
            assertThrows(
                io.kestra.core.exceptions.IllegalVariableEvaluationException.class,
                () -> task.run(TestsUtils.mockRunContext(runContextFactory, task, Map.of()))
            );
            var trigger = ScriptTrigger.builder().id(id()).type(ScriptTrigger.class.getName())
                .containerImage(Property.ofValue("groovy:jdk21"))
                .script(Property.ofValue(script)).exitCondition(Property.ofValue("exit 0")).build();
            var context = TestsUtils.mockTrigger(runContextFactory, trigger);
            assertTrue(trigger.evaluate(context.getKey(), context.getValue()).isEmpty());
        }
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
