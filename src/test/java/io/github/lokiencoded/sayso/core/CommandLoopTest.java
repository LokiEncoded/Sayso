package io.github.lokiencoded.sayso.core;

import io.github.lokiencoded.sayso.capture.TextCapture;
import io.github.lokiencoded.sayso.speech.SpeechEngine;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
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
    private final CommandLoop loop =
            new CommandLoop(new LinkedBlockingQueue<>(), capture, speech, settings);

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
    void fasterRaisesRateAndAnnouncesIt() {
        loop.handle(Command.FASTER);
        assertEquals(2, settings.rate());
        assertEquals(List.of("Speed 2"), speech.spoken);
        assertEquals(2, speech.lastRate);
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

    private static final class FakeCapture implements TextCapture {
        String selection = "";
        String page = "";

        @Override
        public String captureSelection() {
            return selection;
        }

        @Override
        public String captureAll() {
            return page;
        }
    }

    private static final class FakeSpeech implements SpeechEngine {
        final List<String> spoken = new ArrayList<>();
        List<String> installed = List.of();
        int stops;
        int lastRate;
        String lastVoice;

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
