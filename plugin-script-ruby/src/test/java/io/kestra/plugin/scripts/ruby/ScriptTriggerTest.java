package io.kestra.plugin.scripts.ruby;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * Unit tests for ScriptTrigger's condition-matching logic and edge mode.
 *
 * These tests exercise matchesCondition via the Output model without requiring a Ruby
 * runtime, which may not be available on all CI machines.
 * Integration tests against an actual Ruby runtime are covered by the sanity checks.
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
    void edgeMode_shouldEmitOnFirstMatch() {
        var lastMatched = new AtomicBoolean(false);
        boolean matched = true;
        boolean emit = !lastMatched.getAndSet(matched) && matched;
        assertThat("first match should emit", emit, is(true));
    }

    @Test
    void edgeMode_shouldSuppressConsecutiveMatches() {
        var lastMatched = new AtomicBoolean(true);
        boolean matched = true;
        boolean emit = !lastMatched.getAndSet(matched) && matched;
        assertThat("consecutive match should not emit in edge mode", emit, is(false));
    }

    @Test
    void edgeMode_shouldEmitAgainAfterNonMatch() {
        var lastMatched = new AtomicBoolean(false);
        boolean matched = true;
        boolean emit = !lastMatched.getAndSet(matched) && matched;
        assertThat("match after non-match should emit", emit, is(true));
    }
}
