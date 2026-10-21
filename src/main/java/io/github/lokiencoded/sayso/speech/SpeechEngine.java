package io.github.lokiencoded.sayso.speech;

import java.util.List;

/**
 * Turns text into speech. v0.1 uses the voices built into Windows; v0.2 will add
 * natural neural voices as another implementation of this same interface,
 * without touching the rest of the app.
 */
public interface SpeechEngine {

    /**
     * Starts speaking {@code text} in the background, cutting off anything already playing.
     *
     * @param rate      speed from -10 (slowest) to 10 (fastest)
     * @param voiceName a name from {@link #voices()}, or null for the default voice
     */
    void speak(String text, int rate, String voiceName);

    /** Stops speaking immediately. Safe to call when nothing is playing. */
    void stop();

    /** True while something is still being spoken. */
    boolean isSpeaking();

    /** Names of the installed voices, in a stable order. */
    List<String> voices();
}
