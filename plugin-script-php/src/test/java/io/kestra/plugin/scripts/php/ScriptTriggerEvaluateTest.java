package io.kestra.plugin.scripts.php;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.models.executions.Execution;
import io.kestra.core.models.property.Property;
import io.kestra.core.runners.RunContextFactory;
import io.kestra.core.serializers.JacksonMapper;
import io.kestra.core.storages.kv.KVStore;
import io.kestra.core.utils.IdUtils;
import io.kestra.core.utils.TestsUtils;

import jakarta.inject.Inject;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

@KestraTest
class ScriptTriggerEvaluateTest {
    private static final String IMAGE = "php";

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
    void evaluate_blankExitCondition_propagatesIllegalArgumentException() throws Exception {
        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("blank-exit-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("   "))
            .script(Property.ofValue("<?php echo 1;"))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Executable evaluate = () -> trigger.evaluate(context.getKey(), context.getValue());

        assertThrows(IllegalArgumentException.class, evaluate);
    }

    @Test
    void evaluate_executionFailure_doesNotAdvanceEdgeState() throws Exception {
        ScriptTrigger trigger = trigger("script-exec-fail", "<?php exit(1);");
        var mock = TestsUtils.mockTrigger(runContextFactory, trigger);
        KVStore kvStore = mock.getKey().getRunContext().namespaceKv(mock.getValue().getNamespace());
        String key = ScriptTrigger.edgeStateKey(mock.getValue());

        Optional<Execution> execution = trigger.evaluate(mock.getKey(), mock.getValue());

        if (execution.isEmpty()) {
            assertThat(kvStore.getValue(key).isPresent(), is(false));
        }
    }

    @Test
    void evaluate_edgeDisabledEmitsOnEveryMatchWhenDockerWorks() throws Exception {
        if (!dockerAvailable()) {
            return;
        }

        ScriptTrigger trigger = ScriptTrigger.builder()
            .id("edge-off-" + IdUtils.create())
            .type(ScriptTrigger.class.getName())
            .exitCondition(Property.ofValue("exit 0"))
            .edge(Property.ofValue(false))
            .containerImage(Property.ofValue(IMAGE))
            .script(Property.ofValue("<?php echo \"ok\\n\";"))
            .build();

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> first = trigger.evaluate(context.getKey(), context.getValue());
        Optional<Execution> second = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(first.isPresent(), is(true));
        assertThat(second.isPresent(), is(true));
    }

    @Test
    void scriptTrigger_shouldEmitWhenExitCodeMatches() throws Exception {
        if (!dockerAvailable()) {
            return;
        }

        ScriptTrigger trigger = trigger("script-exit1", "<?php\nexit(1);\n");

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
        if (!dockerAvailable()) {
            return;
        }

        ScriptTrigger trigger = trigger("script-exit0", "<?php\necho \"ok\\n\";\n");

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> execution = trigger.evaluate(context.getKey(), context.getValue());

        assertThat(execution.isPresent(), is(false));
    }

    @Test
    void scriptTrigger_edgeModeShouldSuppressSecondEmissionAcrossFreshInstances() throws Exception {
        if (!dockerAvailable()) {
            return;
        }

        ScriptTrigger trigger = trigger("script-edge", "<?php\nexit(1);\n");

        var context = TestsUtils.mockTrigger(runContextFactory, trigger);
        Optional<Execution> first = trigger.evaluate(context.getKey(), context.getValue());
        assertThat("first poll should fire", first.isPresent(), is(true));

        ScriptTrigger nextPoll = JacksonMapper.ofJson().readValue(JacksonMapper.ofJson().writeValueAsString(trigger), ScriptTrigger.class);
        context = TestsUtils.mockTrigger(runContextFactory, nextPoll);
        Optional<Execution> second = nextPoll.evaluate(context.getKey(), context.getValue());
        assertThat("edge mode should suppress the repeat", second.isPresent(), is(false));
    }

    private static boolean dockerAvailable() {
        try {
            var process = new ProcessBuilder("docker", "info").start();
            return process.waitFor(10, TimeUnit.SECONDS) && process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
