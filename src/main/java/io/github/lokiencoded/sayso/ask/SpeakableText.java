package io.github.lokiencoded.sayso.ask;

import java.util.regex.Pattern;

/**
 * Removes formatting that sounds wrong when read aloud ("asterisk asterisk important")
 * from AI answers, in case the model ignores the plain-text instruction.
 */
public final class SpeakableText {

    private static final Pattern CODE_FENCE = Pattern.compile("(?m)^```.*$");
    private static final Pattern HEADING = Pattern.compile("(?m)^\\s{0,3}#{1,6}\\s*");
    private static final Pattern BULLET = Pattern.compile("(?m)^\\s*(?:[-*+•]|\\d+[.)])\\s+");
    private static final Pattern EMPHASIS = Pattern.compile("(\\*\\*|__|\\*|_|`)(?=\\S)(.+?)(?<=\\S)\\1");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\([^)]+\\)");

    private SpeakableText() {
    }

    public static String clean(String text) {
        if (text == null) {
            return "";
        }
        String out = CODE_FENCE.matcher(text).replaceAll("");
        out = LINK.matcher(out).replaceAll("$1");
        out = HEADING.matcher(out).replaceAll("");
        out = BULLET.matcher(out).replaceAll("");
        out = EMPHASIS.matcher(out).replaceAll("$2");
        return out.replaceAll("[ \\t]+", " ").replaceAll("\\n{3,}", "\n\n").strip();
    }
}
