package io.github.lokiencoded.sayso.ask;

/** Answers a spoken question, optionally about some text the user selected. */
public interface Assistant {

    /**
     * @param questionWav the question as recorded audio (WAV)
     * @param selectedText what the user had selected; empty if nothing
     * @return the answer, written to be read aloud
     * @throws AskException with a message that can be spoken to the user
     */
    String answer(byte[] questionWav, String selectedText) throws AskException;
}
