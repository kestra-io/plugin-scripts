package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ExitConditionRegex {

    static final Duration TIMEOUT = Duration.ofSeconds(1);

    private static final Logger LOG = LoggerFactory.getLogger(ExitConditionRegex.class);
    private static final int CHECK_INTERVAL = 1024;
    private static final int MAX_WARNED_CONDITIONS = 64;
    private static final Map<String, Boolean> WARNED_CONDITIONS = Collections.synchronizedMap(
        new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                return size() > MAX_WARNED_CONDITIONS;
            }
        }
    );

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
        return find(pattern, haystack, timeout, System::nanoTime);
    }

    static boolean find(Pattern pattern, String haystack, Duration timeout, LongSupplier nanoTime) {
        try {
            return pattern.matcher(new TimeoutCharSequence(haystack, timeout, nanoTime)).find();
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
        if (WARNED_CONDITIONS.putIfAbsent(condition, Boolean.TRUE) == null) {
            LOG.warn("exitCondition '{}' {}; falling back to a substring match", condition, reason);
        } else {
            LOG.debug("exitCondition '{}' {}; falling back to a substring match", condition, reason);
        }
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
        private final LongSupplier nanoTime;

        TimeoutCharSequence(CharSequence delegate, Duration timeout, LongSupplier nanoTime) {
            this.delegate = delegate;
            this.timeout = timeout;
            this.nanoTime = nanoTime;
            this.deadlineNanos = nanoTime.getAsLong() + timeout.toNanos();
            this.counter = new int[1];
        }

        private TimeoutCharSequence(CharSequence delegate, long deadlineNanos, Duration timeout, int[] counter, LongSupplier nanoTime) {
            this.delegate = delegate;
            this.deadlineNanos = deadlineNanos;
            this.timeout = timeout;
            this.counter = counter;
            this.nanoTime = nanoTime;
        }

        @Override
        public int length() {
            return delegate.length();
        }

        @Override
        public char charAt(int index) {
            if (++counter[0] % CHECK_INTERVAL == 0 && nanoTime.getAsLong() - deadlineNanos >= 0) {
                throw new RegexTimeoutException(timeout);
            }
            return delegate.charAt(index);
        }

        @Override
        public CharSequence subSequence(int start, int end) {
            return new TimeoutCharSequence(delegate.subSequence(start, end), deadlineNanos, timeout, counter, nanoTime);
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
