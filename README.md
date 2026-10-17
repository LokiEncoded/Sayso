# Sayso

Reads anything on your screen aloud with one shortcut. No copying, no switching apps.

Sayso is the other half of the voice loop: tools like Wispr Flow turn your voice into text in place; Sayso turns on-screen text into voice in place, and will speak for you too.

**Status:** v0.1, Stage 1 prototype. Windows only.

## Shortcuts

Hold **Ctrl+Alt**, then press:

| Key | Action |
| --- | --- |
| R | Read the selected text |
| P | Read the whole page or window |
| S | Stop |
| . / , | Faster / slower |
| V | Next voice |
| Q | Quit |

More voices: Windows Settings > Time & language > Speech > Add voices.

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
├── docs/
│   ├── ARCHITECTURE.md             How the parts fit together
│   └── devlog/                     What was built at each step and why
└── src/
    ├── main/java/io/github/lokiencoded/sayso/
    │   ├── SaysoApp.java           Entry point: wires the parts together
    │   ├── core/                   Commands, settings, the command loop
    │   ├── hotkey/                 System-wide keyboard shortcuts
    │   ├── capture/                Getting text from other apps
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

Speech uses Windows' built-in engine (System.Speech, via PowerShell), so there is nothing extra to install.

## Known limits (v0.2 work)

- Built-in Windows voices sound robotic, and each sentence takes a moment to start.
- "Read page" uses Ctrl+A, which picks up menus and clutter; inside a text box it selects only that box.
- The shortcut also reaches the app you are in; harmless in most apps.
- Settings reset each time the app starts.
