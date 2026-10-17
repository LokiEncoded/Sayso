package io.github.lokiencoded.sayso.core;

/** The user's current voice preferences. Kept in memory for now; saved to disk in a later version. */
public final class Settings {

    public static final int MIN_RATE = -10;
    public static final int MAX_RATE = 10;

    private int rate = 1;
    private int voiceIndex = -1;    // -1 means "the system's default voice"
    private String voiceName;       // null means "the system's default voice"

    public int rate() {
        return rate;
    }

    /** Changes speed by {@code step}, staying within MIN_RATE..MAX_RATE. */
    public void changeRate(int step) {
        rate = Math.max(MIN_RATE, Math.min(MAX_RATE, rate + step));
    }

    public int voiceIndex() {
        return voiceIndex;
    }

    public String voiceName() {
        return voiceName;
    }

    /** Index of the voice after the current one, in a list of {@code count} voices, wrapping at the end. */
    public int nextVoiceIndex(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive, was " + count);
        }
        return (voiceIndex + 1) % count;
    }

    public void selectVoice(int index, String name) {
        this.voiceIndex = index;
        this.voiceName = name;
    }
}
