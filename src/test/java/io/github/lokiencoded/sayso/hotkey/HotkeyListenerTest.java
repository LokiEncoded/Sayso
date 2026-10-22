package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import io.github.lokiencoded.sayso.config.Modifier;
import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.Settings;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotkeyListenerTest {

    private static final int CTRL_ALT_MASK = NativeInputEvent.CTRL_L_MASK | NativeInputEvent.ALT_L_MASK;

    private final List<Command> fired = new ArrayList<>();
    private Set<Modifier> windowsSaysHeld = EnumSet.of(Modifier.CTRL, Modifier.ALT);
    private final HotkeyListener listener = new HotkeyListener(
            new KeyBindings(new Settings().shortcuts()), fired::add, () -> windowsSaysHeld);

    @Test
    void firesWhenTheShortcutIsReallyHeld() {
        listener.onKeyPressed(NativeKeyEvent.VC_X, CTRL_ALT_MASK);
        assertEquals(List.of(Command.STOP), fired);
    }

    /** The bug: a stuck "Alt down" made every Ctrl+V switch voice. Windows' real state wins. */
    @Test
    void ignoresShortcutWhenWindowsSaysAltIsNotHeld() {
        windowsSaysHeld = EnumSet.of(Modifier.CTRL);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);
        assertTrue(fired.isEmpty());
    }

    @Test
    void plainCtrlVIsNeverAShortcut() {
        windowsSaysHeld = EnumSet.of(Modifier.CTRL);
        listener.onKeyPressed(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_L_MASK);
        assertTrue(fired.isEmpty());
    }

    @Test
    void lockKeysDoNotGetInTheWay() {
        listener.onKeyPressed(NativeKeyEvent.VC_X, CTRL_ALT_MASK | NativeInputEvent.NUM_LOCK_MASK);
        assertEquals(List.of(Command.STOP), fired);
    }

    @Test
    void holdingAKeyFiresOnce() {
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);   // auto-repeat
        assertEquals(List.of(Command.NEXT_VOICE), fired);
    }

    @Test
    void recoversAfterAMissedKeyUp() {
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);   // fires; its key-up is then "missed"
        windowsSaysHeld = EnumSet.noneOf(Modifier.class);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);   // ignored, and resets repeat tracking
        windowsSaysHeld = EnumSet.of(Modifier.CTRL, Modifier.ALT);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);   // a real press works again
        assertEquals(List.of(Command.NEXT_VOICE, Command.NEXT_VOICE), fired);
    }

    @Test
    void lettingGoOfAskEndsTheQuestion() {
        listener.onKeyPressed(NativeKeyEvent.VC_A, CTRL_ALT_MASK);
        listener.onKeyPressed(NativeKeyEvent.VC_A, CTRL_ALT_MASK);   // held: auto-repeat
        listener.onKeyReleased(NativeKeyEvent.VC_A);
        assertEquals(List.of(Command.ASK, Command.ASK_RELEASED), fired);
    }

    @Test
    void lettingGoOfReplyEndsTheReply() {
        listener.onKeyPressed(NativeKeyEvent.VC_D, CTRL_ALT_MASK);
        listener.onKeyReleased(NativeKeyEvent.VC_D);
        assertEquals(List.of(Command.REPLY, Command.REPLY_RELEASED), fired);
    }

    @Test
    void lettingGoOfOtherShortcutsSendsNothingExtra() {
        listener.onKeyPressed(NativeKeyEvent.VC_R, CTRL_ALT_MASK);
        listener.onKeyReleased(NativeKeyEvent.VC_R);
        assertEquals(List.of(Command.READ_SELECTION), fired);
    }

    @Test
    void aMissedKeyUpStillEndsTheQuestion() {
        listener.onKeyPressed(NativeKeyEvent.VC_A, CTRL_ALT_MASK);   // Ask starts; its key-up is "missed"
        windowsSaysHeld = EnumSet.noneOf(Modifier.class);
        listener.onKeyPressed(NativeKeyEvent.VC_V, CTRL_ALT_MASK);   // stale event triggers the reset
        assertEquals(List.of(Command.ASK, Command.ASK_RELEASED), fired);
    }

    @Test
    void modifiersHeldAsksWindows() {
        assertTrue(listener.modifiersHeld());
        windowsSaysHeld = EnumSet.noneOf(Modifier.class);
        assertFalse(listener.modifiersHeld());
    }
}
