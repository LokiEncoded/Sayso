package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import io.github.lokiencoded.sayso.core.Command;

import java.util.Map;
import java.util.Optional;

/** Which key, pressed together with Ctrl+Alt, triggers which command. */
public final class KeyBindings {

    private static final Map<Integer, Command> BINDINGS = Map.of(
            NativeKeyEvent.VC_R, Command.READ_SELECTION,
            NativeKeyEvent.VC_P, Command.READ_PAGE,
            // Not S: Ctrl+Alt+S is taken by HP laptops (System Information) and IntelliJ (Settings).
            NativeKeyEvent.VC_X, Command.STOP,
            NativeKeyEvent.VC_PERIOD, Command.FASTER,
            NativeKeyEvent.VC_COMMA, Command.SLOWER,
            NativeKeyEvent.VC_V, Command.NEXT_VOICE,
            NativeKeyEvent.VC_Q, Command.QUIT);

    private KeyBindings() {
    }

    /** The command for this key, or empty if the combination isn't one of ours. */
    public static Optional<Command> lookup(int keyCode, boolean ctrlDown, boolean altDown) {
        if (!(ctrlDown && altDown)) {
            return Optional.empty();
        }
        return Optional.ofNullable(BINDINGS.get(keyCode));
    }
}
