package io.github.lokiencoded.tellsy.core;

import io.github.lokiencoded.tellsy.ask.AskException;
import io.github.lokiencoded.tellsy.ask.Assistant;
import io.github.lokiencoded.tellsy.ask.Cues;
import io.github.lokiencoded.tellsy.ask.Recorder;
import io.github.lokiencoded.tellsy.capture.CapturedText;
import io.github.lokiencoded.tellsy.capture.TextCapture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VoiceControllerTest {

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

    private final List<String> typed = new ArrayList<>();
    private final RecentText recent = new RecentText();

    private VoiceController controller(Optional<Assistant> assistant) {
        return new VoiceController(recorder, assistant, capture, typed::add, recent, Cues.SILENT, later, spoken::add);
    }

    @Test
    void holdSpeakReleaseGetsASpokenAnswer() {
        VoiceController ask = controller(Optional.of(assistant));
        ask.startListening(VoiceController.Mode.ASK);
        assertTrue(recorder.recording);

        recorder.durationMillis = 2_000;
        ask.finishListening();
        assertFalse(recorder.recording);
        pending.forEach(Runnable::run);

        assertEquals(List.of("Plants make sugar from light."), spoken);   // markdown removed
        assertEquals("The selected paragraph.", assistant.lastSelection);  // citation removed
        assertEquals(2_000, assistant.lastWav.length);
    }

    /** Seen on a real laptop: a second "pressed" event while already listening restarted the question. */
    @Test
    void aSecondPressWhileListeningIsIgnored() {
        VoiceController ask = controller(Optional.of(assistant));
        ask.startListening(VoiceController.Mode.ASK);
        recorder.durationMillis = 2_000;
        ask.startListening(VoiceController.Mode.ASK);
        assertEquals(1, recorder.starts);
        assertTrue(recorder.recording);
    }

    @Test
    void withoutAnApiKeyItExplainsHowToSetUp() {
        VoiceController ask = controller(Optional.empty());
        ask.startListening(VoiceController.Mode.ASK);
        assertFalse(recorder.recording);
        assertTrue(spoken.getFirst().contains("API key"), spoken.getFirst());
    }

    @Test
    void aQuickTapIsNotSent() {
        VoiceController ask = controller(Optional.of(assistant));
        ask.startListening(VoiceController.Mode.ASK);
        recorder.durationMillis = 100;
        ask.finishListening();
        assertTrue(pending.isEmpty());
        assertTrue(spoken.getFirst().contains("Hold the shortcut"), spoken.getFirst());
    }

    @Test
    void stopDropsAnAnswerStillOnItsWay() {
        VoiceController ask = controller(Optional.of(assistant));
        ask.startListening(VoiceController.Mode.ASK);
        recorder.durationMillis = 2_000;
        ask.finishListening();

        ask.cancel();                     // user pressed Stop while Gemini was thinking
        pending.forEach(Runnable::run);   // the answer arrives late

        assertTrue(spoken.isEmpty());
    }

    @Test
    void errorsAreSpoken() {
        assistant.failure = new AskException("I couldn't reach Gemini. Check your internet connection.");
        VoiceController ask = controller(Optional.of(assistant));
        ask.startListening(VoiceController.Mode.ASK);
        recorder.durationMillis = 2_000;
        ask.finishListening();
        pending.forEach(Runnable::run);
        assertEquals(List.of("I couldn't reach Gemini. Check your internet connection."), spoken);
    }

    @Test
    void missingMicrophoneIsSpoken() {
        recorder.failure = new AskException("I can't find a microphone.");
        controller(Optional.of(assistant)).startListening(VoiceController.Mode.ASK);
        assertEquals(List.of("I can't find a microphone."), spoken);
    }

    // ---- Reply ----

    @Test
    void replyIsTypedIntoTheTextBoxNotSpoken() {
        holdSpeakAndLetGoForReply();
        assertEquals(List.of("Sounds good, see you Thursday."), typed);
        assertTrue(spoken.isEmpty());
    }

    @Test
    void replyUsesWhatWasJustReadAsContext() {
        recent.remember("Hi Lokesh, can we meet on Thursday? - Priya");
        holdSpeakAndLetGoForReply();
        assertEquals("Hi Lokesh, can we meet on Thursday? - Priya", assistant.lastReplyContext);
    }

    @Test
    void askingRemembersTheSelectionForTheNextReply() {
        VoiceController voice = controller(Optional.of(assistant));
        voice.startListening(VoiceController.Mode.ASK);
        recorder.durationMillis = 2_000;
        voice.finishListening();
        assertEquals("The selected paragraph.", recent.get());
    }

    @Test
    void anEmptyReplyIsSpokenNotTyped() {
        assistant.reply = "   ";
        holdSpeakAndLetGoForReply();
        assertTrue(typed.isEmpty());
        assertTrue(spoken.getFirst().contains("didn't catch"), spoken.getFirst());
    }

    @Test
    void stopDropsAReplyStillOnItsWay() {
        VoiceController voice = controller(Optional.of(assistant));
        voice.startListening(VoiceController.Mode.REPLY);
        recorder.durationMillis = 2_000;
        voice.finishListening();
        voice.cancel();
        pending.forEach(Runnable::run);
        assertTrue(typed.isEmpty());
    }

    @Test
    void replyWithoutAKeySaysReplyNeedsOne() {
        controller(Optional.empty()).startListening(VoiceController.Mode.REPLY);
        assertTrue(spoken.getFirst().startsWith("Reply needs"), spoken.getFirst());
    }

    private void holdSpeakAndLetGoForReply() {
        VoiceController voice = controller(Optional.of(assistant));
        voice.startListening(VoiceController.Mode.REPLY);
        recorder.durationMillis = 2_000;
        voice.finishListening();
        pending.forEach(Runnable::run);
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
        int starts;
        AskException failure;

        @Override
        public void start() throws AskException {
            if (failure != null) {
                throw failure;
            }
            starts++;
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
        String lastReplyContext;
        String reply = "Sounds good, see you Thursday.";
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

        @Override
        public String writeReply(byte[] speechWav, String recentContext) throws AskException {
            if (failure != null) {
                throw failure;
            }
            lastReplyContext = recentContext;
            return reply;
        }
    }
}
