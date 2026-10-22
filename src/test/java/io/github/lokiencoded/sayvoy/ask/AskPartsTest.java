package io.github.lokiencoded.sayvoy.ask;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Small pieces of Ask: speech-friendly cleanup, the WAV wrapper and the cue tones. */
class AskPartsTest {

    @Test
    void removesMarkdownThatSoundsWrongAloud() {
        String answer = """
                ## Summary
                **Photosynthesis** is how plants make *food*.
                - It needs `light`
                - See [this page](https://example.com)
                """;
        assertEquals("Summary\nPhotosynthesis is how plants make food.\nIt needs light\nSee this page",
                SpeakableText.clean(answer));
    }

    @Test
    void leavesPlainSentencesAlone() {
        String plain = "Prices rose 5% in 2026, and that's a 3-year high.";
        assertEquals(plain, SpeakableText.clean(plain));
    }

    @Test
    void wavHasAProperHeaderAndAllTheAudio() throws Exception {
        byte[] pcm = new byte[16_000 * 2];   // one second of silence at 16 kHz, 16-bit mono
        byte[] wav = MicrophoneRecorder.toWav(pcm, MicrophoneRecorder.FORMAT);

        AudioFileFormat format = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(wav));
        assertEquals(AudioFileFormat.Type.WAVE, format.getType());
        assertEquals(16_000, format.getFormat().getSampleRate(), 0.1);
        assertEquals(16_000, format.getFrameLength());
    }

    @Test
    void cueToneHasTheRightLength() {
        byte[] tone = Cues.sweep(660, 990, 0.1);
        assertEquals(4_410 * 2, tone.length);   // 0.1 s at 44.1 kHz, 2 bytes per sample
        assertEquals(0, tone[0]);               // starts silent: faded in, so no click
    }
}
