package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.ask.AskException;
import io.github.lokiencoded.sayso.ask.Assistant;
import io.github.lokiencoded.sayso.ask.Cues;
import io.github.lokiencoded.sayso.ask.Recorder;
import io.github.lokiencoded.sayso.ask.Recorder.Recording;
import io.github.lokiencoded.sayso.ask.SpeakableText;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.extract.PageCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Hold to talk: press the Ask shortcut, speak, let go. Sayso then copies the selected text,
 * sends both to the assistant, and speaks the answer.
 *
 * <p>The assistant can take several seconds, so it runs on its own thread: the command loop
 * (and with it Stop and every other shortcut) never waits for the network.
 */
public final class AskController {

    private static final Logger log = LoggerFactory.getLogger(AskController.class);

    /** Shorter than this is almost certainly an accidental tap, not a question. */
    static final long MIN_QUESTION_MILLIS = 400;

    private final Recorder recorder;
    private final Optional<Assistant> assistant;
    private final TextCapture capture;
    private final Cues cues;
    private final Executor background;
    private final Consumer<String> say;

    /** Bumped by every new question and by Stop, so a late answer to an old question is dropped. */
    private final AtomicInteger generation = new AtomicInteger();

    /**
     * @param assistant empty when no API key is set up
     * @param say       speaks text with the user's voice and speed
     */
    public AskController(Recorder recorder, Optional<Assistant> assistant, TextCapture capture,
                         Cues cues, Executor background, Consumer<String> say) {
        this.recorder = recorder;
        this.assistant = assistant;
        this.capture = capture;
        this.cues = cues;
        this.background = background;
        this.say = say;
    }

    /** The Ask shortcut went down: start listening. */
    void startListening() {
        if (assistant.isEmpty()) {
            say.accept("Ask needs a Gemini API key. Add it to your Sayso settings file, then restart Sayso.");
            return;
        }
        cancel();
        try {
            recorder.start();
            cues.listening();
            log.info("Listening...");
        } catch (AskException e) {
            say.accept(e.getMessage());
        }
    }

    /** The Ask shortcut was let go: stop listening and send the question. */
    void finishListening() {
        if (!recorder.isRecording()) {
            return;
        }
        Recording question = recorder.stop();
        if (question.durationMillis() < MIN_QUESTION_MILLIS) {
            say.accept("Hold the shortcut while you speak your question.");
            return;
        }
        cues.thinking();
        // Copy the selection now: the user has just let go of the shortcut keys.
        String selection = PageCleaner.tidy(capture.captureSelection());
        int ticket = generation.incrementAndGet();
        log.info("Asking about {} characters of selected text", selection.length());
        background.execute(() -> answer(ticket, question, selection));
    }

    private void answer(int ticket, Recording question, String selection) {
        String reply;
        try {
            reply = SpeakableText.clean(assistant.orElseThrow().answer(question.wav(), selection));
        } catch (AskException e) {
            log.warn("Ask failed: {}", e.getMessage(), e.getCause());
            reply = e.getMessage();
        } catch (RuntimeException e) {
            log.error("Ask failed unexpectedly", e);
            reply = "Something went wrong while asking. Please try again.";
        }
        if (ticket == generation.get()) {
            say.accept(reply);
        } else {
            log.info("Dropped an answer to a question that was cancelled");
        }
    }

    /** Drops any answer still on its way, and stops listening. Called by Stop and by new reads. */
    void cancel() {
        generation.incrementAndGet();
        if (recorder.isRecording()) {
            recorder.stop();
        }
    }
}
