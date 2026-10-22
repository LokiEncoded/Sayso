package io.github.lokiencoded.sayvoy.core;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void startsWithDefaultVoiceAndShortcuts() {
        Settings settings = new Settings();
        assertNull(settings.voiceName());
        assertEquals("Ctrl+Alt+R", settings.shortcut(Command.READ_SELECTION).label());
        assertEquals(Command.withShortcuts().size(), settings.shortcuts().size());
    }

    @Test
    void nextVoiceStartsAtTheFirstAndWraps() {
        Settings settings = new Settings();
        List<String> voices = List.of("alba", "amy", "lessac");
        assertEquals(Optional.of("alba"), settings.nextVoice(voices));
        settings.setVoiceName("lessac");
        assertEquals(Optional.of("alba"), settings.nextVoice(voices));
        settings.setVoiceName("alba");
        assertEquals(Optional.of("amy"), settings.nextVoice(voices));
    }

    @Test
    void savedVoiceThatIsNoLongerInstalledStartsOver() {
        Settings settings = new Settings();
        settings.setVoiceName("uninstalled-voice");
        assertEquals(Optional.of("alba"), settings.nextVoice(List.of("alba", "amy")));
    }

    @Test
    void noVoicesMeansNoNextVoice() {
        assertEquals(Optional.empty(), new Settings().nextVoice(List.of()));
    }

    @Test
    void blankVoiceNameMeansDefault() {
        Settings settings = new Settings();
        settings.setVoiceName("  ");
        assertNull(settings.voiceName());
    }
}
