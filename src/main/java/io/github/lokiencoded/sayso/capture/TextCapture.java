package io.github.lokiencoded.sayso.capture;

/**
 * Gets text from whatever app the user is looking at.
 * v0.1 does this through the clipboard; later versions can read the
 * screen directly (Windows UI Automation) behind this same interface.
 */
public interface TextCapture {

    /** Text the user has selected, or an empty string if nothing is selected. */
    String captureSelection();

    /** All text in the focused page or window, or an empty string if none was found. */
    String captureAll();
}
