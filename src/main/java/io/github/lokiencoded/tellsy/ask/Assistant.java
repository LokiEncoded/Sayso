package io.github.lokiencoded.tellsy.ask;

/** The AI behind Ask and Reply. */
public interface Assistant {

    /**
     * Answers a spoken question, optionally about some text the user selected.
     *
     * @param questionWav  the question as recorded audio (WAV)
     * @param selectedText what the user had selected; empty if nothing
     * @return the answer, written to be read aloud
     * @throws AskException with a message that can be spoken to the user
     */
    String answer(byte[] questionWav, String selectedText) throws AskException;

    /**
     * Turns what the user said into clean, ready-to-send text, as if they had typed it carefully.
     *
     * @param speechWav     what the user said (WAV)
     * @param recentContext what Tellsy recently read to the user (often the message being replied to),
     *                      used only to get names, terms and tone right; empty if none
     * @return the text to type into the user's app
     * @throws AskException with a message that can be spoken to the user
     */
    String writeReply(byte[] speechWav, String recentContext) throws AskException;
}
