package io.github.lokiencoded.tellsy.config;

import com.github.kwhat.jnativehook.NativeInputEvent;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** A key held together with the main key of a shortcut. */
public enum Modifier {
    CTRL(NativeInputEvent.CTRL_MASK),
    ALT(NativeInputEvent.ALT_MASK),
    SHIFT(NativeInputEvent.SHIFT_MASK),
    WIN(NativeInputEvent.META_MASK);

    private final int mask;

    Modifier(int mask) {
        this.mask = mask;
    }

    /** Which of our modifiers are set in a JNativeHook modifier mask (lock keys and mouse buttons ignored). */
    public static Set<Modifier> fromMask(int modifierMask) {
        Set<Modifier> held = EnumSet.noneOf(Modifier.class);
        for (Modifier modifier : values()) {
            if ((modifierMask & modifier.mask) != 0) {
                held.add(modifier);
            }
        }
        return held;
    }

    /** "ctrl", "control", "alt", "shift", "win", "windows" or "meta", in any case. */
    public static Optional<Modifier> parse(String name) {
        return switch (name.strip().toLowerCase(Locale.ROOT)) {
            case "ctrl", "control" -> Optional.of(CTRL);
            case "alt" -> Optional.of(ALT);
            case "shift" -> Optional.of(SHIFT);
            case "win", "windows", "meta" -> Optional.of(WIN);
            default -> Optional.empty();
        };
    }

    public String displayName() {
        return switch (this) {
            case CTRL -> "Ctrl";
            case ALT -> "Alt";
            case SHIFT -> "Shift";
            case WIN -> "Win";
        };
    }
}
