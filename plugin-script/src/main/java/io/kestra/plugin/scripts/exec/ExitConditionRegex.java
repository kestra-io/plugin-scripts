package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Caller-thread exitCondition match with a 1s deadline and substring fallback. */
public final class ExitConditionRegex {

    static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final Logger LOG = LoggerFactory.getLogger(ExitConditionRegex.class);
    private static final int CHECK_INTERVAL = 1024;

    private ExitConditionRegex() {
    }

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
            return invalidPatternFallback(condition, haystack);
        }
    }

    static boolean find(Pattern pattern, String haystack, Duration timeout) {
        try {
            return pattern.matcher(new TimeoutCharSequence(haystack, timeout)).find();
        } catch (RegexTimeoutException e) {
            return substringFallback(pattern.pattern(), haystack, "timed out");
        } catch (StackOverflowError e) {
            return substringFallback(pattern.pattern(), haystack, "overflowed the stack");
        }
    }

    public static boolean invalidPatternFallback(String condition, String haystack) {
        return substringFallback(condition, haystack, "is not a valid regex");
    }

    private static boolean substringFallback(String condition, String haystack, String reason) {
        LOG.warn("exitCondition '{}' {}; falling back to a substring match", condition, reason);
        return haystack.contains(condition);
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
