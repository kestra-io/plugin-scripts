package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

public class ExitConditionRegexTest {

    @Test
    void timeoutMatchesThePreviousTriggerGuard() {
        assertThat(ExitConditionRegex.TIMEOUT, is(Duration.ofSeconds(5)));
    }

    @Test
    void safeRegex_matchesASubstringWithoutWaitingForTheDeadline() {
        long start = System.nanoTime();

        assertThat(ExitConditionRegex.find("status=\\w+", "{status=status=ready}"), is(true));
        assertThat(ExitConditionRegex.find("toto", "{listing=toto}"), is(true));
        assertThat(ExitConditionRegex.find("missing", "{listing=toto}"), is(false));
        assertThat(ExitConditionRegex.find(Pattern.compile("k=\\w+"), "{k=ready}"), is(true));

        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        assertThat(elapsedMs, lessThan(1000L));
    }

    @Test
    void invalidRegex_fallsBackToSubstring() {
        assertThat(ExitConditionRegex.find("[unclosed", "value with [unclosed inside"), is(true));
        assertThat(ExitConditionRegex.find("[unclosed", "nothing"), is(false));
    }

    @Test
    void catastrophicPattern_fallsBackWithinTwiceTheDeadline() {
        var timeout = Duration.ofMillis(500);
        var haystack = "{k=" + "a".repeat(40) + "!}";

        long start = System.nanoTime();
        // (a+)+$ is memoized on JDK 21+ and returns immediately; (.*a){20}$ still backtracks.
        boolean matched = assertTimeoutPreemptively(
            timeout.multipliedBy(2), () -> ExitConditionRegex.find("(.*a){20}$", haystack, timeout)
        );
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(matched, is(false));
        assertThat(elapsedMs, greaterThanOrEqualTo(timeout.toMillis() - 100));
        assertNoRegexOnCommonPool();
    }

    @Test
    void catastrophicPattern_fallsBackToSubstringWhenTheLiteralIsPresent() {
        var timeout = Duration.ofMillis(500);
        var condition = "(.*a){20}$";
        var haystack = condition + "a".repeat(40) + "!";

        long start = System.nanoTime();
        boolean matched = assertTimeoutPreemptively(
            timeout.multipliedBy(2), () -> ExitConditionRegex.find(condition, haystack, timeout)
        );
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();

        assertThat(matched, is(true));
        assertThat(elapsedMs, greaterThanOrEqualTo(timeout.toMillis() - 100));
        assertNoRegexOnCommonPool();
    }

    public static void assertNoRegexOnCommonPool() {
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while checking commonPool", e);
        }

        boolean busy = Thread.getAllStackTraces().entrySet().stream()
            .filter(entry -> entry.getKey().getName().startsWith("ForkJoinPool.commonPool-worker"))
            .flatMap(entry -> Arrays.stream(entry.getValue()))
            .anyMatch(
                frame -> frame.getClassName().startsWith("java.util.regex.")
                    || frame.getClassName().contains("TimeoutCharSequence")
            );

        assertFalse(busy, "a ForkJoinPool.commonPool thread is still inside a regex match");
    }
}
