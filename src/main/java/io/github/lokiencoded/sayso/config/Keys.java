package io.github.lokiencoded.sayso.config;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Key names people can write in the settings file, mapped to JNativeHook key codes.
 *
 * <p>Built from JNativeHook's own VC_* constants, so every key it knows is available:
 * VC_R becomes "R", VC_F5 "F5", VC_PAGE_UP "PAGE_UP". A few symbols are accepted too.
 */
public final class Keys {

    /** Modifier keys can't be the main key of a shortcut. */
    private static final Set<String> MODIFIER_KEYS = Set.of(
            "CONTROL", "ALT", "SHIFT", "META", "CONTEXT_MENU", "UNDEFINED");

    private static final Map<String, String> SYMBOLS = Map.of(
            ".", "PERIOD", ",", "COMMA", "/", "SLASH", ";", "SEMICOLON", "'", "QUOTE",
            "-", "MINUS", "=", "EQUALS", "`", "BACKQUOTE", "[", "OPEN_BRACKET", "]", "CLOSE_BRACKET");

    private static final Map<String, Integer> CODES = new HashMap<>();
    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        for (Field field : NativeKeyEvent.class.getFields()) {
            boolean isKeyConstant = field.getName().startsWith("VC_")
                    && Modifier.isStatic(field.getModifiers())
                    && field.getType() == int.class;
            if (!isKeyConstant) {
                continue;
            }
            String name = field.getName().substring(3);
            // Sun keyboard keys share codes with standard keys; skip them so names stay familiar.
            if (MODIFIER_KEYS.contains(name) || name.startsWith("SUN_")) {
                continue;
            }
            try {
                int code = field.getInt(null);
                CODES.put(name, code);
                NAMES.putIfAbsent(code, name);
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private Keys() {
    }

    /** The key code for a name such as "R", "f5", "page_up", "period" or ".". */
    public static Optional<Integer> parse(String name) {
        String trimmed = name.strip();
        String canonical = SYMBOLS.getOrDefault(trimmed, trimmed)
                .toUpperCase(Locale.ROOT)
                .replace(' ', '_')
                .replace('-', '_');
        return Optional.ofNullable(CODES.get(canonical));
    }

    /** The name we write back to the file for a key code, e.g. "R" or "PERIOD". */
    public static String name(int code) {
        return NAMES.getOrDefault(code, "KEY_" + code);
    }

    /** A friendlier label for messages: "." instead of "PERIOD". */
    public static String label(int code) {
        String name = name(code);
        for (Map.Entry<String, String> symbol : SYMBOLS.entrySet()) {
            if (symbol.getValue().equals(name)) {
                return symbol.getKey();
            }
        }
        return name;
    }
}
