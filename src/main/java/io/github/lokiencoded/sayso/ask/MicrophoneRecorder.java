package io.github.lokiencoded.sayso.ask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

/**
 * Records from the default microphone with Java Sound.
 *
 * <p>16 kHz, 16-bit mono: the standard for speech. A minute of it is about 2 MB,
 * well under Gemini's 20 MB limit for inline audio.
 */
public final class MicrophoneRecorder implements Recorder {

    private static final Logger log = LoggerFactory.getLogger(MicrophoneRecorder.class);

    static final AudioFormat FORMAT = new AudioFormat(16_000, 16, 1, true, false);

    /** Safety net: if the key-up is missed, stop recording on our own. */
    private static final long MAX_MILLIS = 60_000;

    private TargetDataLine line;
    private Thread reader;
    private ByteArrayOutputStream pcm;
    private long startedAt;

    @Override
    public synchronized void start() throws AskException {
        if (isRecording()) {
            return;
        }
        try {
            line = AudioSystem.getTargetDataLine(FORMAT);
            line.open(FORMAT);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            line = null;
            throw new AskException("I can't find a microphone. Check that one is connected and allowed.", e);
        }
        pcm = new ByteArrayOutputStream();
        startedAt = System.currentTimeMillis();
        line.start();
        TargetDataLine recording = line;
        ByteArrayOutputStream sink = pcm;
        reader = new Thread(() -> copy(recording, sink), "sayso-microphone");
        reader.setDaemon(true);
        reader.start();
    }

    private void copy(TargetDataLine recording, ByteArrayOutputStream sink) {
        byte[] buffer = new byte[3_200];   // 100 ms of audio
        while (recording.isOpen()) {
            int read = recording.read(buffer, 0, buffer.length);
            if (read <= 0) {
                break;
            }
            synchronized (sink) {
                sink.write(buffer, 0, read);
            }
            if (System.currentTimeMillis() - startedAt > MAX_MILLIS) {
                log.info("Stopped recording after {} seconds", MAX_MILLIS / 1000);
                recording.stop();
                break;
            }
        }
    }

    @Override
    public synchronized Recording stop() {
        if (line == null) {
            return Recording.EMPTY;
        }
        long duration = System.currentTimeMillis() - startedAt;
        line.stop();
        line.close();   // makes read() return, which ends the reader thread
        try {
            reader.join(1_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        byte[] audio;
        synchronized (pcm) {
            audio = pcm.toByteArray();
        }
        line = null;
        return new Recording(toWav(audio, FORMAT), Math.min(duration, MAX_MILLIS));
    }

    @Override
    public synchronized boolean isRecording() {
        return line != null;
    }

    /** Wraps raw PCM samples in a WAV header so Gemini knows the format. */
    static byte[] toWav(byte[] pcm, AudioFormat format) {
        long frames = pcm.length / format.getFrameSize();
        try (AudioInputStream audio = new AudioInputStream(new ByteArrayInputStream(pcm), format, frames)) {
            ByteArrayOutputStream wav = new ByteArrayOutputStream(pcm.length + 44);
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, wav);
            return wav.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
