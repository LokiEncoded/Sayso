package io.github.lokiencoded.tellsy.config;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * A keyboard shortcut: one or more modifiers plus one main key, such as Ctrl+Alt+R.
 * Written in the settings file as "ctrl+alt+R".
 */
public record Shortcut(Set<Modifier> modifiers, int keyCode) {

    public Shortcut {
        modifiers = modifiers.isEmpty() ? EnumSet.noneOf(Modifier.class) : EnumSet.copyOf(modifiers);
    }

    /**
     * Parses text like "ctrl+alt+R", "Ctrl + Shift + F5" or "ctrl+alt+.".
     * Needs at least one modifier, so a plain letter can never become a global shortcut.
     */
    public static Optional<Shortcut> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.split("\\+");
        if (parts.length < 2) {
            return Optional.empty();
        }
        Set<Modifier> modifiers = EnumSet.noneOf(Modifier.class);
        for (int i = 0; i < parts.length - 1; i++) {
            Optional<Modifier> modifier = Modifier.parse(parts[i]);
            if (modifier.isEmpty()) {
                return Optional.empty();
            }
            modifiers.add(modifier.get());
        }
        return Keys.parse(parts[parts.length - 1]).map(code -> new Shortcut(modifiers, code));
    }

    /** True if this exact combination is held: same modifiers (no extra ones) and the same key. */
    public boolean matches(int pressedKeyCode, Set<Modifier> held) {
        return keyCode == pressedKeyCode && modifiers.equals(held);
    }

    /** The form we write to the settings file: "ctrl+alt+R". */
    public String toSettingsText() {
        StringJoiner text = new StringJoiner("+");
        for (Modifier modifier : modifiers) {
            text.add(modifier.name().toLowerCase(Locale.ROOT));
        }
        return text.add(Keys.name(keyCode)).toString();
    }

    /** The form we show people: "Ctrl+Alt+R". */
    public String label() {
        StringJoiner text = new StringJoiner("+");
        for (Modifier modifier : modifiers) {
            text.add(modifier.displayName());
        }
        return text.add(Keys.label(keyCode)).toString();
    }
}
