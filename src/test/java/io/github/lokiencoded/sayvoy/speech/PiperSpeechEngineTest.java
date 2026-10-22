package io.github.lokiencoded.sayvoy.speech;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiperSpeechEngineTest {

    @Test
    void normalRateIsNormalSpeed() {
        assertEquals(1.0, PiperSpeechEngine.lengthScaleFor(0), 1e-9);
    }

    @Test
    void higherRateMeansShorterAudio() {
        assertTrue(PiperSpeechEngine.lengthScaleFor(5) < PiperSpeechEngine.lengthScaleFor(0));
        assertTrue(PiperSpeechEngine.lengthScaleFor(-5) > PiperSpeechEngine.lengthScaleFor(0));
    }

    @Test
    void rateIsClampedToTheScale() {
        assertEquals(PiperSpeechEngine.lengthScaleFor(10), PiperSpeechEngine.lengthScaleFor(99), 1e-9);
        assertEquals(PiperSpeechEngine.lengthScaleFor(-10), PiperSpeechEngine.lengthScaleFor(-99), 1e-9);
    }

    @Test
    void joinsWrappedLinesButKeepsParagraphs() {
        String pdfText = "This sentence was\r\nwrapped by a PDF.\r\n\r\nNew paragraph.";
        assertEquals("This sentence was wrapped by a PDF.\nNew paragraph.\n",
                PiperSpeechEngine.prepare(pdfText));
    }

    @Test
    void listsVoicesByName() {
        PiperSpeechEngine engine = new PiperSpeechEngine(Path.of("piper.exe"),
                List.of(new PiperVoice("amy", Path.of("amy.onnx"), 22050),
                        new PiperVoice("lessac", Path.of("lessac.onnx"), 22050)),
                Path.of("piper.log"));
        assertEquals(List.of("amy", "lessac"), engine.voices());
    }

    /**
     * Regression: reading a long page used to block speak() until Piper had read the whole
     * text, which froze the command loop so Stop could not get through.
     * A fake Piper that never reads its input proves speak() now returns at once.
     */
    @Test
    void speakReturnsImmediatelyEvenWhenPiperIsSlowToReadText() throws Exception {
        if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return;   // the fake Piper below is a shell script
        }
        Path fakePiper = Files.createTempFile("fake-piper", ".sh");
        Files.writeString(fakePiper, "#!/bin/sh\ntrap '' TERM\nsleep 30\n");
        fakePiper.toFile().setExecutable(true);

        PiperSpeechEngine engine = new PiperSpeechEngine(fakePiper,
                List.of(new PiperVoice("test", Path.of("test.onnx"), 22050)),
                Files.createTempFile("piper", ".log"));
        String longPage = "This is a long sentence on a busy web page. ".repeat(20_000);   // ~900 KB

        long start = System.nanoTime();
        engine.speak(longPage, 0, null);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        engine.stop();

        assertTrue(elapsedMs < 2_000, "speak() blocked for " + elapsedMs + " ms");
    }

    @Test
    void needsAtLeastOneVoice() {
        assertThrows(IllegalArgumentException.class,
                () -> new PiperSpeechEngine(Path.of("piper.exe"), List.of(), Path.of("piper.log")));
    }
}
