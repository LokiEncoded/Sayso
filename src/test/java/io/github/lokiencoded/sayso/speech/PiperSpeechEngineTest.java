package io.github.lokiencoded.sayso.speech;

import org.junit.jupiter.api.Test;

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

    @Test
    void needsAtLeastOneVoice() {
        assertThrows(IllegalArgumentException.class,
                () -> new PiperSpeechEngine(Path.of("piper.exe"), List.of(), Path.of("piper.log")));
    }
}
