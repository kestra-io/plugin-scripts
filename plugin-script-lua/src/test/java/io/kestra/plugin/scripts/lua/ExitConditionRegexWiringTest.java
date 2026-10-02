package io.kestra.plugin.scripts.lua;

import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

class ExitConditionRegexWiringTest {

    @Test
    void commandsTrigger_delegatesToHelperAndReturnsItsResult() {
        CommandsTrigger trigger = CommandsTrigger.builder().build();
        String condition = "status=\\w+";
        String haystack = "{status=status=ready}";
        CommandsTrigger.Output output = new CommandsTrigger.Output(
            Instant.now(), " " + condition + " ", 0, Map.of("status", "status=ready")
        );

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
