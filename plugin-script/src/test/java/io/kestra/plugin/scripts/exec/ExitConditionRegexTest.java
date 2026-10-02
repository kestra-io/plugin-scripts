package io.kestra.plugin.scripts.exec;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ExitConditionRegexTest {

    @Test
    void timeoutMatchesTheDocumentedMatchBudget() {
        assertThat(ExitConditionRegex.TIMEOUT, is(Duration.ofSeconds(1)));
    }

    @Test
    void safeRegex_matchesASubstring() {
        assertThat(ExitConditionRegex.find("status=\\w+", "{status=status=ready}"), is(true));
        assertThat(ExitConditionRegex.find("toto", "{listing=toto}"), is(true));
        assertThat(ExitConditionRegex.find("missing", "{listing=toto}"), is(false));
        assertThat(ExitConditionRegex.find(Pattern.compile("k=\\w+"), "{k=ready}"), is(true));
    }

    @Test
    void invalidRegex_fallsBackToSubstring() {
        assertThat(ExitConditionRegex.find("[unclosed", "value with [unclosed inside"), is(true));
        assertThat(ExitConditionRegex.find("[unclosed", "nothing"), is(false));
    }

    @Test
    void catastrophicPattern_checksInjectedDeadlineAndFallsBack() {
        var timeout = Duration.ofMillis(10);
        var condition = "(.*a){20}$";
        for (boolean literalPresent : new boolean[] { false, true }) {
            var clockReads = new AtomicInteger();
            var haystack = (literalPresent ? condition : "") + "a".repeat(40) + "!";
            boolean matched = assertTimeoutPreemptively(
                Duration.ofSeconds(5), () -> ExitConditionRegex.find(
                    Pattern.compile(condition), haystack, timeout,
                    () -> clockReads.getAndIncrement() < 2 ? 0 : timeout.toNanos()
                )
            );
            assertThat(matched, is(literalPresent));
            assertThat(clockReads.get(), is(3));
        }
    }

    @Test
    void timeoutDoesNotBlacklistPatternForLaterOutput() {
        var pattern = Pattern.compile("(.*a){20}$");
        assertTimeoutPreemptively(
            Duration.ofSeconds(5),
            () -> assertThat(ExitConditionRegex.find(pattern, "a".repeat(40) + "!", Duration.ZERO), is(false))
        );
        assertThat(ExitConditionRegex.find(pattern, "a".repeat(20)), is(true));
    }

    @Test
    void stackOverflowPattern_fallsBackToSubstring() {
        var condition = "(a|b)*c";
        var haystack = "a".repeat(100_000);

        boolean matched = assertTimeoutPreemptively(
            Duration.ofSeconds(5), () -> ExitConditionRegex.find(condition, haystack)
        );

        assertThat(matched, is(false));
    }

    @Test
    void repeatedFallback_warnsOnceThenLogsDebug() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExitConditionRegex.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.DEBUG);
        try {
            var condition = "[" + UUID.randomUUID();
            assertThat(ExitConditionRegex.find(condition, condition), is(true));
            assertThat(ExitConditionRegex.find(condition, "absent"), is(false));

            assertThat(appender.list.size(), is(2));
            assertThat(appender.list.get(0).getLevel(), is(Level.WARN));
            assertThat(appender.list.get(1).getLevel(), is(Level.DEBUG));
            for (var event : appender.list) {
                assertThat(event.getFormattedMessage(), containsString(condition));
                assertThat(event.getFormattedMessage(), containsString("is not a valid regex"));
            }
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    void warningCache_evictsOldConditions() {
        Logger logger = (Logger) LoggerFactory.getLogger(ExitConditionRegex.class);
        Level previousLevel = logger.getLevel();
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            var prefix = "[" + UUID.randomUUID();
            for (int i = 0; i <= 64; i++) {
                ExitConditionRegex.find(prefix + i, "");
            }
            ExitConditionRegex.find(prefix + 0, "");
            assertThat(appender.list.size(), is(66));
        } finally {
            logger.setLevel(previousLevel);
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
