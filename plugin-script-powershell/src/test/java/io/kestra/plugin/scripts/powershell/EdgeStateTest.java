package io.kestra.plugin.scripts.powershell;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.property.Property;
import io.kestra.core.models.triggers.Trigger;
import io.kestra.core.models.triggers.TriggerContext;
import io.kestra.core.runners.RunContext;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static io.kestra.core.tenant.TenantService.MAIN_TENANT;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * Edge mode keeps its previous result in the namespace KV store. None of these tests need Docker:
 * they drive shouldEmit directly, so only the state handling is under test.
 *
 * The KV store used by the test config is a local folder that survives between runs, so every
 * test uses a unique trigger id.
 */
@KestraTest
class EdgeStateTest {
    @Inject
    private RunContextFactory runContextFactory;

    private static ScriptTrigger scriptTrigger(String id) {
        return ScriptTrigger.builder()
            .id(id)
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .script(Property.ofValue("unused"))
            .build();
    }

    private static CommandsTrigger commandsTrigger(String id) {
        return CommandsTrigger.builder()
            .id(id)
            .type(CommandsTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 1"))
            .commands(Property.ofValue(List.of("unused")))
            .build();
    }

    // The scheduler hands every poll a freshly deserialized trigger, so each poll here does the same.
    private static <T> T freshCopy(T trigger, Class<T> type) throws Exception {
        return JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(trigger), type);
    }

    private static TriggerContext contextFor(String flowId, String triggerId, String namespace) {
        return Trigger.builder()
            .triggerId(triggerId)
            .flowId(flowId)
            .tenantId(MAIN_TENANT)
            .namespace(namespace)
            .date(ZonedDateTime.now())
            .build();
    }

    @Test
    void scriptTrigger_edgeEmitsOnlyOnTransitionAcrossFreshInstances() throws Exception {
        ScriptTrigger original = scriptTrigger("script-edge-" + IdUtils.create());
        var mock = TestsUtils.mockTrigger(runContextFactory, original);
        RunContext runContext = mock.getKey().getRunContext();
        TriggerContext context = mock.getValue();

        boolean[] polls = { false, true, true, true, false, false, true };
        boolean[] expected = { false, true, false, false, false, false, true };

        for (int i = 0; i < polls.length; i++) {
            boolean emit = freshCopy(original, ScriptTrigger.class).shouldEmit(runContext, context, true, polls[i]);
            assertThat("poll " + i + " (matched=" + polls[i] + ")", emit, is(expected[i]));
        }
    }

    @Test
    void commandsTrigger_edgeEmitsOnlyOnTransitionAcrossFreshInstances() throws Exception {
        CommandsTrigger original = commandsTrigger("commands-edge-" + IdUtils.create());
        var mock = TestsUtils.mockTrigger(runContextFactory, original);
        RunContext runContext = mock.getKey().getRunContext();
        TriggerContext context = mock.getValue();

        boolean[] polls = { false, true, true, true, false, false, true };
        boolean[] expected = { false, true, false, false, false, false, true };

        for (int i = 0; i < polls.length; i++) {
            boolean emit = freshCopy(original, CommandsTrigger.class).shouldEmit(runContext, context, true, polls[i]);
            assertThat("poll " + i + " (matched=" + polls[i] + ")", emit, is(expected[i]));
        }
    }

    @Test
    void scriptTrigger_edgeDisabledEmitsOnEveryMatchAndKeepsNoState() throws Exception {
        ScriptTrigger original = scriptTrigger("script-noedge-" + IdUtils.create());
        var mock = TestsUtils.mockTrigger(runContextFactory, original);
        RunContext runContext = mock.getKey().getRunContext();
        TriggerContext context = mock.getValue();

        assertThat(original.shouldEmit(runContext, context, false, true), is(true));
        assertThat(original.shouldEmit(runContext, context, false, true), is(true));
        assertThat(original.shouldEmit(runContext, context, false, false), is(false));

        assertThat(
            "edge=false must not write to the KV store",
            runContext.namespaceKv(context.getNamespace()).getValue(ScriptTrigger.edgeStateKey(context)).isPresent(),
            is(false)
        );
    }

