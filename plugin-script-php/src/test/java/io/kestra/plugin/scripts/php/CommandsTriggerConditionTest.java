package io.kestra.plugin.scripts.php;

import io.kestra.plugin.scripts.exec.ExitConditionRegex;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class CommandsTriggerConditionTest {

    private final CommandsTrigger trigger = CommandsTrigger.builder().build();

    private CommandsTrigger.Output output(String condition, Integer exitCode, Map<String, Object> vars) {
        return new CommandsTrigger.Output(Instant.now(), condition, exitCode, vars);
    }

    @ParameterizedTest
    @CsvSource({
        "exit 0, 0, true",
        "exit 1, 1, true",
        "exit 1, 0, false",
    })
    void exitCodeCondition(String condition, int exitCode, boolean expected) {
        assertThat(trigger.matchesCondition(output(condition, exitCode, null)), is(expected));
    }

    @Test
    void regexCondition_delegatesToHelperAndReturnsItsResult() {
        String condition = "status=\\w+";
        String haystack = "{status=status=ready}";
        var out = output(condition, 0, Map.of("status", "status=ready"));

        try (var helper = mockStatic(ExitConditionRegex.class)) {
            helper.when(() -> ExitConditionRegex.find(condition, haystack)).thenReturn(true);

            assertThat(trigger.matchesCondition(out), is(true));

            helper.verify(() -> ExitConditionRegex.find(condition, haystack), times(1));
            helper.verifyNoMoreInteractions();
        }
    }
}
