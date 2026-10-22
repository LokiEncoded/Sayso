package io.github.lokiencoded.sayvoy.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Speaks with the voices built into Windows. Java has no good speech library of
 * its own, so we ask PowerShell to use Windows' System.Speech engine. Each
 * utterance runs in its own short-lived PowerShell process; stopping speech
 * means ending that process.
 */
public final class WindowsSapiSpeechEngine implements SpeechEngine {

    private static final Logger log = LoggerFactory.getLogger(WindowsSapiSpeechEngine.class);

    private Process current;
    private List<String> voices;

    @Override
    public synchronized void speak(String text, int rate, String voiceName) {
        stop();
        if (text == null || text.isBlank()) {
            return;
        }
        try {
            ProcessBuilder builder = new ProcessBuilder(powershell(SapiScripts.speak(rate, voiceName)));
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);   // errors show in our console
            current = builder.start();

            // The text goes in through stdin, so length and special characters never matter.
            try (Writer stdin = new OutputStreamWriter(current.getOutputStream(), StandardCharsets.UTF_8)) {
                stdin.write(text);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not start Windows speech", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (current != null && current.isAlive()) {
            current.descendants().forEach(ProcessHandle::destroyForcibly);
            current.destroyForcibly();
        }
        current = null;
    }

    @Override
    public synchronized boolean isSpeaking() {
        return current != null && current.isAlive();
    }

    @Override
    public synchronized List<String> voices() {
        if (voices == null) {
            voices = loadVoices();
            log.info("Installed voices: {}", voices);
        }
        return voices;
    }

    private static List<String> loadVoices() {
        try {
            Process process = new ProcessBuilder(powershell(SapiScripts.listVoices()))
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            process.getOutputStream().close();
            List<String> names;
            try (BufferedReader out = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                names = out.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
            }
            if (!process.waitFor(15, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            return names;
        } catch (IOException e) {
            log.warn("Could not list voices", e);
            return List.of();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
    }

    private static List<String> powershell(String script) {
        return List.of("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-EncodedCommand", SapiScripts.encode(script));
    }
}
