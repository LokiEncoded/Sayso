package io.github.lokiencoded.tellsy.capture;

/**
 * What a copy produced. Browsers and many apps put the same content on the clipboard
 * twice: as plain text, and as HTML that still shows the page's structure.
 *
 * @param text plain text; empty if nothing was copied
 * @param html the HTML version, or null if the app didn't provide one
 */
public record CapturedText(String text, String html) {

    public static final CapturedText EMPTY = new CapturedText("", null);

    public boolean hasHtml() {
        return html != null && !html.isBlank();
    }
}
