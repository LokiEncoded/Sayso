package io.github.lokiencoded.sayso.speech;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Natural-sounding offline voices with Piper (https://github.com/rhasspy/piper, MIT licence).
 *
 * <p>How one utterance works:
 * <ol>
 *   <li>Start piper.exe with a voice model and --output_raw.</li>
 *   <li>Write the text to its stdin on a separate thread. Piper turns it into audio one
 *       sentence at a time, and only reads more text as it speaks.</li>
 *   <li>A background thread reads the raw audio (16-bit mono PCM) from piper's stdout and
 *       plays it through Java Sound as it arrives, so the first sentence starts quickly.</li>
 * </ol>
 * Stopping kills the process and throws away any audio still buffered.
 */
public final class PiperSpeechEngine implements SpeechEngine {

    private static final Logger log = LoggerFactory.getLogger(PiperSpeechEngine.class);

    /** Each step of the -10..10 rate scale changes speed by about 7%. */
    private static final double RATE_STEP = 0.93;

    private final Path piperExe;
    private final Path logFile;
    private final List<PiperVoice> voices;
    private Playback current;

    public PiperSpeechEngine(Path piperExe, List<PiperVoice> voices, Path logFile) {
        if (voices.isEmpty()) {
            throw new IllegalArgumentException("Piper needs at least one voice");
        }
        this.piperExe = piperExe;
        this.voices = List.copyOf(voices);
        this.logFile = logFile;
    }

    @Override
    public synchronized void speak(String text, int rate, String voiceName) {
        stop();
        if (text == null || text.isBlank()) {
            return;
        }
        PiperVoice voice = find(voiceName);
        try {
            Process process = new ProcessBuilder(command(voice, rate))
                    .redirectError(ProcessBuilder.Redirect.appendTo(logFile.toFile()))
                    .start();
            current = new Playback(process, voice.sampleRate());
            current.start();
            // Never write the text on this thread: Piper reads it only as fast as it speaks,
            // so a long page would block here, and with it every other shortcut (even Stop).
            current.feed(prepare(text));
        } catch (IOException e) {
            stop();
            throw new UncheckedIOException("Could not start Piper (" + piperExe + ")", e);
        }
    }

    @Override
    public synchronized void stop() {
        if (current != null) {
            current.cancel();
            current = null;
        }
    }

    @Override
    public List<String> voices() {
        return voices.stream().map(PiperVoice::name).toList();
    }

    private PiperVoice find(String voiceName) {
        return voices.stream()
                .filter(voice -> voice.name().equals(voiceName))
                .findFirst()
                .orElse(voices.getFirst());
    }

    private List<String> command(PiperVoice voice, int rate) {
        List<String> command = new ArrayList<>();
        command.add(piperExe.toString());
        command.add("--model");
        command.add(voice.model().toString());
        command.add("--output_raw");
        command.add("--length_scale");
        command.add(String.format(Locale.ROOT, "%.3f", lengthScaleFor(rate)));
        return command;
    }

    /**
     * Piper's speed knob is "length scale": 1.0 is normal, smaller is faster.
     * Rate 0 maps to 1.0, rate 10 to about 0.48 (twice as fast), rate -10 to about 2.07 (half speed).
     */
    static double lengthScaleFor(int rate) {
        int clamped = Math.max(-10, Math.min(10, rate));
        return Math.pow(RATE_STEP, clamped);
    }

    /**
     * Piper reads one line at a time. Join wrapped lines (common in PDFs) so sentences are not
     * cut in half, but keep paragraph breaks, and end with a newline so the last line is read.
     */
    static String prepare(String text) {
        String normalised = text.replace("\r\n", "\n").replace('\r', '\n');
        String[] paragraphs = normalised.split("\\n\\s*\\n");
        StringBuilder out = new StringBuilder();
        for (String paragraph : paragraphs) {
            String joined = paragraph.strip().replaceAll("\\s*\\n\\s*", " ");
            if (!joined.isEmpty()) {
                out.append(joined).append('\n');
            }
        }
        return out.toString();
    }

    /** Plays one utterance: copies piper's audio to the speakers on its own thread. */
    private final class Playback extends Thread {

        private final Process process;
        private final AudioFormat format;
        private volatile boolean cancelled;
        private volatile SourceDataLine line;

        Playback(Process process, int sampleRate) {
            super("sayso-piper-playback");
            setDaemon(true);
            this.process = process;
            // 16-bit, mono, signed, little-endian: exactly what piper --output_raw produces.
            this.format = new AudioFormat(sampleRate, 16, 1, true, false);
        }

        @Override
        public void run() {
            try (InputStream audio = process.getInputStream()) {
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
                line.start();
                byte[] buffer = new byte[4096];
                int carry = 0;   // a 16-bit sample may arrive split across two reads
                int read;
                while (!cancelled && (read = audio.read(buffer, carry, buffer.length - carry)) != -1) {
                    int available = carry + read;
                    int whole = available - (available % 2);
                    line.write(buffer, 0, whole);
                    carry = available - whole;
                    if (carry > 0) {
                        buffer[0] = buffer[whole];
                    }
                }
                if (!cancelled) {
                    line.drain();   // let the last sentence finish playing
                }
            } catch (LineUnavailableException | IllegalArgumentException e) {
                // IllegalArgumentException is what Java Sound throws when no device supports the format.
                log.error("No audio output available for {}", format, e);
            } catch (IOException e) {
                if (!cancelled) {
                    log.warn("Piper audio stream ended unexpectedly", e);
                }
            } finally {
                closeLine();
                process.destroy();
            }
        }

        /** Sends the text to Piper on its own short-lived thread, so the caller returns at once. */
        void feed(String text) {
            Thread writer = new Thread(() -> {
                try (OutputStream stdin = process.getOutputStream()) {
                    stdin.write(text.getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    // Expected when Stop kills Piper mid-write; only worth logging otherwise.
                    if (!cancelled) {
                        log.warn("Could not send text to Piper", e);
                    }
                }
            }, "sayso-piper-input");
            writer.setDaemon(true);
            writer.start();
        }

        void cancel() {
            cancelled = true;
            SourceDataLine open = line;
            if (open != null) {
                open.stop();
                open.flush();   // drop audio already buffered so Stop is instant
            }
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
        }

        private void closeLine() {
            SourceDataLine open = line;
            if (open != null) {
                open.close();
            }
        }
    }
}
