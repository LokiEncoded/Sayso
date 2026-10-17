package io.github.lokiencoded.sayso.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettingsTest {

    @Test
    void rateNeverGoesAboveMaximum() {
        Settings settings = new Settings();
        settings.changeRate(100);
        assertEquals(Settings.MAX_RATE, settings.rate());
    }

    @Test
    void rateNeverGoesBelowMinimum() {
        Settings settings = new Settings();
        settings.changeRate(-100);
        assertEquals(Settings.MIN_RATE, settings.rate());
    }

    @Test
    void startsWithDefaultVoice() {
        Settings settings = new Settings();
        assertNull(settings.voiceName());
        assertEquals(0, settings.nextVoiceIndex(3));
    }

    @Test
    void voiceIndexWrapsAroundAtTheEnd() {
        Settings settings = new Settings();
        settings.selectVoice(2, "Voice C");
        assertEquals(0, settings.nextVoiceIndex(3));
    }

    @Test
    void rejectsEmptyVoiceList() {
        assertThrows(IllegalArgumentException.class, () -> new Settings().nextVoiceIndex(0));
    }
}
