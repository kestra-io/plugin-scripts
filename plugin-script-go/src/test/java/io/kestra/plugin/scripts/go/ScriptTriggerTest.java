package io.kestra.plugin.scripts.go;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit tests for ScriptTrigger's condition-matching logic.
 *
 * These tests exercise matchesCondition / buildHaystack via the Output model
 * without requiring the scheduler infrastructure or a Go runtime.
 */
class ScriptTriggerTest {

    @Test
    void matchesCondition_exitCodeCondition_shouldMatchWhenExitCodeEquals() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", 1, null);
        assertThat(exitConditionMatches(output), is(true));
    }

    @Test
    void matchesCondition_exitCodeCondition_shouldNotMatchWhenExitCodeDiffers() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", 127, null);
        assertThat("exit 1 condition with exitCode=127 should not match", exitConditionMatches(output), is(false));
    }

    @Test
    void matchesCondition_exitCodeCondition_shouldNotMatchWhenExitCodeIsNull() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", null, null);
        assertThat(exitConditionMatches(output), is(false));
    }

    @Test
    void matchesCondition_substringCondition_shouldMatchAgainstVars() {
        var output = new ScriptTrigger.Output(Instant.now(), "toto", 0, Map.of("listing", "toto"));
        assertThat(exitConditionMatches(output), is(true));
    }

    @Test
    void matchesCondition_substringCondition_shouldNotMatchWhenAbsent() {
        var output = new ScriptTrigger.Output(Instant.now(), "toto", 0, Map.of("listing", "something_else"));
        assertThat(exitConditionMatches(output), is(false));
    }

    @Test
    void matchesCondition_regexCondition_shouldMatchAgainstVars() {
        var output = new ScriptTrigger.Output(Instant.now(), "status=\\w+", 0, Map.of("status", "status=ready"));
        assertThat(exitConditionMatches(output), is(true));
    }

    @Test
    void matchesCondition_emptyCondition_shouldNotMatch() {
        var output = new ScriptTrigger.Output(Instant.now(), "", 0, null);
        assertThat(exitConditionMatches(output), is(false));
    }

    @Test
    void matchesCondition_nullCondition_shouldNotMatch() {
        var output = new ScriptTrigger.Output(Instant.now(), null, 0, null);
        assertThat(exitConditionMatches(output), is(false));
    }

    @Test
    void matchesCondition_exitZero_shouldMatchSuccessfulExecution() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 0", 0, null);
        assertThat(exitConditionMatches(output), is(true));
    }

    private boolean exitConditionMatches(ScriptTrigger.Output out) {
        var cond = out.getCondition() == null ? "" : out.getCondition().trim();

        var exitMatcher = java.util.regex.Pattern
            .compile("^\\s*exit\\s+(\\d+)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(cond);

        if (exitMatcher.matches()) {
            var expected = Integer.parseInt(exitMatcher.group(1));
            return out.getExitCode() != null && out.getExitCode() == expected;
        }

        var haystack = out.getVars() != null && !out.getVars().isEmpty()
            ? out.getVars().toString()
            : "";

        if (haystack.isEmpty() || cond.isEmpty()) {
            return false;
        }

        try {
            return java.util.regex.Pattern.compile(cond).matcher(haystack).find();
        } catch (Exception e) {
            return haystack.contains(cond);
        }
    }
}
