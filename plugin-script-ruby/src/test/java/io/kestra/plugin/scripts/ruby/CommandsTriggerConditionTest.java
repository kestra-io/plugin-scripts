package io.kestra.plugin.scripts.ruby;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class CommandsTriggerConditionTest {

    private final CommandsTrigger trigger = CommandsTrigger.builder().build();

    private CommandsTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars, String logs) {
        return new CommandsTrigger.Output(Instant.now(), condition, exitCode, vars, logs);
    }

    @ParameterizedTest
    @CsvSource({
        "exit 0, 0, true",
        "exit 1, 1, true",
        "EXIT 1, 1, true",
        "exit 0, 1, false",
        "exit 1, 0, false",
        "exit 42, 42, true",
    })
    void exitCodeCondition(String condition, int exitCode, boolean expected) {
        assertThat(trigger.matchesCondition(output(condition, exitCode, null, null)), is(expected));
    }

    @Test
    void exitCondition_nullExitCode_doesNotMatch() {
        assertThat(trigger.matchesCondition(output("exit 1", null, null, null)), is(false));
    }

    @Test
    void substringMatch_inVars() {
        assertThat(trigger.matchesCondition(
            output("toto", 0, Map.of("key", "toto"), null)), is(true));
    }

    @Test
    void substringMatch_inLogs() {
        assertThat(trigger.matchesCondition(
            output("error", 1, null, "some error occurred")), is(true));
    }

    @Test
    void regexMatch_inLogs() {
        assertThat(trigger.matchesCondition(
            output("err.*red", 1, null, "some error occurred")), is(true));
    }

    @Test
    void noMatch_emptyHaystack() {
        assertThat(trigger.matchesCondition(output("something", 0, null, null)), is(false));
    }

    @Test
    void noMatch_emptyCondition() {
        assertThat(trigger.matchesCondition(output("", 0, Map.of("k", "v"), null)), is(false));
    }

    @Test
    void nullCondition_doesNotMatch() {
        assertThat(trigger.matchesCondition(output(null, 0, Map.of("k", "v"), null)), is(false));
    }
}
