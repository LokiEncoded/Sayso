package io.github.lokiencoded.sayso.core;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Everything the user can ask Sayso to do. Hotkeys produce these; the CommandLoop acts on them. */
public enum Command {
    READ_SELECTION("read the selected text (press again to stop)"),
    READ_PAGE("read the whole page or window (press again to stop)"),
    ASK("ask about the selected text: hold, speak your question, let go"),
    STOP("stop reading"),
    FASTER("faster"),
    SLOWER("slower"),
    NEXT_VOICE("next voice"),
    QUIT("quit"),

    /** Sent automatically when the Ask shortcut is let go. Not a shortcut of its own. */
    ASK_RELEASED(null);

    private final String description;

    Command(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** Commands the user triggers with their own shortcut (everything except internal ones). */
    public static List<Command> withShortcuts() {
        return Arrays.stream(values()).filter(command -> command.description != null).toList();
    }

    /** The key used for this command's shortcut in the settings file, e.g. "shortcut.read_selection". */
    public String settingsKey() {
        return "shortcut." + name().toLowerCase(Locale.ROOT);
    }
}
