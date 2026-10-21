# Architecture

Sayso is four small parts connected by interfaces, with one class that wires them together.

```
 keyboard ──► HotkeyListener ──► queue of Commands ──► CommandLoop ──► SpeechEngine ──► speakers
                                                          │
                                                          └──► TextCapture ──► clipboard / other apps
```

| Part | Package | Job | v0.1 implementation |
| --- | --- | --- | --- |
| HotkeyListener | `hotkey` | Turns system-wide key presses into `Command`s | JNativeHook |
| CommandLoop | `core` | Takes one command at a time and carries it out | Plain Java, single thread |
| TextCapture | `capture` | Gets text from the app in front | Clipboard trick (`ClipboardTextCapture`) |
| SpeechEngine | `speech` | Speaks text, stops, lists voices | `PiperSpeechEngine` (neural, offline) when installed, else `WindowsSapiSpeechEngine`; chosen by `SpeechEngines.best()` |

## Rules we follow

1. **Parts depend on interfaces, not on each other's classes.** `CommandLoop` only knows `TextCapture` and `SpeechEngine`. That is why v0.2 can add a neural-voice engine without changing anything else, and why `CommandLoopTest` can use fakes.
2. **Only `SaysoApp` creates things.** Every other class receives what it needs through its constructor (dependency injection, done by hand).
3. **Key handling never does slow work.** The hotkey thread only drops a command on the queue; the `CommandLoop` thread does the work. A slow speech start can never freeze the keyboard.
4. **One failed command never crashes the app.** The loop logs the error and carries on.

## Planned parts

| Version | Addition |
| --- | --- |
| v0.2 | ~~Neural voices (`SpeechEngine`)~~ done, UI Automation reading (`TextCapture`), saved settings |
| v0.3 | Ask: speech-to-text plus an AI model, answers spoken back |
| v0.4 | Reply: speak a response, cleaned up and pasted into the text box (the full loop) |
