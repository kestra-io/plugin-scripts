package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Bounded regex search for a user-supplied {@code exitCondition}.
 *
 * <p>
 * Inlines the deadline {@link CharSequence} from {@code io.kestra.core.utils.RegexUtils}
 * (Kestra 2.0+). This plugin still compiles against Kestra 1.3.x, where that class is absent.
 * The match runs on the calling thread and stops once the deadline passes, so a pathological
 * pattern cannot occupy {@code ForkJoinPool.commonPool}. When this plugin targets Kestra 2.0
 * or newer, replace {@link TimeoutCharSequence} with
 * {@code RegexUtils.matcher(pattern, input, timeout)}.
 */
public final class ExitConditionRegex {

    static final Duration TIMEOUT = Duration.ofSeconds(5);

    private static final int CHECK_INTERVAL = 1024;

    private ExitConditionRegex() {
    }

    /**
     * Whether {@code condition} occurs in {@code haystack}.
     * An invalid pattern, or one that exceeds {@link #TIMEOUT}, falls back to a literal substring check.
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
        } catch (RegexTimeoutException e) {
            return haystack.contains(pattern.pattern());
        }
    }

    static final class RegexTimeoutException extends RuntimeException {
        RegexTimeoutException(Duration timeout) {
            super("Regex operation timed out after " + timeout.toMillis() + "ms");
        }
    }

    /**
     * Checks a shared deadline from {@code charAt}, including on subsequences the regex engine slices off.
     */
    private static final class TimeoutCharSequence implements CharSequence {

        private final CharSequence delegate;
        private final long deadlineNanos;
        private final Duration timeout;
        private int counter;

        TimeoutCharSequence(CharSequence delegate, Duration timeout) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.deadlineNanos = System.nanoTime() + timeout.toNanos();
        }

        private TimeoutCharSequence(CharSequence delegate, long deadlineNanos) {
            this.delegate = delegate;
            this.deadlineNanos = deadlineNanos;
            this.timeout = Duration.ofNanos(Math.max(0, deadlineNanos - System.nanoTime()));
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (++counter % CHECK_INTERVAL == 0 && System.nanoTime() > deadlineNanos) {
                throw new RegexTimeoutException(timeout);
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new TimeoutCharSequence(delegate.subSequence(start, end), deadlineNanos);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
