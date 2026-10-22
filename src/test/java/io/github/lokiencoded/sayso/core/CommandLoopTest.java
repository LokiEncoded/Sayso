package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.ask.Cues;
import io.github.lokiencoded.sayso.ask.Recorder;
import io.github.lokiencoded.sayso.capture.CapturedText;
import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.speech.SpeechEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the app's behaviour with fake parts: no keyboard, clipboard or speakers needed.
 * This is what the interfaces buy us.
 */
class CommandLoopTest {

    private final FakeCapture capture = new FakeCapture();
    private final FakeSpeech speech = new FakeSpeech();
    private final Settings settings = new Settings();
    private int saves;
    private final AskController ask = new AskController(new IdleRecorder(), Optional.empty(), capture,
            Cues.SILENT, Runnable::run, speech.spoken::add);
    private final CommandLoop loop =
            new CommandLoop(new LinkedBlockingQueue<>(), capture, speech, settings, saved -> saves++, ask);

    @Test
    void readsSelectedText() {
        capture.selection = "Hello world";
        loop.handle(Command.READ_SELECTION);
        assertEquals(List.of("Hello world"), speech.spoken);
    }

    @Test
    void saysSoWhenNothingIsSelected() {
        capture.selection = "";
        loop.handle(Command.READ_SELECTION);
        assertEquals(List.of("Nothing is selected."), speech.spoken);
    }

    @Test
    void readsWholePage() {
        capture.page = "The whole page";
        loop.handle(Command.READ_PAGE);
        assertEquals(List.of("The whole page"), speech.spoken);
    }

    @Test
    void readsOnlyTheArticleWhenTheCopyHasHtml() {
        capture.page = "Home News Sport\nBig story\nThe article text.\nPrivacy Terms";
        capture.pageHtml = "<nav>Home News Sport</nav><article><h1>Big story</h1>"
                + "<p>" + "The article text is long enough to count as an article. ".repeat(5) + "</p>"
                + "</article><footer>Privacy Terms</footer>";
        loop.handle(Command.READ_PAGE);
        String spoken = speech.spoken.getFirst();
        assertTrue(spoken.startsWith("Big story."));
        assertFalse(spoken.contains("Home News Sport"));
        assertFalse(spoken.contains("Privacy"));
    }

    @Test
    void dropsCitationMarkersFromSelections() {
        capture.selection = "Free content is free.[5] It is open.[citation needed]";
        loop.handle(Command.READ_SELECTION);
        assertEquals(List.of("Free content is free. It is open."), speech.spoken);
    }

    @Test
    void fasterRaisesRateAndAnnouncesIt() {
        loop.handle(Command.FASTER);
        assertEquals(2, settings.rate());
        assertEquals(List.of("Speed 2"), speech.spoken);
        assertEquals(2, speech.lastRate);
        assertEquals(1, saves);   // speed survives a restart
    }

    @Test
    void nextVoiceCyclesThroughInstalledVoices() {
        speech.installed = List.of("David", "Zira");
        loop.handle(Command.NEXT_VOICE);
        assertEquals("David", speech.lastVoice);
        loop.handle(Command.NEXT_VOICE);
        assertEquals("Zira", speech.lastVoice);
        loop.handle(Command.NEXT_VOICE);
        assertEquals("David", speech.lastVoice);
        assertEquals(3, saves);   // each voice change is saved
    }

    @Test
    void readingDoesNotRewriteTheSettingsFile() {
        capture.selection = "Hello";
        loop.handle(Command.READ_SELECTION);
        assertEquals(0, saves);
    }

    @Test
    void readShortcutStopsWhenAlreadySpeaking() {
        capture.selection = "Hello world";
        speech.speaking = true;
        loop.handle(Command.READ_SELECTION);
        assertEquals(1, speech.stops);
        assertTrue(speech.spoken.isEmpty());
    }

    @Test
    void pageShortcutStopsWhenAlreadySpeaking() {
        capture.page = "The whole page";
        speech.speaking = true;
        loop.handle(Command.READ_PAGE);
        assertEquals(1, speech.stops);
        assertTrue(speech.spoken.isEmpty());
    }

    @Test
    void askStopsSpeechSoItDoesNotRecordItself() {
        speech.speaking = true;
        loop.handle(Command.ASK);
        assertEquals(1, speech.stops);
        assertTrue(speech.spoken.getFirst().contains("API key"));   // no key in this test
    }

    @Test
    void askReleasedWithoutAQuestionIsIgnored() {
        assertTrue(loop.handle(Command.ASK_RELEASED));
        assertTrue(speech.spoken.isEmpty());
    }

    @Test
    void stopStopsSpeech() {
        assertTrue(loop.handle(Command.STOP));
        assertEquals(1, speech.stops);
    }

    @Test
    void quitStopsSpeechAndEndsTheLoop() {
        assertFalse(loop.handle(Command.QUIT));
        assertEquals(1, speech.stops);
    }

    private static final class IdleRecorder implements Recorder {
        @Override
        public void start() {
        }

        @Override
        public Recording stop() {
            return Recording.EMPTY;
        }

        @Override
        public boolean isRecording() {
            return false;
        }
    }

    private static final class FakeCapture implements TextCapture {
        String selection = "";
        String page = "";

        @Override
        public String captureSelection() {
            return selection;
        }

        String pageHtml;

        @Override
        public CapturedText captureAll() {
            return new CapturedText(page, pageHtml);
        }
    }

    private static final class FakeSpeech implements SpeechEngine {
        final List<String> spoken = new ArrayList<>();
        List<String> installed = List.of();
        int stops;
        int lastRate;
        String lastVoice;
        boolean speaking;

        @Override
        public boolean isSpeaking() {
            return speaking;
        }

        @Override
        public void speak(String text, int rate, String voiceName) {
            spoken.add(text);
            lastRate = rate;
            lastVoice = voiceName;
        }

        @Override
        public void stop() {
            stops++;
        }

        @Override
        public List<String> voices() {
            return installed;
        }
    }
}
