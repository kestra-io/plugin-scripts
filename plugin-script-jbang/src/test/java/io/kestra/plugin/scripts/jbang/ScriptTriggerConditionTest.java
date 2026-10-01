package io.kestra.plugin.scripts.jbang;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ScriptTriggerConditionTest {

    private final ScriptTrigger trigger = ScriptTrigger.builder().build();

    @Test
    void catastrophicBacktrackingRegex_fallsBackToSubstring_withoutHanging() {
        String condition = "(.*a){20}$";
        String value = "a".repeat(40) + "!";

        long start = System.nanoTime();
        assertTimeoutPreemptively(
            Duration.ofSeconds(4), () -> assertThat(
                trigger.matchesCondition(new ScriptTrigger.Output(Instant.now(), condition, 0, Map.of("k", value))),
                is(false)
            )
        );
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(elapsedMs, greaterThanOrEqualTo(500L));
    }
}
