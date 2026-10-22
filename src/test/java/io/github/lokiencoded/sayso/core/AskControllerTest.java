package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.ask.AskException;
import io.github.lokiencoded.sayso.ask.Assistant;
import io.github.lokiencoded.sayso.ask.Cues;
import io.github.lokiencoded.sayso.ask.Recorder;
import io.github.lokiencoded.sayso.capture.CapturedText;
import io.github.lokiencoded.sayso.capture.TextCapture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AskControllerTest {

    private final FakeRecorder recorder = new FakeRecorder();
    private final FakeAssistant assistant = new FakeAssistant();
    private final List<String> spoken = new ArrayList<>();
    private final List<Runnable> pending = new ArrayList<>();
    private String selection = "The selected paragraph.[3]";

    private final TextCapture capture = new TextCapture() {
        @Override
        public String captureSelection() {
            return selection;
        }

        @Override
        public CapturedText captureAll() {
            return CapturedText.EMPTY;
        }
    };

    /** Holds background work until the test runs it, so we can cancel in between. */
    private final Executor later = pending::add;

    private AskController controller(Optional<Assistant> assistant) {
        return new AskController(recorder, assistant, capture, Cues.SILENT, later, spoken::add);
    }

    @Test
    void holdSpeakReleaseGetsASpokenAnswer() {
        AskController ask = controller(Optional.of(assistant));
        ask.startListening();
        assertTrue(recorder.recording);

        recorder.durationMillis = 2_000;
        ask.finishListening();
        assertFalse(recorder.recording);
        pending.forEach(Runnable::run);

        assertEquals(List.of("Plants make sugar from light."), spoken);   // markdown removed
        assertEquals("The selected paragraph.", assistant.lastSelection);  // citation removed
        assertEquals(2_000, assistant.lastWav.length);
    }

    @Test
    void withoutAnApiKeyItExplainsHowToSetUp() {
        AskController ask = controller(Optional.empty());
        ask.startListening();
        assertFalse(recorder.recording);
        assertTrue(spoken.getFirst().contains("API key"), spoken.getFirst());
    }

    @Test
    void aQuickTapIsNotSent() {
        AskController ask = controller(Optional.of(assistant));
        ask.startListening();
        recorder.durationMillis = 100;
        ask.finishListening();
        assertTrue(pending.isEmpty());
        assertTrue(spoken.getFirst().contains("Hold the shortcut"), spoken.getFirst());
    }

    @Test
    void stopDropsAnAnswerStillOnItsWay() {
        AskController ask = controller(Optional.of(assistant));
        ask.startListening();
        recorder.durationMillis = 2_000;
        ask.finishListening();

        ask.cancel();                     // user pressed Stop while Gemini was thinking
        pending.forEach(Runnable::run);   // the answer arrives late

        assertTrue(spoken.isEmpty());
    }

    @Test
    void errorsAreSpoken() {
        assistant.failure = new AskException("I couldn't reach Gemini. Check your internet connection.");
        AskController ask = controller(Optional.of(assistant));
        ask.startListening();
        recorder.durationMillis = 2_000;
        ask.finishListening();
        pending.forEach(Runnable::run);
        assertEquals(List.of("I couldn't reach Gemini. Check your internet connection."), spoken);
    }

    @Test
    void missingMicrophoneIsSpoken() {
        recorder.failure = new AskException("I can't find a microphone.");
        controller(Optional.of(assistant)).startListening();
        assertEquals(List.of("I can't find a microphone."), spoken);
    }

    @Test
    void letGoWithoutListeningDoesNothing() {
        controller(Optional.of(assistant)).finishListening();
        assertTrue(spoken.isEmpty());
        assertTrue(pending.isEmpty());
    }

    private static final class FakeRecorder implements Recorder {
        boolean recording;
        long durationMillis;
        AskException failure;

        @Override
        public void start() throws AskException {
            if (failure != null) {
                throw failure;
            }
            recording = true;
        }

        @Override
        public Recording stop() {
            recording = false;
            return new Recording(new byte[(int) durationMillis], durationMillis);
        }

        @Override
        public boolean isRecording() {
            return recording;
        }
    }

    private static final class FakeAssistant implements Assistant {
        byte[] lastWav;
        String lastSelection;
        AskException failure;

        @Override
        public String answer(byte[] questionWav, String selectedText) throws AskException {
            if (failure != null) {
                throw failure;
            }
            lastWav = questionWav;
            lastSelection = selectedText;
            return "**Plants** make sugar from light.";
        }
    }
}
