package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.config.Shortcut;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The user's preferences: voice, speed and shortcuts.
 * Loaded from and saved to the settings file by {@link io.github.lokiencoded.sayso.config.SettingsFile}.
 */
public final class Settings {

    public static final int MIN_RATE = -10;
    public static final int MAX_RATE = 10;
    public static final int DEFAULT_RATE = 1;

    /** Defaults chosen to avoid common clashes (Ctrl+Alt+S is HP System Information and IntelliJ Settings). */
    public static final Map<Command, String> DEFAULT_SHORTCUTS = defaultShortcuts();

    private int rate = DEFAULT_RATE;
    private String voiceName;   // null means "the first installed voice"
    private final Map<Command, Shortcut> shortcuts = new EnumMap<>(Command.class);

    public Settings() {
        DEFAULT_SHORTCUTS.forEach((command, text) ->
                shortcuts.put(command, Shortcut.parse(text).orElseThrow()));
    }

    public int rate() {
        return rate;
    }

    public void setRate(int rate) {
        this.rate = Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
    }

    /** Changes speed by {@code step}, staying within MIN_RATE..MAX_RATE. */
    public void changeRate(int step) {
        setRate(rate + step);
    }

    public String voiceName() {
        return voiceName;
    }

    public void setVoiceName(String voiceName) {
        this.voiceName = voiceName == null || voiceName.isBlank() ? null : voiceName.strip();
    }

    /**
     * The voice after the current one in {@code voices}, wrapping at the end.
     * If the current voice isn't installed (or none is set), this is the first voice.
     */
    public Optional<String> nextVoice(List<String> voices) {
        if (voices.isEmpty()) {
            return Optional.empty();
        }
        // -1 when unset or not installed. (indexOf(null) throws on immutable lists, hence the check.)
        int current = voiceName == null ? -1 : voices.indexOf(voiceName);
        return Optional.of(voices.get((current + 1) % voices.size()));
    }

    public Shortcut shortcut(Command command) {
        return shortcuts.get(command);
    }

    public void setShortcut(Command command, Shortcut shortcut) {
        shortcuts.put(command, shortcut);
    }

    public Map<Command, Shortcut> shortcuts() {
        return Map.copyOf(shortcuts);
    }

    private static Map<Command, String> defaultShortcuts() {
        Map<Command, String> defaults = new EnumMap<>(Command.class);
        defaults.put(Command.READ_SELECTION, "ctrl+alt+R");
        defaults.put(Command.READ_PAGE, "ctrl+alt+P");
        defaults.put(Command.STOP, "ctrl+alt+X");
        defaults.put(Command.FASTER, "ctrl+alt+PERIOD");
        defaults.put(Command.SLOWER, "ctrl+alt+COMMA");
        defaults.put(Command.NEXT_VOICE, "ctrl+alt+V");
        defaults.put(Command.QUIT, "ctrl+alt+Q");
        return Map.copyOf(defaults);
    }
}
