package io.github.lokiencoded.sayso.ask;

/** Records the user's voice between start() and stop(). */
public interface Recorder {

    /** Starts recording from the default microphone. */
    void start() throws AskException;

    /** Stops recording and returns what was heard, as a WAV file. Empty if nothing was recorded. */
    Recording stop();

    boolean isRecording();

    /** A finished recording. */
    record Recording(byte[] wav, long durationMillis) {
        public static final Recording EMPTY = new Recording(new byte[0], 0);
    }
}
