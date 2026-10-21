package io.github.lokiencoded.sayso.core;

import java.nio.file.Path;

/**
 * Where Sayso keeps its files on the user's computer.
 * Defaults to a ".sayso" folder in the user's home; the SAYSO_HOME
 * environment variable overrides it (handy for testing).
 *
 * <pre>
 * ~/.sayso/
 * ├── piper/piper.exe        the neural speech engine
 * └── voices/*.onnx          voice models, each with a matching .onnx.json
 * </pre>
 */
public record SaysoPaths(Path home) {

    public static SaysoPaths fromEnvironment() {
        String override = System.getenv("SAYSO_HOME");
        if (override != null && !override.isBlank()) {
            return new SaysoPaths(Path.of(override));
        }
        return new SaysoPaths(Path.of(System.getProperty("user.home"), ".sayso"));
    }

    public Path piperExecutable() {
        return home.resolve("piper").resolve("piper.exe");
    }

    public Path voicesDir() {
        return home.resolve("voices");
    }
}
