package io.github.lokiencoded.sayvoy.capture;

/**
 * Gets text from whatever app the user is looking at.
 * v0.2 does this through the clipboard; later versions can read the
 * screen directly (Windows UI Automation) behind this same interface.
 */
public interface TextCapture {

    /** Text the user has selected, or an empty string if nothing is selected. */
    String captureSelection();

    /** Everything in the focused page or window, as plain text and (when available) HTML. */
    CapturedText captureAll();
}
