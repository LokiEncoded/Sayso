package io.github.lokiencoded.sayvoy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Where Sayvoy keeps its files on the user's computer.
 * Defaults to a ".sayvoy" folder in the user's home; the SAYVOY_HOME
 * environment variable overrides it (handy for testing).
 *
 * <pre>
 * ~/.sayvoy/
 * ├── settings.properties    voice, speed, shortcuts and Gemini key (created on first run)
 * ├── piper/piper.exe        the neural speech engine
 * └── voices/*.onnx          voice models, each with a matching .onnx.json
 * </pre>
 */
public record SayvoyPaths(Path home) {

    private static final Logger log = LoggerFactory.getLogger(SayvoyPaths.class);

    static final String FOLDER = ".sayvoy";

    /** The folder used before the product was renamed from Sayso to Sayvoy. */
    static final String OLD_FOLDER = ".sayso";

    public static SayvoyPaths fromEnvironment() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    /**
     * Picks the home folder. On the first run after the rename, the old ~/.sayso folder
     * (settings, API key, downloaded voices) is moved to ~/.sayvoy so nothing is lost.
     */
    static SayvoyPaths resolve(Map<String, String> env, Path userHome) {
        String override = env.get("SAYVOY_HOME");
        if (override != null && !override.isBlank()) {
            return new SayvoyPaths(Path.of(override));
        }
        Path home = userHome.resolve(FOLDER);
        Path old = userHome.resolve(OLD_FOLDER);
        if (!Files.exists(home) && Files.isDirectory(old)) {
            try {
                Files.move(old, home);
                log.info("Moved your settings and voices from {} to {}", old, home);
            } catch (IOException e) {
                // Still usable: keep reading from the old folder rather than starting empty.
                log.warn("Could not move {} to {}; using the old folder for now", old, home, e);
                return new SayvoyPaths(old);
            }
        }
        return new SayvoyPaths(home);
    }

    public Path piperExecutable() {
        return home.resolve("piper").resolve("piper.exe");
    }

    public Path voicesDir() {
        return home.resolve("voices");
    }

    public Path settingsFile() {
        return home.resolve("settings.properties");
    }
}
