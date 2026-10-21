package io.github.lokiencoded.sayso.speech;

import io.github.lokiencoded.sayso.core.SaysoPaths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.List;

/** Picks the best speech engine available on this computer. */
public final class SpeechEngines {

    private static final Logger log = LoggerFactory.getLogger(SpeechEngines.class);

    private SpeechEngines() {
    }

    /**
     * Natural Piper voices when they are installed, otherwise the built-in Windows voices,
     * so Sayso always works, just less nicely without Piper.
     */
    public static SpeechEngine best(SaysoPaths paths) {
        List<PiperVoice> voices = PiperVoice.discover(paths.voicesDir());
        boolean piperInstalled = Files.isRegularFile(paths.piperExecutable());

        if (piperInstalled && !voices.isEmpty()) {
            log.info("Using natural Piper voices: {}", voices.stream().map(PiperVoice::name).toList());
            return new PiperSpeechEngine(paths.piperExecutable(), voices, paths.home().resolve("piper.log"));
        }

        if (!piperInstalled) {
            log.info("Piper not found at {}", paths.piperExecutable());
        } else {
            log.info("No Piper voices found in {}", paths.voicesDir());
        }
        log.info("Using built-in Windows voices. For natural voices, run scripts\\install-voices.ps1");
        return new WindowsSapiSpeechEngine();
    }
}
