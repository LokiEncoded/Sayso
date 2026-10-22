package io.github.lokiencoded.sayso.ask;

/**
 * Something went wrong while asking. The message is written to be spoken to the
 * user as is ("I couldn't reach Gemini..."), so keep it short and plain.
 */
public final class AskException extends Exception {

    private final boolean temporary;

    public AskException(String spokenMessage) {
        this(spokenMessage, false, null);
    }

    public AskException(String spokenMessage, Throwable cause) {
        this(spokenMessage, false, cause);
    }

    /**
     * @param temporary true when trying again (or with another model) may well work,
     *                  such as "model overloaded" or a timeout; false for a bad key or bad request
     */
    public AskException(String spokenMessage, boolean temporary, Throwable cause) {
        super(spokenMessage, cause);
        this.temporary = temporary;
    }

    public boolean isTemporary() {
        return temporary;
    }
}
