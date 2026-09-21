package io.kestra.plugin.scripts.deno;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.kestra.core.models.property.Property;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit tests for ScriptTrigger's condition-matching logic and task wiring.
 *
 * Calls the real, package-private ScriptTrigger#matchesCondition and ScriptTrigger#buildTask
 * directly (same pattern as plugin-script-ruby's ScriptTriggerConditionTest), so a change to
 * the production code is what these tests actually exercise, not a separately maintained copy
 * of its logic. No scheduler infrastructure, Docker or Deno runtime is required.
 */
class ScriptTriggerTest {

    private final ScriptTrigger trigger = ScriptTrigger.builder().build();

    private ScriptTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars) {
        return new ScriptTrigger.Output(Instant.now(), condition, exitCode, vars);
    }

    @ParameterizedTest
    @CsvSource(
        {
            "exit 0, 0, true",
            "exit 1, 1, true",
            "EXIT 1, 1, true",
            "exit 0, 1, false",
            "exit 1, 127, false",
            "exit 42, 42, true",
        }
    )
    void exitCodeCondition(String condition, int exitCode, boolean expected) {
        assertThat(trigger.matchesCondition(output(condition, exitCode, null)), is(expected));
    }

    @Test
    void exitCondition_nullExitCode_doesNotMatch() {
        assertThat(trigger.matchesCondition(output("exit 1", null, null)), is(false));
    }

    @Test
    void substringMatch_inVars() {
        assertThat(trigger.matchesCondition(output("toto", 0, Map.of("listing", "toto"))), is(true));
    }

    @Test
    void noMatch_whenSubstringAbsentFromVars() {
        assertThat(trigger.matchesCondition(output("toto", 0, Map.of("listing", "something_else"))), is(false));
    }

    @Test
    void regexMatch_inVars() {
        assertThat(trigger.matchesCondition(output("status=\\w+", 0, Map.of("status", "status=ready"))), is(true));
    }

    @Test
    void noMatch_emptyHaystack() {
        assertThat(trigger.matchesCondition(output("something", 0, null)), is(false));
    }

    @Test
    void noMatch_emptyCondition() {
        assertThat(trigger.matchesCondition(output("", 0, Map.of("k", "v"))), is(false));
    }

    @Test
    void nullCondition_doesNotMatch() {
        assertThat(trigger.matchesCondition(output(null, 0, Map.of("k", "v"))), is(false));
    }

    @Test
    void buildTask_defaultPermissionsMatchTheScriptTaskDefault() {
        assertThat(trigger.buildTask().getPermissions(), is(Script.builder().build().getPermissions()));
    }

    @Test
    void buildTask_passesTriggerPermissionsThroughToTheScriptTask() {
        var permissions = Property.ofValue(List.of("--allow-net"));
        var withNet = ScriptTrigger.builder().permissions(permissions).build();

        assertThat(withNet.buildTask().getPermissions(), is(permissions));
    }
}
