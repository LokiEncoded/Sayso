package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.ask.AskException;
import io.github.lokiencoded.sayso.ask.Assistant;
import io.github.lokiencoded.sayso.ask.Cues;
import io.github.lokiencoded.sayso.ask.Recorder;
import io.github.lokiencoded.sayso.ask.Recorder.Recording;
import io.github.lokiencoded.sayso.ask.SpeakableText;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.capture.TextOutput;
import io.github.lokiencoded.sayso.extract.PageCleaner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * The two hold-to-talk features, which share everything up to the moment Gemini replies:
 *
 * <ul>
 *   <li><b>Ask</b>: speak a question about the selected text; hear the answer.</li>
 *   <li><b>Reply</b>: speak what you want to write; it's cleaned up and typed into the text box
 *       you're in, using what Sayso last read to you as context.</li>
 * </ul>
 *
 * <p>Gemini can take a few seconds, so it runs on its own thread: the command loop (and with it
 * Stop and every other shortcut) never waits for the network.
 */
public final class VoiceController {

    private static final Logger log = LoggerFactory.getLogger(VoiceController.class);

    /** Shorter than this is almost certainly an accidental tap, not speech. */
    static final long MIN_SPEECH_MILLIS = 400;

    /** What the current recording is for. */
    enum Mode { ASK, REPLY }

    private final Recorder recorder;
    private final Optional<Assistant> assistant;
    private final TextCapture capture;
    private final TextOutput output;
    private final RecentText recent;
    private final Cues cues;
    private final Executor background;
    private final Consumer<String> say;

    /** Bumped by every new recording and by Stop, so a late result for an old one is dropped. */
    private final AtomicInteger generation = new AtomicInteger();
    private Mode mode = Mode.ASK;

    /**
     * @param assistant empty when no API key is set up
     * @param recent    what Sayso last read aloud, used as context for replies
     * @param say       speaks text with the user's voice and speed
     */
    public VoiceController(Recorder recorder, Optional<Assistant> assistant, TextCapture capture,
                           TextOutput output, RecentText recent, Cues cues, Executor background,
                           Consumer<String> say) {
        this.recorder = recorder;
        this.assistant = assistant;
        this.capture = capture;
        this.output = output;
        this.recent = recent;
        this.cues = cues;
        this.background = background;
        this.say = say;
    }

    /** A hold-to-talk shortcut went down: start listening. */
    void startListening(Mode mode) {
        if (assistant.isEmpty()) {
            say.accept((mode == Mode.ASK ? "Ask" : "Reply")
                    + " needs a Gemini API key. Add it to your Sayso settings file, then restart Sayso.");
            return;
        }
        if (recorder.isRecording()) {
            return;   // already listening: a repeated key event must not restart the recording
        }
        cancel();
        this.mode = mode;
        try {
            recorder.start();
            cues.listening();
            log.info("Listening ({})...", mode);
        } catch (AskException e) {
            say.accept(e.getMessage());
        }
    }

    /** The shortcut was let go: stop listening and send what was said. */
    void finishListening() {
        if (!recorder.isRecording()) {
            return;
        }
        Recording speech = recorder.stop();
        if (speech.durationMillis() < MIN_SPEECH_MILLIS) {
            say.accept("Hold the shortcut while you speak.");
            return;
        }
        cues.thinking();
        long letGoAt = System.nanoTime();
        int ticket = generation.incrementAndGet();
        if (mode == Mode.ASK) {
            // Copy the selection now: the user has just let go of the shortcut keys.
            String selection = PageCleaner.tidy(capture.captureSelection());
            recent.remember(selection);
            log.info("Asking about {} characters of selected text", selection.length());
            background.execute(() -> answer(ticket, speech, selection, letGoAt));
        } else {
            String context = recent.get();
            log.info("Writing a reply ({} characters of context)", context.length());
            background.execute(() -> reply(ticket, speech, context, letGoAt));
        }
    }

    private void answer(int ticket, Recording question, String selection, long letGoAt) {
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
        if (isCurrent(ticket)) {
            log.info("Answer ready {} ms after you let go", millisSince(letGoAt));
            say.accept(reply);
        }
    }

    private void reply(int ticket, Recording speech, String context, long letGoAt) {
        try {
            String text = assistant.orElseThrow().writeReply(speech.wav(), context).strip();
            if (!isCurrent(ticket)) {
                return;
            }
            if (text.isEmpty()) {
                say.accept("I didn't catch that. Please try again.");
                return;
            }
            long pasteStarted = System.nanoTime();
            output.paste(text);
            log.info("Typed a reply of {} characters, {} ms after you let go (Gemini {} ms, pasting {} ms)",
                    text.length(), millisSince(letGoAt),
                    (pasteStarted - letGoAt) / 1_000_000, millisSince(pasteStarted));
        } catch (AskException e) {
            log.warn("Reply failed: {}", e.getMessage(), e.getCause());
            if (isCurrent(ticket)) {
                say.accept(e.getMessage());
            }
        } catch (RuntimeException e) {
            log.error("Reply failed unexpectedly", e);
            if (isCurrent(ticket)) {
                say.accept("Something went wrong while writing your reply. Please try again.");
            }
        }
    }

    private static long millisSince(long nanoTime) {
        return (System.nanoTime() - nanoTime) / 1_000_000;
    }

    private boolean isCurrent(int ticket) {
        boolean current = ticket == generation.get();
        if (!current) {
            log.info("Dropped a result for a recording that was cancelled");
        }
        return current;
    }

    /** Drops any result still on its way, and stops listening. Called by Stop and by new reads. */
    void cancel() {
        generation.incrementAndGet();
        if (recorder.isRecording()) {
            recorder.stop();
        }
    }
}
