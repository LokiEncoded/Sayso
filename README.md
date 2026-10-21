# Sayso

[![Build](https://github.com/LokiEncoded/Sayso/actions/workflows/build.yml/badge.svg)](https://github.com/LokiEncoded/Sayso/actions/workflows/build.yml)

Reads anything on your screen aloud with one shortcut. No copying, no switching apps.

Sayso is the other half of the voice loop: tools like Wispr Flow turn your voice into text in place; Sayso turns on-screen text into voice in place, and will speak for you too.

**Status:** v0.2: natural offline voices, toggle shortcuts, article-only page reading. Windows only.

Contributing or picking this up again? See [docs/WORKFLOW.md](docs/WORKFLOW.md) for branches, commits and the PR flow.

## Natural voices (recommended)

Sayso speaks with [Piper](https://github.com/rhasspy/piper) neural voices: offline, free, and far more natural than the built-in Windows voices. Install them once:

```
powershell -ExecutionPolicy Bypass -File scripts\install-voices.ps1
```

This downloads Piper and three English voices (about 200 MB) into `%USERPROFILE%\.sayso`. Without them, Sayso falls back to the built-in Windows voices.

## Shortcuts

Hold **Ctrl+Alt**, then press:

| Key | Action |
| --- | --- |
| R | Read the selected text; press again to stop |
| P | Read the whole page or window; press again to stop |
| X | Stop |
| . / , | Faster / slower |
| V | Next voice |
| Q | Quit |

More voices: add any `.onnx` + `.onnx.json` pair from [piper-voices](https://huggingface.co/rhasspy/piper-voices) to `%USERPROFILE%\.sayso\voices`. (Using Windows voices instead? Windows Settings > Time & language > Speech > Add voices.)

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
    │   ├── capture/                Getting text from other apps
    │   ├── extract/                Finding the article in a copied page
    │   └── speech/                 Turning text into speech
    ├── main/resources/logback.xml  Log format
    └── test/java/...               Unit tests, mirroring the main packages
```

## Dependencies

| Library | Why |
| --- | --- |
| [JNativeHook](https://github.com/kwhat/jnativehook) | Keyboard shortcuts that work in every app, not just ours |
| SLF4J + Logback | Logging |
| JUnit 5 | Tests |

Speech runs outside Java: [Piper](https://github.com/rhasspy/piper) (MIT) for natural voices, installed by `scripts/install-voices.ps1`, or Windows' built-in engine (System.Speech, via PowerShell) as a fallback.

## Known limits

- In browsers, "Read page" reads just the article (title, body, headings). Apps that copy only plain text are read in full.
- "Read page" uses Ctrl+A, so the page stays highlighted afterwards; inside a text box it selects only that box.
- The shortcut also reaches the app you are in; harmless in most apps.
- Settings (voice, speed) reset each time the app starts, and shortcuts can't be changed yet. Next up.
