package io.github.lokiencoded.sayvoy.ask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Short tones so you know what Ask is doing without looking: a rising "pip" when it
 * starts listening, a falling one when it stops and starts thinking.
 * Generated in code, so there are no sound files to ship.
 */
public class Cues {

    private static final Logger log = LoggerFactory.getLogger(Cues.class);
    private static final float RATE = 44_100;

    /** A silent version for tests and for machines without speakers. */
    public static final Cues SILENT = new Cues() {
        @Override
        public void listening() {
        }

        @Override
        public void thinking() {
        }
    };

    public void listening() {
        playAsync(660, 990);
    }

    public void thinking() {
        playAsync(990, 660);
    }

    private void playAsync(double fromHz, double toHz) {
        Thread player = new Thread(() -> play(fromHz, toHz), "sayvoy-cue");
        player.setDaemon(true);
        player.start();
    }

    private static void play(double fromHz, double toHz) {
        AudioFormat format = new AudioFormat(RATE, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            byte[] tone = sweep(fromHz, toHz, 0.12);
            line.write(tone, 0, tone.length);
            line.drain();
        } catch (Exception e) {
            log.debug("Could not play cue", e);
        }
    }

    /** A sine tone gliding from one pitch to another, faded in and out so it doesn't click. */
    static byte[] sweep(double fromHz, double toHz, double seconds) {
        int samples = (int) (RATE * seconds);
        byte[] out = new byte[samples * 2];
        double phase = 0;
        for (int i = 0; i < samples; i++) {
            double progress = (double) i / samples;
            double hz = fromHz + (toHz - fromHz) * progress;
            phase += 2 * Math.PI * hz / RATE;
            double fade = Math.min(1, Math.min(progress, 1 - progress) * 10);
            short value = (short) (Math.sin(phase) * fade * 0.3 * Short.MAX_VALUE);
            out[2 * i] = (byte) value;
            out[2 * i + 1] = (byte) (value >> 8);
        }
        return out;
    }
}
