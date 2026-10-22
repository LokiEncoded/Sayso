package io.github.lokiencoded.tellsy.core;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecentTextTest {

    /** A clock the test can move forward. */
    private static final class TestClock extends Clock {
        Instant now = Instant.parse("2026-10-22T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private final TestClock clock = new TestClock();
    private final RecentText recent = new RecentText(clock);

    @Test
    void remembersTheLatestText() {
        recent.remember("first");
        recent.remember("second");
        assertEquals("second", recent.get());
    }

    @Test
    void forgetsAfterAWhile() {
        recent.remember("an old message");
        clock.now = clock.now.plus(RecentText.KEEP_FOR).plus(Duration.ofSeconds(1));
        assertEquals("", recent.get());
    }

    @Test
    void blankTextDoesNotReplaceTheLastMessage() {
        recent.remember("the message");
        recent.remember("  ");
        assertEquals("the message", recent.get());
    }

    @Test
    void veryLongTextIsTrimmed() {
        recent.remember("x".repeat(RecentText.MAX_CHARS + 100));
        assertEquals(RecentText.MAX_CHARS, recent.get().length());
    }
}
