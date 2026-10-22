package io.github.lokiencoded.tellsy.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Where Tellsy keeps its files on the user's computer.
 * Defaults to a ".tellsy" folder in the user's home; the TELLSY_HOME
 * environment variable overrides it (handy for testing).
 *
 * <pre>
 * ~/.tellsy/
 * ├── settings.properties    voice, speed, shortcuts and Gemini key (created on first run)
 * ├── piper/piper.exe        the neural speech engine
 * └── voices/*.onnx          voice models, each with a matching .onnx.json
 * </pre>
 */
public record TellsyPaths(Path home) {

    private static final Logger log = LoggerFactory.getLogger(TellsyPaths.class);

    static final String FOLDER = ".tellsy";

    /** Folders from earlier names of the product, newest first: Sayvoy, then Sayso. */
    static final List<String> OLD_FOLDERS = List.of(".sayvoy", ".sayso");

    public static TellsyPaths fromEnvironment() {
        return resolve(System.getenv(), Path.of(System.getProperty("user.home")));
    }

    /**
     * Picks the home folder. On the first run after a rename, the folder from the previous
     * name (settings, API key, downloaded voices) is moved to ~/.tellsy so nothing is lost.
     * The newest old folder wins; any others are left untouched.
     */
    static TellsyPaths resolve(Map<String, String> env, Path userHome) {
        String override = env.get("TELLSY_HOME");
        if (override != null && !override.isBlank()) {
            return new TellsyPaths(Path.of(override));
        }
        Path home = userHome.resolve(FOLDER);
        if (Files.exists(home)) {
            return new TellsyPaths(home);
        }
        for (String oldName : OLD_FOLDERS) {
            Path old = userHome.resolve(oldName);
            if (Files.isDirectory(old)) {
                try {
                    Files.move(old, home);
                    log.info("Moved your settings and voices from {} to {}", old, home);
                } catch (IOException e) {
                    // Still usable: keep reading from the old folder rather than starting empty.
                    log.warn("Could not move {} to {}; using the old folder for now", old, home, e);
                    return new TellsyPaths(old);
                }
                break;
            }
        }
        return new TellsyPaths(home);
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
