package io.github.lokiencoded.tellsy.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TellsyPathsTest {

    @TempDir
    Path userHome;

    @Test
    void usesTheTellsyFolderInTheUsersHome() {
        assertEquals(userHome.resolve(".tellsy"), TellsyPaths.resolve(Map.of(), userHome).home());
    }

    /** The first rename, from Sayso: settings, the API key and downloaded voices must come along. */
    @Test
    void movesTheOldSaysoFolderOnFirstRun() throws IOException {
        Path old = Files.createDirectories(userHome.resolve(".sayso").resolve("voices"));
        Files.writeString(userHome.resolve(".sayso").resolve("settings.properties"), "gemini.api_key=AIza-test");
        Files.writeString(old.resolve("amy.onnx"), "model");

        TellsyPaths paths = TellsyPaths.resolve(Map.of(), userHome);

        assertEquals(userHome.resolve(".tellsy"), paths.home());
        assertTrue(Files.readString(paths.settingsFile()).contains("AIza-test"));
        assertTrue(Files.exists(paths.voicesDir().resolve("amy.onnx")));
        assertFalse(Files.exists(userHome.resolve(".sayso")));
    }

    /** The second rename: Sayvoy users keep everything too. */
    @Test
    void movesTheSayvoyFolderOnFirstRun() throws IOException {
        Files.createDirectories(userHome.resolve(".sayvoy"));
        Files.writeString(userHome.resolve(".sayvoy").resolve("settings.properties"), "rate=4");

        TellsyPaths paths = TellsyPaths.resolve(Map.of(), userHome);

        assertEquals("rate=4", Files.readString(paths.settingsFile()));
        assertFalse(Files.exists(userHome.resolve(".sayvoy")));
    }

    @Test
    void prefersTheNewerSayvoyFolderOverSayso() throws IOException {
        Files.createDirectories(userHome.resolve(".sayso"));
        Files.writeString(userHome.resolve(".sayso").resolve("settings.properties"), "rate=1");
        Files.createDirectories(userHome.resolve(".sayvoy"));
        Files.writeString(userHome.resolve(".sayvoy").resolve("settings.properties"), "rate=4");

        TellsyPaths paths = TellsyPaths.resolve(Map.of(), userHome);

        assertEquals("rate=4", Files.readString(paths.settingsFile()));
        assertTrue(Files.exists(userHome.resolve(".sayso")));   // the older one is left alone
    }

    @Test
    void neverOverwritesAnExistingTellsyFolder() throws IOException {
        Files.createDirectories(userHome.resolve(".sayso"));
        Files.createDirectories(userHome.resolve(".sayvoy"));
        Files.createDirectories(userHome.resolve(".tellsy"));
        Files.writeString(userHome.resolve(".tellsy").resolve("settings.properties"), "rate=3");

        TellsyPaths paths = TellsyPaths.resolve(Map.of(), userHome);

        assertEquals("rate=3", Files.readString(paths.settingsFile()));
        assertTrue(Files.exists(userHome.resolve(".sayso")));    // left alone
        assertTrue(Files.exists(userHome.resolve(".sayvoy")));   // left alone
    }

    @Test
    void environmentVariableWins() {
        Path custom = userHome.resolve("custom");
        assertEquals(custom, TellsyPaths.resolve(Map.of("TELLSY_HOME", custom.toString()), userHome).home());
    }
}
