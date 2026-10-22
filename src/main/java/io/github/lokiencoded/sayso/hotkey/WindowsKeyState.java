package io.github.lokiencoded.sayso.hotkey;

import com.sun.jna.platform.win32.User32;
import io.github.lokiencoded.sayso.config.Modifier;

import java.util.EnumSet;
import java.util.Set;

/**
 * Asks Windows which modifier keys are physically held right now.
 *
 * <p>Key events alone are not enough: if a key-up is missed (a focus change, a Windows
 * security prompt), a tracked "Alt is down" flag stays stuck and plain Ctrl+V starts
 * acting like Ctrl+Alt+V. So before firing a shortcut we ask Windows directly.
 */
final class WindowsKeyState {

    private static final int KEY_DOWN_BIT = 0x8000;
    private static final int VK_SHIFT = 0x10;
    private static final int VK_CONTROL = 0x11;
    private static final int VK_MENU = 0x12;   // Windows calls Alt "Menu"
    private static final int VK_LWIN = 0x5B;
    private static final int VK_RWIN = 0x5C;

    private WindowsKeyState() {
    }

    static Set<Modifier> heldModifiers() {
        Set<Modifier> held = EnumSet.noneOf(Modifier.class);
        if (isDown(VK_CONTROL)) {
            held.add(Modifier.CTRL);
        }
        if (isDown(VK_MENU)) {
            held.add(Modifier.ALT);
        }
        if (isDown(VK_SHIFT)) {
            held.add(Modifier.SHIFT);
        }
        if (isDown(VK_LWIN) || isDown(VK_RWIN)) {
            held.add(Modifier.WIN);
        }
        return held;
    }

    private static boolean isDown(int virtualKey) {
        return (User32.INSTANCE.GetAsyncKeyState(virtualKey) & KEY_DOWN_BIT) != 0;
    }
}
