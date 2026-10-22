package io.github.lokiencoded.sayso.ask;

/**
 * Something went wrong while asking. The message is written to be spoken to the
 * user as is ("I couldn't reach Gemini..."), so keep it short and plain.
 */
public final class AskException extends Exception {

    public AskException(String spokenMessage) {
        super(spokenMessage);
    }

    public AskException(String spokenMessage, Throwable cause) {
        super(spokenMessage, cause);
    }
}
