package io.kestra.plugin.scripts.perl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CommandsTriggerConditionTest {

    private final CommandsTrigger trigger = CommandsTrigger.builder().build();

    private CommandsTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars) {
        return new CommandsTrigger.Output(Instant.now(), condition, exitCode, vars);
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
        assertThat(trigger.matchesCondition(output(condition, exitCode, null)), is(expected));
    }

    @Test
    void exitCondition_nullExitCode_doesNotMatch() {
        assertThat(trigger.matchesCondition(output("exit 1", null, null)), is(false));
    }

    @Test
    void substringMatch_inVars() {
        assertThat(trigger.matchesCondition(
            output("toto", 0, Map.of("key", "toto"))), is(true));
    }

    @Test
    void regexMatch_inVars() {
        assertThat(trigger.matchesCondition(
            output("status=\\w+", 0, Map.of("status", "status=ready"))), is(true));
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
    void catastrophicBacktrackingRegex_fallsBackToSubstring_withoutHanging() {
        // (a+)+$ is memoized by the JDK 25 regex engine and returns near-instantly, so it no longer
        // exercises the 5s timeout guard; (.*a){20}$ still triggers catastrophic backtracking there.
        String condition = "(.*a){20}$";
        String value = "a".repeat(40) + "!";

        long start = System.nanoTime();
        assertTimeoutPreemptively(Duration.ofSeconds(15), () ->
            assertThat(trigger.matchesCondition(output(condition, 0, Map.of("k", value))), is(false))
        );
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        // Confirms the 5s timeout guard actually tripped rather than a fast regex miss.
        assertThat(elapsedMs, greaterThanOrEqualTo(4000L));
    }

    @Test
    void invalidRegex_fallsBackToSubstring() {
        assertThat(trigger.matchesCondition(
            output("[unclosed", 0, Map.of("k", "value with [unclosed inside"))), is(true));
    }
}
