package io.kestra.plugin.scripts.go;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ScriptTriggerTest {

    private final ScriptTrigger trigger = ScriptTrigger.builder().build();

    @Test
    void matchesCondition_exitCodeCondition_shouldMatchWhenExitCodeEquals() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", 1, null);
        assertThat(trigger.matchesCondition(output), is(true));
    }

    @Test
    void matchesCondition_exitCodeCondition_shouldNotMatchWhenExitCodeDiffers() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", 127, null);
        assertThat("exit 1 condition with exitCode=127 should not match", trigger.matchesCondition(output), is(false));
    }

    @Test
    void matchesCondition_exitCodeCondition_shouldNotMatchWhenExitCodeIsNull() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 1", null, null);
        assertThat(trigger.matchesCondition(output), is(false));
    }

    @Test
    void matchesCondition_substringCondition_shouldMatchAgainstVars() {
        var output = new ScriptTrigger.Output(Instant.now(), "toto", 0, Map.of("listing", "toto"));
        assertThat(trigger.matchesCondition(output), is(true));
    }

    @Test
    void matchesCondition_substringCondition_shouldNotMatchWhenAbsent() {
        var output = new ScriptTrigger.Output(Instant.now(), "toto", 0, Map.of("listing", "something_else"));
        assertThat(trigger.matchesCondition(output), is(false));
    }

    @Test
    void matchesCondition_regexCondition_shouldMatchAgainstVars() {
        var output = new ScriptTrigger.Output(Instant.now(), "status=\\w+", 0, Map.of("status", "status=ready"));
        assertThat(trigger.matchesCondition(output), is(true));
    }

    @Test
    void matchesCondition_emptyCondition_shouldNotMatch() {
        var output = new ScriptTrigger.Output(Instant.now(), "", 0, null);
        assertThat(trigger.matchesCondition(output), is(false));
    }

    @Test
    void matchesCondition_nullCondition_shouldNotMatch() {
        var output = new ScriptTrigger.Output(Instant.now(), null, 0, null);
        assertThat(trigger.matchesCondition(output), is(false));
    }

    @Test
    void matchesCondition_exitZero_shouldMatchSuccessfulExecution() {
        var output = new ScriptTrigger.Output(Instant.now(), "exit 0", 0, null);
        assertThat(trigger.matchesCondition(output), is(true));
    }
}
