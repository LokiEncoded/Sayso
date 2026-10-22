package io.github.lokiencoded.sayso.hotkey;

import io.github.lokiencoded.sayso.config.Modifier;
import io.github.lokiencoded.sayso.config.Shortcut;
import io.github.lokiencoded.sayso.core.Command;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Which shortcut triggers which command. Built from the user's settings. */
public final class KeyBindings {

    private final Map<Command, Shortcut> shortcuts;

    public KeyBindings(Map<Command, Shortcut> shortcuts) {
        this.shortcuts = new EnumMap<>(shortcuts);
    }

    /** The command whose shortcut is exactly this key plus these held modifiers, if any. */
    public Optional<Command> lookup(int keyCode, Set<Modifier> held) {
        return shortcuts.entrySet().stream()
                .filter(entry -> entry.getValue().matches(keyCode, held))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    public Map<Command, Shortcut> shortcuts() {
        return Map.copyOf(shortcuts);
    }
}
