package io.github.lokiencoded.sayso;

import io.github.lokiencoded.sayso.ask.Assistant;
import io.github.lokiencoded.sayso.ask.Cues;
import io.github.lokiencoded.sayso.ask.GeminiAssistant;
import io.github.lokiencoded.sayso.ask.MicrophoneRecorder;
import io.github.lokiencoded.sayso.capture.ClipboardTextCapture;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.config.SettingsFile;
import io.github.lokiencoded.sayso.core.AskController;
import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.CommandLoop;
import io.github.lokiencoded.sayso.core.SaysoPaths;
import io.github.lokiencoded.sayso.core.Settings;
import io.github.lokiencoded.sayso.hotkey.HotkeyListener;
import io.github.lokiencoded.sayso.hotkey.KeyBindings;
import io.github.lokiencoded.sayso.speech.SpeechEngine;
import io.github.lokiencoded.sayso.speech.SpeechEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Entry point. Its only job is wiring: it creates each part and connects them.
 * The parts themselves never create each other, which keeps them swappable
 * (for example, a better SpeechEngine in v0.2) and testable.
 */
public final class SaysoApp {

    private static final Logger log = LoggerFactory.getLogger(SaysoApp.class);

    private SaysoApp() {
    }

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.err.println("Sayso runs on Windows only for now.");
            System.exit(1);
        }

        SaysoPaths paths = SaysoPaths.fromEnvironment();
        SettingsFile settingsFile = new SettingsFile(paths.settingsFile());
        Settings settings = settingsFile.load();

        BlockingQueue<Command> commands = new LinkedBlockingQueue<>();
        HotkeyListener hotkeys = new HotkeyListener(new KeyBindings(settings.shortcuts()), commands::offer);
        TextCapture capture = new ClipboardTextCapture(hotkeys::modifiersHeld);
        SpeechEngine speech = SpeechEngines.best(paths);
        AskController ask = new AskController(new MicrophoneRecorder(), assistant(settings), capture,
                new Cues(), Executors.newSingleThreadExecutor(SaysoApp::daemon),
                text -> speech.speak(text, settings.rate(), settings.voiceName()));

        hotkeys.start();
        printShortcuts(settings, settingsFile);

        // Blocks here until the user presses the quit shortcut.
        new CommandLoop(commands, capture, speech, settings, settingsFile::save, ask).run();

        speech.stop();
        hotkeys.stop();
        log.info("Bye.");
        // The keyboard hook and AWT keep background threads alive, so exit explicitly.
        System.exit(0);
    }

    /** Gemini, when a key is set (the GEMINI_API_KEY environment variable wins over the settings file). */
    private static Optional<Assistant> assistant(Settings settings) {
        String envKey = System.getenv("GEMINI_API_KEY");
        String key = envKey != null && !envKey.isBlank() ? envKey.strip() : settings.geminiApiKey();
        if (key == null) {
            log.info("Ask is off: no Gemini API key. Add gemini.api_key to the settings file to turn it on.");
            return Optional.empty();
        }
        log.info("Ask uses Gemini model {}", settings.geminiModel());
        return Optional.of(new GeminiAssistant(key, settings.geminiModel()));
    }

    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "sayso-ask");
        thread.setDaemon(true);
        return thread;
    }

    private static void printShortcuts(Settings settings, SettingsFile settingsFile) {
        StringBuilder out = new StringBuilder("\nSayso is running. Shortcuts:\n");
        for (Command command : Command.withShortcuts()) {
            out.append(String.format("  %-16s %s%n", settings.shortcut(command).label(), command.description()));
        }
        out.append("\nChange voice, speed or shortcuts in ").append(settingsFile.path()).append('\n');
        System.out.println(out);
    }
}
