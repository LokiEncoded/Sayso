package io.github.lokiencoded.sayvoy.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SayvoyPathsTest {

    @TempDir
    Path userHome;

    @Test
    void usesTheSayvoyFolderInTheUsersHome() {
        assertEquals(userHome.resolve(".sayvoy"), SayvoyPaths.resolve(Map.of(), userHome).home());
    }

    /** The rename from Sayso: settings, the API key and downloaded voices must come along. */
    @Test
    void movesTheOldSaysoFolderOnFirstRun() throws IOException {
        Path old = Files.createDirectories(userHome.resolve(".sayso").resolve("voices"));
        Files.writeString(userHome.resolve(".sayso").resolve("settings.properties"), "gemini.api_key=AIza-test");
        Files.writeString(old.resolve("amy.onnx"), "model");

        SayvoyPaths paths = SayvoyPaths.resolve(Map.of(), userHome);

        assertEquals(userHome.resolve(".sayvoy"), paths.home());
        assertTrue(Files.readString(paths.settingsFile()).contains("AIza-test"));
        assertTrue(Files.exists(paths.voicesDir().resolve("amy.onnx")));
        assertFalse(Files.exists(userHome.resolve(".sayso")));
    }

    @Test
    void neverOverwritesAnExistingSayvoyFolder() throws IOException {
        Files.createDirectories(userHome.resolve(".sayso"));
        Files.createDirectories(userHome.resolve(".sayvoy"));
        Files.writeString(userHome.resolve(".sayvoy").resolve("settings.properties"), "rate=3");

        SayvoyPaths paths = SayvoyPaths.resolve(Map.of(), userHome);

        assertEquals("rate=3", Files.readString(paths.settingsFile()));
        assertTrue(Files.exists(userHome.resolve(".sayso")));   // left alone
    }

    @Test
    void environmentVariableWins() {
        Path custom = userHome.resolve("custom");
        assertEquals(custom, SayvoyPaths.resolve(Map.of("SAYVOY_HOME", custom.toString()), userHome).home());
    }
}
