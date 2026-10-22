package io.github.lokiencoded.sayso.core;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Remembers what Sayso last read to you (or what you last asked about), so a Reply
 * can use it as context: names spelled right, the right tone, "it" meaning the right thing.
 *
 * <p>Forgotten after a while, so an old message never leaks into an unrelated reply.
 */
public final class RecentText {

    static final Duration KEEP_FOR = Duration.ofMinutes(10);
    static final int MAX_CHARS = 8_000;

    private final Clock clock;
    private String text = "";
    private Instant at = Instant.EPOCH;

    public RecentText() {
        this(Clock.systemUTC());
    }

    RecentText(Clock clock) {
        this.clock = clock;
    }

    public synchronized void remember(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        this.text = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        this.at = clock.instant();
    }

    /** The remembered text, or an empty string if there is none or it is too old. */
    public synchronized String get() {
        return Duration.between(at, clock.instant()).compareTo(KEEP_FOR) <= 0 ? text : "";
    }
}
