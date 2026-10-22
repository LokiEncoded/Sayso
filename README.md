# Sayso

[![Build](https://github.com/LokiEncoded/Sayso/actions/workflows/build.yml/badge.svg)](https://github.com/LokiEncoded/Sayso/actions/workflows/build.yml)

Reads anything on your screen aloud with one shortcut. No copying, no switching apps.

Sayso is the other half of the voice loop: tools like Wispr Flow turn your voice into text in place; Sayso turns on-screen text into voice in place, and will speak for you too.

**Status:** v0.4, the full loop: Sayso reads anything to you, answers questions about it, and writes your spoken replies into any text box. Windows only.

Contributing or picking this up again? See [docs/WORKFLOW.md](docs/WORKFLOW.md) for branches, commits and the PR flow.

## Natural voices (recommended)

Sayso speaks with [Piper](https://github.com/rhasspy/piper) neural voices: offline, free, and far more natural than the built-in Windows voices. Install them once:

```
powershell -ExecutionPolicy Bypass -File scripts\install-voices.ps1
```

This downloads Piper and three English voices (about 200 MB) into `%USERPROFILE%\.sayso`. Without them, Sayso falls back to the built-in Windows voices.

## Shortcuts

By default, hold **Ctrl+Alt**, then press (all of these can be changed, see [Settings](#settings)):

| Key | Action |
| --- | --- |
| R | Read the selected text; press again to stop |
| P | Read the whole page or window; press again to stop |
| A | Ask: hold, speak your question, let go |
| D | Reply: hold, speak, let go; typed into the text box you're in |
| X | Stop |
| . / , | Faster / slower |
| V | Next voice |
| Q | Quit |

More voices: add any `.onnx` + `.onnx.json` pair from [piper-voices](https://huggingface.co/rhasspy/piper-voices) to `%USERPROFILE%\.sayso\voices`. (Using Windows voices instead? Windows Settings > Time & language > Speech > Add voices.)

## Ask (hold to talk)

Select some text (or nothing), **hold Ctrl+Alt+A, ask your question out loud, and let go**. A rising pip means Sayso is listening, a falling one means it's thinking. Then it speaks the answer.

- "What does this mean?" · "Summarize this in two sentences" · "Translate this to Hindi" · "Is this email rude?"
- Works without a selection too: just ask.
- Ctrl+Alt+X stops a long answer, or cancels one still on its way.

**Setup (one time):** get a free API key at [Google AI Studio](https://aistudio.google.com/apikey), paste it after `gemini.api_key=` in your settings file, and restart Sayso.

**Privacy:** your recorded question and the selected text are sent to Google Gemini. Nothing is sent until you hold the Ask shortcut.

## Reply (hold to talk)

Click into any text box (WhatsApp, Gmail, Slack, Word…), **hold Ctrl+Alt+D, say what you want to write, and let go**. Sayso types a cleaned-up version for you: no "um"s, no false starts, grammar fixed, your own words and tone kept.

It closes the loop with reading: if Sayso just read you a message (Ctrl+Alt+R), your reply uses it as context, so names and details come out right. That context is forgotten after 10 minutes.

- "Sounds good um let's do Thursday no wait Friday at five" becomes "Sounds good, let's do Friday at five."
- Say "new line" or "new paragraph" for line breaks.
- Your clipboard is put back afterwards.

**Privacy:** like Ask, your speech and the recent context go to Google Gemini, and only while you hold the shortcut.

## Settings

Voice, speed and every shortcut live in `%USERPROFILE%\.sayso\settings.properties`, created the first time Sayso runs. Changing voice or speed with the shortcuts saves automatically. To change a shortcut, close Sayso, edit the file in Notepad, and start it again:

```
shortcut.stop=ctrl+shift+F12
```

Use one or more of `ctrl`, `alt`, `shift`, `win`, then one key. A bad value is reported in the console and the default is used instead.

## Run it in IntelliJ IDEA

1. **File > Open**, pick the `sayso` folder (or its `pom.xml`), and choose **Open as Project**.
2. **File > Project Structure > Project**: set the SDK to a **JDK 21** (IntelliJ can download one from that menu).
3. Wait for Maven to download the dependencies (progress bar at the bottom).
4. Open `src/main/java/io/github/lokiencoded/sayso/SaysoApp.java` and click the green run arrow next to `main`.
5. Select text in any app and press **Ctrl+Alt+R**.

Run the tests: right-click `src/test/java` > **Run 'All Tests'**, or `mvn test`.

## Build a runnable jar

```
mvn package
java -jar target/sayso.jar
```

## Project structure

```
sayso/
├── pom.xml                         Maven build: dependencies, Java 21, runnable jar
├── scripts/install-voices.ps1      Downloads Piper and natural voices
├── .github/workflows/build.yml     CI: builds and tests every push and PR on Windows
├── docs/
│   ├── ARCHITECTURE.md             How the parts fit together
│   ├── WORKFLOW.md                 Branches, commits and the PR flow
│   └── devlog/                     What was built at each step and why
└── src/
    ├── main/java/io/github/lokiencoded/sayso/
    │   ├── SaysoApp.java           Entry point: wires the parts together
    │   ├── core/                   Commands, settings, the command loop
    │   ├── hotkey/                 System-wide keyboard shortcuts
    │   ├── ask/                    Microphone, Gemini, spoken answers and written replies
    │   ├── capture/                Getting text from other apps
    │   ├── config/                 Settings file, shortcuts and key names
    │   ├── extract/                Finding the article in a copied page
    │   └── speech/                 Turning text into speech
    ├── main/resources/logback.xml  Log format
    └── test/java/...               Unit tests, mirroring the main packages
```

## Dependencies

| Library | Why |
| --- | --- |
| [JNativeHook](https://github.com/kwhat/jnativehook) | Keyboard shortcuts that work in every app, not just ours |
| [Gson](https://github.com/google/gson) | JSON for Gemini requests and replies |
| SLF4J + Logback | Logging |
| JUnit 5 | Tests |

Speech runs outside Java: [Piper](https://github.com/rhasspy/piper) (MIT) for natural voices, installed by `scripts/install-voices.ps1`, or Windows' built-in engine (System.Speech, via PowerShell) as a fallback.

## Known limits

- In browsers, "Read page" reads just the article (title, body, headings). Apps that copy only plain text are read in full.
- "Read page" uses Ctrl+A, so the page stays highlighted afterwards; inside a text box it selects only that box.
- The shortcut also reaches the app you are in; harmless in most apps.
- Shortcut changes take effect when Sayso restarts.
