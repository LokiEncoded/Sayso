# 002: Natural voices with Piper

**Date:** 2026-10-22 · **Stage:** 1 → 2 (Read prototype, heading to MVP)

## What we built

A second `SpeechEngine` that speaks with Piper's neural voices: offline, free, and far more natural than the built-in Windows voices. Sayso picks it automatically when Piper is installed, and falls back to Windows voices when it isn't.

## Decisions and why

| Decision | Why | Alternative we skipped |
| --- | --- | --- |
| Piper (C++ release 2023.11.14-2) | Offline, fast on a normal laptop, MIT licence, Windows build ready to download | Cloud voices (Azure, ElevenLabs): better quality, but cost money per character and need an API key. Saved for the paid tier. |
| The archived C++ build, not the newer "piper1-gpl" | The new project is GPL, which complicates a commercial product; the C++ build is MIT and works | Kokoro: newer and very good, but a heavier setup |
| Run piper.exe as a separate process | No native code in our Java app; the same pattern as the Windows engine | Calling ONNX Runtime from Java directly: much more work |
| Stream raw audio and play it as it arrives | First sentence starts in well under a second instead of waiting for the whole text | Write a WAV file, then play it: simpler, but slow on long pages |
| Files in `~/.sayso` | One predictable place per user, outside the code; settings will live here too (v0.2.x) | Next to the jar: breaks when the jar moves |
| Fall back to Windows voices | The app always works, even before the user installs anything | Refuse to start without Piper |

## Concepts to study from this step

- **Raw PCM audio:** sound as a stream of numbers. Here each sample is 16 bits (2 bytes), little-endian, one channel, 22,050 samples per second. Look at `Playback.run()` and why we carry over a half sample when a read ends on an odd byte.
- **Java Sound (`javax.sound.sampled`):** `AudioFormat` describes the stream; a `SourceDataLine` is a pipe to the speakers. `drain()` waits for playback to finish; `flush()` throws away what's buffered (that's how Stop is instant).
- **The Strategy pattern:** `SpeechEngine` is the strategy; `SpeechEngines.best()` chooses one at startup. `CommandLoop` didn't change at all in this PR. That's the payoff from v0.1's interfaces.
- **Threads and `volatile`:** the playback thread and the command thread share the `cancelled` flag. `volatile` makes sure one thread sees the other's write.

## Exercises for you

1. Run `scripts\install-voices.ps1`, restart Sayso, and compare voices with Ctrl+Alt+V. Which one should be the default?
2. Find a Hindi or Indian-English voice in the [piper-voices list](https://huggingface.co/rhasspy/piper-voices) and add it to the install script.
3. Read the PR diff on GitHub before merging, and leave at least one review comment.
