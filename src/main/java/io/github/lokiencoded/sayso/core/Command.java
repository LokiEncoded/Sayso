package io.github.lokiencoded.sayso.core;

/** Everything the user can ask Sayso to do. Hotkeys produce these; the CommandLoop acts on them. */
public enum Command {
    READ_SELECTION,
    READ_PAGE,
    STOP,
    FASTER,
    SLOWER,
    NEXT_VOICE,
    QUIT
}
