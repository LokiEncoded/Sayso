package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.capture.CapturedText;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.extract.PageCleaner;
import io.github.lokiencoded.sayso.speech.SpeechEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Takes commands off the queue one at a time and carries them out.
 * Everything runs on this one thread, so the parts never have to worry
 * about two commands running at once.
 */
public final class CommandLoop implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(CommandLoop.class);

    private final BlockingQueue<Command> commands;
    private final TextCapture capture;
    private final SpeechEngine speech;
    private final Settings settings;
    private final Consumer<Settings> saveSettings;

    /**
     * @param saveSettings called whenever the user changes voice or speed, so the
     *                     change survives a restart (SettingsFile::save in the app)
     */
    public CommandLoop(BlockingQueue<Command> commands, TextCapture capture,
                       SpeechEngine speech, Settings settings, Consumer<Settings> saveSettings) {
        this.commands = commands;
        this.capture = capture;
        this.speech = speech;
        this.settings = settings;
        this.saveSettings = saveSettings;
    }

    @Override
    public void run() {
        while (true) {
            Command command;
            try {
                command = commands.take();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                if (!handle(command)) {
                    return;
                }
            } catch (RuntimeException e) {
                // One failed command must never crash the app.
                log.error("Could not handle {}", command, e);
            }
        }
    }

    /** Carries out one command. Returns false when the app should quit. */
    boolean handle(Command command) {
        log.debug("Handling {}", command);
        switch (command) {
            // The read shortcuts toggle: pressed while speaking, they stop instead.
            case READ_SELECTION -> {
                if (!stopIfSpeaking()) {
                    speakOr(PageCleaner.tidy(capture.captureSelection()), "Nothing is selected.");
                }
            }
            case READ_PAGE -> {
                if (!stopIfSpeaking()) {
                    speakOr(readablePage(capture.captureAll()), "I couldn't find any text here.");
                }
            }
            case STOP -> speech.stop();
            case FASTER -> changeRate(+1);
            case SLOWER -> changeRate(-1);
            case NEXT_VOICE -> nextVoice();
            case QUIT -> {
                speech.stop();
                return false;
            }
        }
        return true;
    }

    /**
     * Just the article, when we can find it: the HTML version keeps the page's structure,
     * so menus, sidebars and footers can be dropped. Apps that copy only plain text get
     * a light cleanup instead.
     */
    static String readablePage(CapturedText page) {
        if (page.hasHtml()) {
            String article = PageCleaner.fromHtml(page.html());
            if (!article.isBlank()) {
                return article;
            }
        }
        return PageCleaner.tidy(page.text());
    }

    /** Stops speech if any is playing. Returns true if it did. */
    private boolean stopIfSpeaking() {
        if (speech.isSpeaking()) {
            speech.stop();
            return true;
        }
        return false;
    }

    private void speakOr(String text, String fallback) {
        say(text == null || text.isBlank() ? fallback : text);
    }

    private void changeRate(int step) {
        settings.changeRate(step);
        saveSettings.accept(settings);
        say("Speed " + settings.rate());
    }

    private void nextVoice() {
        List<String> voices = speech.voices();
        if (voices.isEmpty()) {
            say("No other voices are installed.");
            return;
        }
        settings.nextVoice(voices).ifPresent(settings::setVoiceName);
        saveSettings.accept(settings);
        log.info("Voice: {}", settings.voiceName());
        say("This is my new voice.");
    }

    private void say(String text) {
        speech.speak(text, settings.rate(), settings.voiceName());
    }
}
