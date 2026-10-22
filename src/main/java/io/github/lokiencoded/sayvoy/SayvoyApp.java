package io.github.lokiencoded.sayvoy;

import io.github.lokiencoded.sayvoy.ask.Assistant;
import io.github.lokiencoded.sayvoy.ask.Cues;
import io.github.lokiencoded.sayvoy.ask.GeminiAssistant;
import io.github.lokiencoded.sayvoy.ask.MicrophoneRecorder;
import io.github.lokiencoded.sayvoy.capture.ClipboardTextCapture;
import io.github.lokiencoded.sayvoy.config.SettingsFile;
import io.github.lokiencoded.sayvoy.core.VoiceController;
import io.github.lokiencoded.sayvoy.core.Command;
import io.github.lokiencoded.sayvoy.core.CommandLoop;
import io.github.lokiencoded.sayvoy.core.RecentText;
import io.github.lokiencoded.sayvoy.core.SayvoyPaths;
import io.github.lokiencoded.sayvoy.core.Settings;
import io.github.lokiencoded.sayvoy.hotkey.HotkeyListener;
import io.github.lokiencoded.sayvoy.hotkey.KeyBindings;
import io.github.lokiencoded.sayvoy.speech.SpeechEngine;
import io.github.lokiencoded.sayvoy.speech.SpeechEngines;
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
public final class SayvoyApp {

    private static final Logger log = LoggerFactory.getLogger(SayvoyApp.class);

    private SayvoyApp() {
    }

    public static void main(String[] args) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) {
            System.err.println("Sayvoy runs on Windows only for now.");
            System.exit(1);
        }

        SayvoyPaths paths = SayvoyPaths.fromEnvironment();
        SettingsFile settingsFile = new SettingsFile(paths.settingsFile());
        Settings settings = settingsFile.load();

        BlockingQueue<Command> commands = new LinkedBlockingQueue<>();
        HotkeyListener hotkeys = new HotkeyListener(new KeyBindings(settings.shortcuts()), commands::offer);
        ClipboardTextCapture clipboard = new ClipboardTextCapture(hotkeys::modifiersHeld);
        RecentText recent = new RecentText();
        SpeechEngine speech = SpeechEngines.best(paths);
        VoiceController voice = new VoiceController(new MicrophoneRecorder(), assistant(settings), clipboard,
                clipboard, recent,
                // A cached pool, not a single thread: a new question must never queue behind a slow one.
                new Cues(), Executors.newCachedThreadPool(SayvoyApp::daemon),
                text -> speech.speak(text, settings.rate(), settings.voiceName()));

        hotkeys.start();
        printShortcuts(settings, settingsFile);

        // Blocks here until the user presses the quit shortcut.
        new CommandLoop(commands, clipboard, speech, settings, settingsFile::save, voice, recent).run();

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
            log.info("Ask and Reply are off: no Gemini API key. Add gemini.api_key to the settings file to turn them on.");
            return Optional.empty();
        }
        log.info("Ask and Reply use Gemini model {} (fallback: {})", settings.geminiModel(),
                settings.geminiFallbackModel() == null ? "none" : settings.geminiFallbackModel());
        return Optional.of(new GeminiAssistant(key, settings.geminiModel(), settings.geminiFallbackModel()));
    }

    private static Thread daemon(Runnable task) {
        Thread thread = new Thread(task, "sayvoy-ask");
        thread.setDaemon(true);
        return thread;
    }

    private static void printShortcuts(Settings settings, SettingsFile settingsFile) {
        StringBuilder out = new StringBuilder("\nSayvoy is running. Shortcuts:\n");
        for (Command command : Command.withShortcuts()) {
            out.append(String.format("  %-16s %s%n", settings.shortcut(command).label(), command.description()));
        }
        out.append("\nChange voice, speed or shortcuts in ").append(settingsFile.path()).append('\n');
        System.out.println(out);
    }
}
