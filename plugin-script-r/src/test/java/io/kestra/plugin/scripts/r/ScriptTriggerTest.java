package io.kestra.plugin.scripts.r;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit tests for ScriptTrigger's condition-matching logic.
 *
 * These tests exercise matchesCondition via the Output model without requiring an R
 * runtime, which may not be available on all CI machines. Edge mode is covered in
 * EdgeStateTest, and integration coverage against an actual R runtime lives in
 * CommandsTriggerTest.
 */
class ScriptTriggerTest {

    private final ScriptTrigger trigger = ScriptTrigger.builder().build();

    private ScriptTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars) {
        return new ScriptTrigger.Output(Instant.now(), condition, exitCode, vars);
    }

    @Test
    void exitCodeCondition_shouldMatchWhenExitCodeEquals() {
        assertThat(trigger.matchesCondition(output("exit 1", 1, null)), is(true));
    }

    @Test
    void exitCodeCondition_shouldNotMatchWhenExitCodeDiffers() {
        assertThat(trigger.matchesCondition(output("exit 1", 127, null)), is(false));
    }

    @Test
    void exitCodeCondition_shouldNotMatchWhenExitCodeIsNull() {
        assertThat(trigger.matchesCondition(output("exit 1", null, null)), is(false));
    }

    @Test
    void substringCondition_shouldMatchAgainstVars() {
        assertThat(trigger.matchesCondition(output("toto", 0, Map.of("listing", "toto"))), is(true));
    }

    @Test
    void substringCondition_shouldNotMatchWhenAbsent() {
        assertThat(trigger.matchesCondition(output("toto", 0, Map.of("listing", "something_else"))), is(false));
    }

    @Test
    void regexCondition_shouldMatchAgainstVars() {
        assertThat(trigger.matchesCondition(output("status=\\w+", 0, Map.of("status", "status=ready"))), is(true));
    }

    @Test
    void emptyCondition_shouldNotMatch() {
        assertThat(trigger.matchesCondition(output("", 0, null)), is(false));
    }

    @Test
    void nullCondition_shouldNotMatch() {
        assertThat(trigger.matchesCondition(output(null, 0, null)), is(false));
    }

    @Test
    void exitZeroCondition_shouldMatchSuccessfulExecution() {
        assertThat(trigger.matchesCondition(output("exit 0", 0, null)), is(true));
    }

    @Test
    void invalidRegex_shouldFallBackToSubstringMatch() {
        assertThat(trigger.matchesCondition(output("a[b", 0, Map.of("k", "xa[by"))), is(true));
        assertThat(trigger.matchesCondition(output("a[b", 0, Map.of("k", "nothing"))), is(false));
    }
}
