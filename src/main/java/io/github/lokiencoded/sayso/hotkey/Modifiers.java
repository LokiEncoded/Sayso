package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.sun.jna.platform.win32.User32;

/**
 * Answers "are Ctrl and Alt held right now?".
 *
 * <p>Key events alone are not enough: if a key-up is missed (a focus change, a Windows
 * security prompt), a tracked "Alt is down" flag stays stuck and plain Ctrl+V starts
 * acting like Ctrl+Alt+V. So before firing a shortcut we ask Windows directly.
 */
final class Modifiers {

    private static final int VK_CONTROL = 0x11;
    private static final int VK_MENU = 0x12;   // Windows calls Alt "Menu"
    private static final int KEY_DOWN_BIT = 0x8000;

    private Modifiers() {
    }

    static boolean ctrl(int mask) {
        return (mask & NativeInputEvent.CTRL_MASK) != 0;
    }

    static boolean alt(int mask) {
        return (mask & NativeInputEvent.ALT_MASK) != 0;
    }

    /** Asks Windows for the physical state of Ctrl and Alt (either side). */
    static boolean windowsCtrlAndAltDown() {
        return isDown(VK_CONTROL) && isDown(VK_MENU);
    }

    /** Asks Windows whether Ctrl or Alt is physically held. */
    static boolean windowsCtrlOrAltDown() {
        return isDown(VK_CONTROL) || isDown(VK_MENU);
    }

    private static boolean isDown(int virtualKey) {
        return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & KEY_DOWN_BIT) != 0;
    }
}
