package io.kestra.plugin.scripts.exec;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Shared assertions for exitCondition regex guards across trigger modules.
 */
public final class ExitConditionRegexTestSupport {

    private ExitConditionRegexTestSupport() {
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
