package io.github.lokiencoded.sayso.config;

import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Reads and writes the settings file, %USERPROFILE%\.sayso\settings.properties.
 *
 * <p>It is a plain text file people can edit in Notepad. Bad values never stop Sayso:
 * they are reported in the log and the default is used instead.
 */
public final class SettingsFile {

    private static final Logger log = LoggerFactory.getLogger(SettingsFile.class);

    private static final String VOICE = "voice";
    private static final String RATE = "rate";
    private static final String GEMINI_API_KEY = "gemini.api_key";
    private static final String GEMINI_MODEL = "gemini.model";
    private static final String GEMINI_FALLBACK_MODEL = "gemini.fallback_model";

    private final Path path;

    public SettingsFile(Path path) {
        this.path = path;
    }

    public Path path() {
        return path;
    }

    /**
     * Loads settings, creating the file with defaults the first time.
     * Returns defaults (and logs why) if the file can't be read.
     */
    public Settings load() {
        Settings settings = new Settings();
        if (!Files.exists(path)) {
            save(settings);
            log.info("Created settings file: {}", path);
            return settings;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            log.warn("Could not read {}; using default settings", path, e);
            return settings;
        }
        apply(properties, settings).forEach(problem -> log.warn("Settings: {}", problem));
        return settings;
    }

    /**
     * Copies values from {@code properties} into {@code settings}.
     * Returns a description of every value that was ignored, for the log.
     */
    static List<String> apply(Properties properties, Settings settings) {
        List<String> problems = new ArrayList<>();

        settings.setVoiceName(properties.getProperty(VOICE));
        settings.setGeminiApiKey(properties.getProperty(GEMINI_API_KEY));
        settings.setGeminiModel(properties.getProperty(GEMINI_MODEL));
        if (properties.containsKey(GEMINI_FALLBACK_MODEL)) {
            settings.setGeminiFallbackModel(properties.getProperty(GEMINI_FALLBACK_MODEL));
        }

        String rate = properties.getProperty(RATE);
        if (rate != null && !rate.isBlank()) {
            try {
                settings.setRate(Integer.parseInt(rate.strip()));
            } catch (NumberFormatException e) {
                problems.add("rate=" + rate + " is not a whole number; using " + settings.rate());
            }
        }

        Map<Shortcut, Command> taken = new HashMap<>();
        for (Command command : Command.withShortcuts()) {
            String text = properties.getProperty(command.settingsKey());
            if (text != null && !text.isBlank()) {
                Shortcut.parse(text).ifPresentOrElse(
                        shortcut -> settings.setShortcut(command, shortcut),
                        () -> problems.add(command.settingsKey() + "=" + text
                                + " is not a valid shortcut; using " + settings.shortcut(command).label()));
            }
            Command clash = taken.putIfAbsent(settings.shortcut(command), command);
            if (clash != null) {
                problems.add(settings.shortcut(command).label() + " is set for both " + clash.settingsKey()
                        + " and " + command.settingsKey() + "; only " + clash.settingsKey() + " will work");
            }
        }
        return problems;
    }

    /** Writes all settings, with comments explaining each one. Never throws: failures are logged. */
    public void save(Settings settings) {
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            Files.writeString(temp, render(settings), StandardCharsets.UTF_8);
            moveIntoPlace(temp);
        } catch (IOException e) {
            log.warn("Could not save settings to {}", path, e);
        }
    }

    /** Write to a temp file, then swap it in, so a crash mid-save never leaves a half-written file. */
    private void moveIntoPlace(Path temp) throws IOException {
        try {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static String render(Settings settings) {
        StringBuilder out = new StringBuilder();
        out.append("# Sayso settings\n");
        out.append("# Edit this file while Sayso is closed, then start Sayso again.\n");
        out.append("# Voice and speed are saved here automatically when you change them with the shortcuts.\n\n");

        out.append("# Voice: a name from your .sayso\\voices folder, without .onnx. Leave blank for the first one.\n");
        out.append(VOICE).append('=').append(escape(settings.voiceName() == null ? "" : settings.voiceName()))
                .append("\n\n");

        out.append("# Speed: from ").append(Settings.MIN_RATE).append(" (slowest) to ")
                .append(Settings.MAX_RATE).append(" (fastest). 0 is normal.\n");
        out.append(RATE).append('=').append(settings.rate()).append("\n\n");

        out.append("# Ask (Ctrl+Alt+A): your spoken question and the selected text are sent to Google Gemini.\n");
        out.append("# Get a free API key at https://aistudio.google.com/apikey and paste it below.\n");
        out.append("# Keep this file private: it holds your key. (Or set the GEMINI_API_KEY environment variable.)\n");
        out.append(GEMINI_API_KEY).append('=')
                .append(escape(settings.geminiApiKey() == null ? "" : settings.geminiApiKey())).append('\n');
        out.append("# Model: ").append(Settings.DEFAULT_GEMINI_MODEL)
                .append(" is fast, for short spoken answers.\n");
        out.append(GEMINI_MODEL).append('=').append(escape(settings.geminiModel())).append('\n');
        out.append("# Fallback: used when the model above is slow or overloaded. ")
                .append(Settings.DEFAULT_GEMINI_FALLBACK_MODEL)
                .append(" always points at Google's current Flash model. Leave blank to turn off.\n");
        out.append(GEMINI_FALLBACK_MODEL).append('=')
                .append(escape(settings.geminiFallbackModel() == null ? "" : settings.geminiFallbackModel()))
                .append("\n\n");

        out.append("# Shortcuts: one or more of ctrl, alt, shift, win, then one key, joined with +.\n");
        out.append("# Keys: A-Z, 0-9, F1-F24, PERIOD, COMMA, SLASH, SEMICOLON, SPACE, ENTER, HOME, END, PAGE_UP, ...\n");
        out.append("# Example: shortcut.stop=ctrl+shift+F12\n");
        for (Command command : Command.withShortcuts()) {
            out.append("# ").append(command.description()).append('\n');
            out.append(command.settingsKey()).append('=')
                    .append(settings.shortcut(command).toSettingsText()).append('\n');
        }
        return out.toString();
    }

    /** Properties files treat backslashes and leading spaces specially. */
    private static String escape(String value) {
        return value.replace("\\", "\\\\");
    }
}
