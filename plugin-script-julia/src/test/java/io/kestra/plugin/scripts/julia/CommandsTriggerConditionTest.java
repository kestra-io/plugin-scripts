package io.kestra.plugin.scripts.julia;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class CommandsTriggerConditionTest {

    private final CommandsTrigger trigger = CommandsTrigger.builder().build();

    private CommandsTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars) {
        return new CommandsTrigger.Output(Instant.now(), condition, exitCode, vars);
    }

    @ParameterizedTest
    @CsvSource(
        {
            "exit 0, 0, true",
            "exit 1, 1, true",
            "EXIT 1, 1, true",
            "exit 0, 1, false",
            "exit 1, 0, false",
            "exit 42, 42, true",
            "EXIT 42, 42, true",
            "'   exit   42   ', 42, true",
            "'   EXIT   42   ', 42, true"
        }
    )
    void exitCodeCondition(String condition, int exitCode, boolean expected) {
        assertThat(trigger.matchesCondition(output(condition, exitCode, null)), is(expected));
    }

    @Test
    void exitCondition_nullExitCode_doesNotMatch() {
        assertThat(trigger.matchesCondition(output("exit 1", null, null)), is(false));
        assertThat(trigger.matchesCondition(output("exit 42", null, null)), is(false));
    }

    @Test
    void substringMatch_inVars() {
        assertThat(
            trigger.matchesCondition(
                output("READY", 0, Map.of("status", "READY"))
            ), is(true)
        );
    }

    @Test
    void regexMatch_inVars() {
        assertThat(
            trigger.matchesCondition(
                output("status=\\w+", 0, Map.of("status", "status=ready"))
            ), is(true)
        );
    }

    @Test
    void invalidRegexFallback_usesSubstringMatch() {
        assertThat(
            trigger.matchesCondition(
                output("[abc", 0, Map.of("raw", "prefix [abc suffix"))
            ), is(true)
        );
        assertThat(
            trigger.matchesCondition(
                output("[abc", 0, Map.of("raw", "different text"))
            ), is(false)
        );
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
    void noMatch_blankCondition() {
        assertThat(trigger.matchesCondition(output("   ", 0, Map.of("k", "v"))), is(false));
    }

    @Test
    void nullCondition_doesNotMatch() {
        assertThat(trigger.matchesCondition(output(null, 0, Map.of("k", "v"))), is(false));
    }

    @Test
    void regexCondition_delegatesToHelperAndReturnsItsResult() {
        String condition = "status=\\w+";
        String haystack = "{status=status=ready}";
        CommandsTrigger.Output output = output(" " + condition + " ", 0, Map.of("status", "status=ready"));

        try (var helper = mockStatic(ExitConditionRegex.class)) {
            helper.when(() -> ExitConditionRegex.find(
                argThat((Pattern pattern) -> pattern.pattern().equals(condition) && pattern.flags() == 0),
                eq(haystack)
            )).thenReturn(true, false);

            assertThat(trigger.matchesCondition(output), is(true));
            assertThat(trigger.matchesCondition(output), is(false));

            helper.verify(() -> ExitConditionRegex.find(
                argThat((Pattern pattern) -> pattern.pattern().equals(condition) && pattern.flags() == 0),
                eq(haystack)
            ), times(2));
            helper.verifyNoMoreInteractions();
        }
    }
}
