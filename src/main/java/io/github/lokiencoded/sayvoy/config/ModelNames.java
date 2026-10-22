package io.github.lokiencoded.sayvoy.config;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Tidies up a Gemini model name typed into the settings file.
 *
 * <p>Forgives the common slips: pasting the whole line twice
 * ("gemini.model=gemini-3.5-flash-lite"), a "models/" prefix copied from Google's docs,
 * quotes or spaces. Anything that still isn't a plain model name is rejected.
 */
public final class ModelNames {

    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

    private ModelNames() {
    }

    public static Optional<String> clean(String text) {
        if (text == null) {
            return Optional.empty();
        }
        String name = text.strip();
        int equals = name.lastIndexOf('=');
        if (equals >= 0) {
            name = name.substring(equals + 1).strip();   // "gemini.model=gemini-x" pasted after the =
        }
        name = name.replace("\"", "").replace("'", "").strip();
        if (name.startsWith("models/")) {
            name = name.substring("models/".length());
        }
        return VALID.matcher(name).matches() ? Optional.of(name) : Optional.empty();
    }
}
