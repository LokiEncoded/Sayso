# 004: Saved and configurable settings

**Date:** 2026-10-22 · **Stage:** 2 (MVP) · Finishes v0.2

## What we built

`%USERPROFILE%\.tellsy\settings.properties` holds the voice, the speed and every shortcut. Tellsy creates it with comments on first run, saves voice and speed whenever you change them, and reads your shortcut edits at startup.

## Decisions and why

| Decision | Why | Alternative we skipped |
| --- | --- | --- |
| A `.properties` file | Built into Java, easy to edit in Notepad, no new library | JSON or YAML: needs a library and is easier to break by hand |
| We write the file ourselves, in a fixed order with comments | `Properties.store()` shuffles keys and drops comments, which is hostile to people editing it | `Properties.store()` |
| Write to a temp file, then move it into place | A crash mid-save can never leave a half-written settings file | Writing in place |
| Bad values fall back to defaults and are logged | A typo should never stop the app from starting | Refusing to start |
| Shortcuts must include a modifier | A plain letter as a global shortcut would hijack typing everywhere | Allowing any key |
| Exact modifier match | Ctrl+Alt+Shift+R must not also trigger Ctrl+Alt+R | Matching if at least the listed modifiers are held |
| Key names come from JNativeHook's own constants (via reflection) | Every key it supports works without us typing a 130-entry table | A hand-written table |

## A bug the tests caught before you did

`List.of("a", "b").indexOf(null)` throws a `NullPointerException`: Java's immutable lists reject null even in lookups. With no voice saved yet, the first Ctrl+Alt+V would have crashed. `SettingsTest.nextVoiceStartsAtTheFirstAndWraps` found it on the first run.

## Concepts to study from this step

- **Reflection:** `Keys` reads every `VC_*` constant from `NativeKeyEvent` at startup. Powerful, but use it sparingly: the compiler can't check it.
- **Enums with data:** `Command` carries its description and settings key; `Modifier` carries its JNativeHook mask.
- **`EnumSet` and `EnumMap`:** fast, ordered sets and maps for enum keys. The modifier set compares with `equals`.
- **Atomic file writes:** why temp-then-move is the standard way to save a file safely.
- **Validation that reports instead of failing:** `SettingsFile.apply()` returns a list of problems rather than throwing.

## Exercises for you

1. Run Tellsy once, open your `settings.properties`, and change Stop to a shortcut you like. Restart and try it.
2. Put a typo in one shortcut and watch the console warning.
3. Add a new setting, `startup_message=true|false`, that controls whether the shortcut list prints at startup. You'll touch `Settings`, `SettingsFile` and `TellsyApp`, and should add a test.
