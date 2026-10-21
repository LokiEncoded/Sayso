package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import io.github.lokiencoded.sayso.core.Command;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyListenerTest {

    private static final int CTRL_ALT = NativeInputEvent.CTRL_L_MASK | NativeInputEvent.ALT_L_MASK;

    private final List<Command> fired = new ArrayList<>();
    private boolean windowsSaysHeld = true;
    private final HotkeyListener listener =
            new HotkeyListener(fired::add, () -> windowsSaysHeld, () -> windowsSaysHeld);

    @Test
    void firesWhenCtrlAltReallyHeld() {
        listener.onKeyPressed(NativeKeyEvent.VC_S, CTRL_ALT);
        assertEquals(List.of(Command.STOP), fired);
    }

    /** The bug: a stuck "Alt down" made every Ctrl+V switch voice. Windows' real state wins. */
    @Test
    void ignoresShortcutWhenWindowsSaysAltIsNotHeld() {
        windowsSaysHeld = false;
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);
        assertTrue(fired.isEmpty());
    }

    @Test
    void plainCtrlVIsNeverAShortcut() {
        listener.onKeyPressed(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_L_MASK);
        assertTrue(fired.isEmpty());
    }

    @Test
    void holdingAKeyFiresOnce() {
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);   // auto-repeat
        assertEquals(List.of(Command.NEXT_VOICE), fired);
    }

    @Test
    void recoversAfterAMissedKeyUp() {
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);   // fires; its key-up is then "missed"
        windowsSaysHeld = false;
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);   // ignored, and resets repeat tracking
        windowsSaysHeld = true;
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT);   // a real press works again
        assertEquals(List.of(Command.NEXT_VOICE, Command.NEXT_VOICE), fired);
    }
}
