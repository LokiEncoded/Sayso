# Architecture

Sayvoy is four small parts connected by interfaces, with one class that wires them together.

```
 keyboard ──► HotkeyListener ──► queue of Commands ──► CommandLoop ──► SpeechEngine ──► speakers
                                                          │
                                                          └──► TextCapture ──► clipboard / other apps
```

| Part | Package | Job | Implementation |
| --- | --- | --- | --- |
| HotkeyListener | `hotkey` | Turns system-wide key presses into `Command`s | JNativeHook |
| CommandLoop | `core` | Takes one command at a time and carries it out | Plain Java, single thread |
| TextCapture / TextOutput | `capture` | Gets text from the app in front, and types text into it | Clipboard trick both ways (`ClipboardTextCapture`) |
| RecentText | `core` | Remembers what was last read aloud, as context for a reply (10 minutes) | In memory only |
| VoiceController | `core` + `ask` | Hold to talk for Ask and Reply: record, ask Gemini off the command thread, then speak the answer (Ask) or paste the cleaned-up text (Reply) | `MicrophoneRecorder`, `GeminiAssistant` (generateContent with inline WAV), `Cues` |
| SettingsFile | `config` | Loads and saves voice, speed and shortcuts in `settings.properties` | Java `Properties` to read; a commented, ordered file when writing |
| PageCleaner | `extract` | Turns a copied page into just the article (like Reader Mode) | jsoup, with semantic tags first and paragraph density as a fallback |
| SpeechEngine | `speech` | Speaks text, stops, lists voices | `PiperSpeechEngine` (neural, offline) when installed, else `WindowsSapiSpeechEngine`; chosen by `SpeechEngines.best()` |

## Rules we follow

1. **Parts depend on interfaces, not on each other's classes.** `CommandLoop` only knows `TextCapture` and `SpeechEngine`. That is why v0.2 can add a neural-voice engine without changing anything else, and why `CommandLoopTest` can use fakes.
2. **Only `SayvoyApp` creates things.** Every other class receives what it needs through its constructor (dependency injection, done by hand).
3. **Key handling never does slow work.** The hotkey thread only drops a command on the queue; the `CommandLoop` thread does the work. A slow speech start can never freeze the keyboard.
4. **One failed command never crashes the app.** The loop logs the error and carries on.
5. **The command loop never waits on the network or a long write.** Slow work (Gemini, feeding Piper a long page) runs on its own thread, so Stop always gets through.

## Planned parts

| Version | Addition |
| --- | --- |
| v0.2 | ~~Neural voices~~, ~~reading just the article~~, ~~saved and configurable settings~~: all done |
| v0.3 | ~~Ask: hold to talk, Gemini hears the question and sees the selection, answer spoken back~~ done |
| v0.4 | ~~Reply: speak a response, cleaned up and pasted into the text box (the full loop)~~ done |
| next | Packaging: a Windows installer and tray icon, so Sayvoy runs without IntelliJ |
