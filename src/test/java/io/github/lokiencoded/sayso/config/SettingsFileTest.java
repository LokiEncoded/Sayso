package io.github.lokiencoded.sayso.config;

import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.Settings;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsFileTest {

    @TempDir
    Path dir;

    @Test
    void createsFileWithDefaultsOnFirstRun() throws IOException {
        Path path = dir.resolve("nested").resolve("settings.properties");
        Settings settings = new SettingsFile(path).load();

        assertTrue(Files.exists(path));
        assertEquals(Settings.DEFAULT_RATE, settings.rate());
        assertNull(settings.voiceName());
        assertTrue(Files.readString(path).contains("shortcut.stop=ctrl+alt+X"));
    }

    @Test
    void savedSettingsComeBackAfterRestart() {
        SettingsFile file = new SettingsFile(dir.resolve("settings.properties"));
        Settings before = file.load();
        before.setRate(4);
        before.setVoiceName("en_US-amy-medium");
        before.setShortcut(Command.STOP, Shortcut.parse("ctrl+shift+F12").orElseThrow());
        file.save(before);

        Settings after = file.load();

        assertEquals(4, after.rate());
        assertEquals("en_US-amy-medium", after.voiceName());
        assertEquals("Ctrl+Shift+F12", after.shortcut(Command.STOP).label());
    }

    @Test
    void readsAFileSomeoneEditedByHand() throws IOException {
        Path path = dir.resolve("settings.properties");
        Files.writeString(path, """
                # my settings
                voice = en_GB-alba-medium
                rate=-3
                shortcut.read_selection = ctrl + shift + R
                """);

        Settings settings = new SettingsFile(path).load();

        assertEquals("en_GB-alba-medium", settings.voiceName());
        assertEquals(-3, settings.rate());
        assertEquals("Ctrl+Shift+R", settings.shortcut(Command.READ_SELECTION).label());
        assertEquals("Ctrl+Alt+X", settings.shortcut(Command.STOP).label());   // untouched = default
    }

    @Test
    void badValuesFallBackToDefaultsAndAreReported() throws IOException {
        Settings settings = new Settings();
        List<String> problems = SettingsFile.apply(properties("""
                rate=fast
                shortcut.stop=ctrl+banana
                """), settings);

        assertEquals(Settings.DEFAULT_RATE, settings.rate());
        assertEquals("Ctrl+Alt+X", settings.shortcut(Command.STOP).label());
        assertEquals(2, problems.size());
    }

    @Test
    void outOfRangeSpeedIsClamped() throws IOException {
        Settings settings = new Settings();
        SettingsFile.apply(properties("rate=50"), settings);
        assertEquals(Settings.MAX_RATE, settings.rate());
    }

    @Test
    void reportsTheSameShortcutUsedTwice() throws IOException {
        Settings settings = new Settings();
        List<String> problems = SettingsFile.apply(properties("shortcut.stop=ctrl+alt+R"), settings);
        assertEquals(1, problems.size());
        assertTrue(problems.getFirst().contains("Ctrl+Alt+R"), problems.getFirst());
    }

    @Test
    void writtenFileExplainsEverySetting() {
        String text = SettingsFile.render(new Settings());
        for (Command command : Command.withShortcuts()) {
            assertTrue(text.contains(command.settingsKey() + "="), command.settingsKey());
        }
        assertTrue(text.contains("# Speed"), text);
    }

    private static Properties properties(String text) throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text));
        return properties;
    }
}