    @Test
    void commandsTrigger_edgeDisabledEmitsOnEveryMatchAndKeepsNoState() throws Exception {
        CommandsTrigger original = commandsTrigger("commands-noedge-" + IdUtils.create());
        var mock = TestsUtils.mockTrigger(runContextFactory, original);
        RunContext runContext = mock.getKey().getRunContext();
        TriggerContext context = mock.getValue();

        assertThat(original.shouldEmit(runContext, context, false, true), is(true));
        assertThat(original.shouldEmit(runContext, context, false, true), is(true));
        assertThat(original.shouldEmit(runContext, context, false, false), is(false));

        assertThat(
            "edge=false must not write to the KV store",
            runContext.namespaceKv(context.getNamespace()).getValue(CommandsTrigger.edgeStateKey(context)).isPresent(),
            is(false)
        );
    }

    @Test
    void scriptTrigger_stateIsScopedToFlowAndTrigger() throws Exception {
        String triggerId = "script-scope-" + IdUtils.create();
        ScriptTrigger trigger = scriptTrigger(triggerId);
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);
        RunContext runContext = mock.getKey().getRunContext();
        String namespace = mock.getValue().getNamespace();

        TriggerContext flowA = contextFor("flow-a", triggerId, namespace);
        TriggerContext flowB = contextFor("flow-b", triggerId, namespace);
        TriggerContext otherTriggerInFlowA = contextFor("flow-a", triggerId + "-other", namespace);

        assertThat(trigger.shouldEmit(runContext, flowA, true, true), is(true));
        assertThat("same trigger id in another flow has its own state", trigger.shouldEmit(runContext, flowB, true, true), is(true));
        assertThat("another trigger in the same flow has its own state", trigger.shouldEmit(runContext, otherTriggerInFlowA, true, true), is(true));

        assertThat(trigger.shouldEmit(runContext, flowA, true, true), is(false));
        assertThat(trigger.shouldEmit(runContext, flowB, true, true), is(false));
    }

    @Test
    void commandsTrigger_stateIsScopedToFlowAndTrigger() throws Exception {
        String triggerId = "commands-scope-" + IdUtils.create();
        CommandsTrigger trigger = commandsTrigger(triggerId);
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);
        RunContext runContext = mock.getKey().getRunContext();
        String namespace = mock.getValue().getNamespace();

        TriggerContext flowA = contextFor("flow-a", triggerId, namespace);
        TriggerContext flowB = contextFor("flow-b", triggerId, namespace);
        TriggerContext otherTriggerInFlowA = contextFor("flow-a", triggerId + "-other", namespace);

        assertThat(trigger.shouldEmit(runContext, flowA, true, true), is(true));
        assertThat("same trigger id in another flow has its own state", trigger.shouldEmit(runContext, flowB, true, true), is(true));
        assertThat("another trigger in the same flow has its own state", trigger.shouldEmit(runContext, otherTriggerInFlowA, true, true), is(true));

        assertThat(trigger.shouldEmit(runContext, flowA, true, true), is(false));
        assertThat(trigger.shouldEmit(runContext, flowB, true, true), is(false));
    }

    @Test
    void edgeStateKey_doesNotCollideWhenIdsShareHyphens() {
        // "a-b" + "c" and "a" + "b-c" would both read "a-b-c" without the length prefix.
        TriggerContext first = contextFor("a-b", "c", "company.team");
        TriggerContext second = contextFor("a", "b-c", "company.team");

        assertThat(ScriptTrigger.edgeStateKey(first), is(not(ScriptTrigger.edgeStateKey(second))));
        assertThat(CommandsTrigger.edgeStateKey(first), is(not(CommandsTrigger.edgeStateKey(second))));
    }

    @Test
    void edgeStateKey_isAValidKvKey() {
        String key = ScriptTrigger.edgeStateKey(contextFor("my_flow-1", "my_trigger-2", "company.team"));

        assertThat(key.matches("[a-zA-Z0-9][a-zA-Z0-9._-]*"), is(true));
    }
}
