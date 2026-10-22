package io.github.lokiencoded.sayso.ask;

/**
 * Something went wrong while asking. The message is written to be spoken to the
 * user as is ("I couldn't reach Gemini..."), so keep it short and plain.
 */
public final class AskException extends Exception {

    /** What kind of failure this is decides whether another try, or another model, can help. */
    public enum Kind {
        /** Overloaded, rate-limited or timed out: trying again or another model may well work. */
        TEMPORARY,
        /** The model name isn't accepted: the other model may still work. */
        BAD_MODEL,
        /** A bad key, no network, a bad request: no retry can help. */
        PERMANENT
    }

    private final Kind kind;

    public AskException(String spokenMessage) {
        this(spokenMessage, Kind.PERMANENT, null);
    }

    public AskException(String spokenMessage, Throwable cause) {
        this(spokenMessage, Kind.PERMANENT, cause);
    }

    public AskException(String spokenMessage, Kind kind, Throwable cause) {
        super(spokenMessage, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isTemporary() {
        return kind == Kind.TEMPORARY;
    }

    /** True when asking another model might succeed. */
    public boolean anotherModelMightWork() {
        return kind != Kind.PERMANENT;
    }
}
