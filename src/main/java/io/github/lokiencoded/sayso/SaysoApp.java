package io.github.lokiencoded.sayso;

import io.github.lokiencoded.sayso.capture.ClipboardTextCapture;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.CommandLoop;
import io.github.lokiencoded.sayso.core.SaysoPaths;
import io.github.lokiencoded.sayso.core.Settings;
import io.github.lokiencoded.sayso.hotkey.HotkeyListener;
import io.github.lokiencoded.sayso.speech.SpeechEngine;
import io.github.lokiencoded.sayso.speech.SpeechEngines;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
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

        BlockingQueue<Command> commands = new LinkedBlockingQueue<>();
        HotkeyListener hotkeys = new HotkeyListener(commands::offer);
        TextCapture capture = new ClipboardTextCapture(hotkeys::modifiersHeld);
        SpeechEngine speech = SpeechEngines.best(SaysoPaths.fromEnvironment());
        Settings settings = new Settings();

        hotkeys.start();
        printShortcuts();

        // Blocks here until the user presses the quit shortcut.
        new CommandLoop(commands, capture, speech, settings).run();

        speech.stop();
        hotkeys.stop();
        log.info("Bye.");
        // The keyboard hook and AWT keep background threads alive, so exit explicitly.
        System.exit(0);
    }

    private static void printShortcuts() {
        System.out.println("""

                Sayso v0.2 is running. Hold Ctrl+Alt, then press:
                  R   read the selected text
                  P   read the whole page or window
                  S   stop reading
                  .   faster
                  ,   slower
                  V   next voice
                  Q   quit
                """);
    }
}
