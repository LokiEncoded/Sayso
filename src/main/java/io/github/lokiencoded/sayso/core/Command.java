package io.github.lokiencoded.sayso.core;

/** Everything the user can ask Sayso to do. Hotkeys produce these; the CommandLoop acts on them. */
public enum Command {
    READ_SELECTION("read the selected text (press again to stop)"),
    READ_PAGE("read the whole page or window (press again to stop)"),
    STOP("stop reading"),
    FASTER("faster"),
    SLOWER("slower"),
    NEXT_VOICE("next voice"),
    QUIT("quit");

    private final String description;

    Command(String description) {
        this.description = description;
    }

    public String description() {
        return description;
    }

    /** The key used for this command's shortcut in the settings file, e.g. "shortcut.read_selection". */
    public String settingsKey() {
        return "shortcut." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
