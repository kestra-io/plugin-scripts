package io.kestra.plugin.scripts.jbang;

import io.kestra.plugin.scripts.exec.ExitConditionRegex;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
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
            helper.when(() -> ExitConditionRegex.find(condition, haystack)).thenReturn(true, false);

            assertThat(trigger.matchesCondition(output), is(true));
            assertThat(trigger.matchesCondition(output), is(false));

            helper.verify(() -> ExitConditionRegex.find(condition, haystack), times(2));
            helper.verifyNoMoreInteractions();
        }
    }
}
