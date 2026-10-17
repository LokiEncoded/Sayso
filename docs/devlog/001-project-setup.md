# 001: Project setup and the first reader

**Date:** 2026-10-18 · **Stage:** 1 (Read prototype)

## What we built

A Java 21 Maven project that reads selected text, or a whole page, aloud from any Windows app with Ctrl+Alt shortcuts.

## Decisions and why

| Decision | Why | Alternative we skipped |
| --- | --- | --- |
| Maven | Simple, declarative, standard in Java teams | Gradle: more flexible, more to learn |
| Java 21 LTS | Long-term support, widest library support | Java 25 |
| Package `io.github.lokiencoded.sayso` | Java packages start with a domain you control; a GitHub account gives you `io.github.<username>` for free | `com.sayso`, which implies owning sayso.com |
| JNativeHook for shortcuts | Java alone can only see keys pressed inside its own windows | Writing native Windows code ourselves |
| Clipboard trick for capture | Works in almost every app with very little code | Windows UI Automation: cleaner, much more work (planned for v0.2) |
| PowerShell + System.Speech for voice | Built into Windows, zero install; Java has no good speech library | FreeTTS: old and robotic; cloud voices: need an API key and cost money (v0.2) |
| Interfaces for capture and speech | Swap implementations later; test with fakes now | Concrete classes everywhere: faster today, painful at v0.2 |

## Concepts to study from this step

- **Maven basics:** `pom.xml`, coordinates (groupId:artifactId:version), dependency scopes (`test`), the lifecycle (`compile`, `test`, `package`), and why the shade plugin makes one runnable jar.
- **Interfaces and dependency injection:** read `SaysoApp`, then `CommandLoopTest`. Notice the test never touches a keyboard or speaker.
- **Producer/consumer with a `BlockingQueue`:** the hotkey thread produces commands, the loop consumes them.
- **`ProcessBuilder`:** how Java runs another program, writes to its stdin, and kills it (`SpeechEngine.stop()`).
- **Java's AWT `Robot` and `Clipboard`:** simulating key presses and borrowing the clipboard politely (`ClipboardSnapshot`).

## Exercises for you

1. Add the Maven wrapper so anyone can build without installing Maven: run `mvn wrapper:wrapper` in the project folder, then commit `mvnw`, `mvnw.cmd` and `.mvn/`.
2. Add a shortcut, Ctrl+Alt+D, that resets speed to the default. You'll touch `Command`, `KeyBindings`, `CommandLoop`, and add a test. Commit it on a branch and open a pull request to yourself.
3. Run the Stage 1 checkpoint test below and write the results in `docs/devlog/002-checkpoint-results.md`.

## Stage 1 checkpoint

Use it for a week, and test it in five daily apps:

- [ ] Chrome or Edge (an article)
- [ ] A PDF
- [ ] WhatsApp or Telegram desktop
- [ ] Word or Google Docs
- [ ] VS Code, IntelliJ or another app of your choice
