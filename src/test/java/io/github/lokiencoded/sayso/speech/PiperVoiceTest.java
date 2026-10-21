package io.github.lokiencoded.sayso.speech;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PiperVoiceTest {

    @TempDir
    Path dir;

    @Test
    void findsModelsThatHaveAConfig() throws IOException {
        voice("en_US-lessac-medium", "{\"audio\": {\"sample_rate\": 22050}}");
        voice("en_GB-alba-medium", "{\"audio\": {\"quality\": \"medium\", \"sample_rate\": 16000}}");

        List<PiperVoice> voices = PiperVoice.discover(dir);

        assertEquals(List.of("en_GB-alba-medium", "en_US-lessac-medium"),
                voices.stream().map(PiperVoice::name).toList());
        assertEquals(16000, voices.getFirst().sampleRate());
    }

    @Test
    void skipsModelsWithoutAConfig() throws IOException {
        Files.writeString(dir.resolve("orphan.onnx"), "model");
        assertTrue(PiperVoice.discover(dir).isEmpty());
    }

    @Test
    void missingFolderMeansNoVoices() {
        assertTrue(PiperVoice.discover(dir.resolve("does-not-exist")).isEmpty());
    }

    @Test
    void fallsBackToDefaultSampleRate() {
        assertEquals(PiperVoice.DEFAULT_SAMPLE_RATE, PiperVoice.sampleRateFrom("{\"audio\": {}}"));
    }

    private void voice(String name, String config) throws IOException {
        Files.writeString(dir.resolve(name + ".onnx"), "model");
        Files.writeString(dir.resolve(name + ".onnx.json"), config);
    }
}
