package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Bounded {@code exitCondition} regex search on the caller thread (1s deadline; substring on timeout / stack overflow).
 * Shim of Kestra 2.0 {@code RegexUtils} for 1.3.x — swap when the plugin targets 2.0+.
 */
public final class ExitConditionRegex {

    /** Match budget per poll against a few KB of script output. */
    static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final int CHECK_INTERVAL = 1024;

    private ExitConditionRegex() {
    }

    /**
     * Whether {@code condition} occurs in {@code haystack}.
     * An invalid pattern, a match that exceeds {@link #TIMEOUT}, or a regex stack overflow
     * falls back to a literal substring check.
     */
    public static boolean find(String condition, String haystack) {
        return find(condition, haystack, TIMEOUT);
    }

    public static boolean find(Pattern pattern, String haystack) {
        return find(pattern, haystack, TIMEOUT);
    }

    static boolean find(String condition, String haystack, Duration timeout) {
        try {
            return find(Pattern.compile(condition), haystack, timeout);
        } catch (PatternSyntaxException e) {
            return haystack.contains(condition);
        }
    }

    static boolean find(Pattern pattern, String haystack, Duration timeout) {
        try {
            return pattern.matcher(new TimeoutCharSequence(haystack, timeout)).find();
        } catch (RegexTimeoutException | StackOverflowError e) {
            return haystack.contains(pattern.pattern());
        }
    }

    static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException(Duration timeout) {
            super("Regex operation timed out after " + timeout.toMillis() + "ms");
        }
    }

    private static final class TimeoutCharSequence implements CharSequence {

        private final CharSequence delegate;
        private final long deadlineNanos;
        private final Duration timeout;
        private final int[] counter;

        TimeoutCharSequence(CharSequence delegate, Duration timeout) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
            this.counter = new int[1];
        }

        private TimeoutCharSequence(CharSequence delegate, long deadlineNanos, Duration timeout, int[] counter) {
            this.delegate = delegate;
            this.deadlineNanos = deadlineNanos;
            this.timeout = timeout;
            this.counter = counter;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (++counter[0] % CHECK_INTERVAL == 0 && System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException(timeout);
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new TimeoutCharSequence(delegate.subSequence(start, end), deadlineNanos, timeout, counter);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
