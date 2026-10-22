package io.github.lokiencoded.sayvoy.capture;

/** Puts text into whatever app the user is typing in, as if they had typed it. */
public interface TextOutput {

    /** Inserts {@code text} at the cursor of the focused app. */
    void paste(String text);
}
