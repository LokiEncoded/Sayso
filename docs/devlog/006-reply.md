# 006: Reply, closing the loop

**Date:** 2026-10-22 · **Stage:** 3 (Close the loop)

## What we built

Click into a text box, hold **Ctrl+Alt+D**, speak, let go. Sayvoy sends your speech to Gemini with instructions to clean it up (filler words, false starts, grammar) without changing your meaning or tone, then pastes the result where your cursor is.

This is the feature the whole project was aiming at:

```
someone's message ─► Ctrl+Alt+R: Sayvoy reads it to you
                 ─► Ctrl+Alt+D: you say your reply ─► Sayvoy writes it, using that message as context
```

Wispr Flow does the second half. Sayvoy does both, and the reply knows what you're replying to.

## Decisions and why

| Decision | Why | Alternative we skipped |
| --- | --- | --- |
| Paste through the clipboard, then restore it | Instant for any length, works with every language and emoji, and is what Wispr Flow does | Simulating each keystroke: slow, and breaks on non-English characters |
| Wait 500 ms before restoring the clipboard | Electron apps (WhatsApp, Slack, Discord) read the clipboard a moment after Ctrl+V | Restoring at once: those apps would paste your old clipboard instead |
| Use what Sayvoy last read as context, for 10 minutes | Names get spelled right and "Thursday" means the right Thursday; forgetting it later stops an old message leaking into an unrelated reply | No context (plain dictation) |
| A separate prompt that forbids answering | Without it, "can you send me the report?" gets an answer instead of being written down | One prompt for Ask and Reply |
| One `VoiceController` for Ask and Reply | They're the same until Gemini replies; only the ending differs (speak or paste) | Two near-identical classes |
| Ctrl+Alt+D (D for dictate) | Free in common apps, easy to hold with one hand | Ctrl+Alt+Space: clashes with input-method switching on some machines |

## Concepts to study from this step

- **Prompt design for transcription:** read `REPLY_INSTRUCTIONS`. Each rule fixes a specific failure mode (answering questions, adding sign-offs, being "helpfully" formal).
- **Enums with behaviour:** `Command.whenReleased()` maps hold-to-talk commands to their release events, so `HotkeyListener` no longer special-cases Ask.
- **Refactoring with tests as a safety net:** `AskController` became `VoiceController`. The existing Ask tests kept passing throughout, which is how we know the rename broke nothing.
- **Testing time:** `RecentTextTest` uses a fake `Clock` to jump ten minutes ahead instantly.

## Exercises for you

1. Have someone message you on WhatsApp Web. Read it with Ctrl+Alt+R, then reply with Ctrl+Alt+D.
2. Try a messy reply on purpose: "um so yeah I think uh we should, no actually let's not". Did it keep only what you meant?
3. Add a setting `reply.style` (for example "casual" or "formal") and pass it into the reply instructions.
