package io.github.lokiencoded.sayvoy.speech;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/** Builds the small PowerShell scripts that drive Windows' speech engine. Pure functions, easy to test. */
final class SapiScripts {

    private SapiScripts() {
    }

    /** A script that reads text from stdin and speaks it at {@code rate} with {@code voiceName}. */
    static String speak(int rate, String voiceName) {
        StringBuilder script = new StringBuilder()
                .append("$ErrorActionPreference = 'Stop'\n")
                .append("[Console]::InputEncoding = [System.Text.Encoding]::UTF8\n")
                .append("Add-Type -AssemblyName System.Speech\n")
                .append("$s = New-Object System.Speech.Synthesis.SpeechSynthesizer\n")
                .append("$s.Rate = ").append(clampRate(rate)).append('\n');
        if (voiceName != null && !voiceName.isBlank()) {
            script.append("$s.SelectVoice('").append(escape(voiceName)).append("')\n");
        }
        script.append("$s.Speak([Console]::In.ReadToEnd())\n");
        return script.toString();
    }

    /** A script that prints the name of each enabled voice, one per line. */
    static String listVoices() {
        return """
                [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
                Add-Type -AssemblyName System.Speech
                $s = New-Object System.Speech.Synthesis.SpeechSynthesizer
                $s.GetInstalledVoices() | Where-Object { $_.Enabled } | ForEach-Object { $_.VoiceInfo.Name }
                """;
    }

    /** PowerShell's -EncodedCommand takes Base64 of UTF-16LE text; this avoids all quoting problems. */
    static String encode(String script) {
        return Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
    }

    /** Inside single quotes, PowerShell escapes a quote by doubling it. */
    static String escape(String value) {
        return value.replace("'", "''");
    }

    static int clampRate(int rate) {
        return Math.max(-10, Math.min(10, rate));
    }
}
