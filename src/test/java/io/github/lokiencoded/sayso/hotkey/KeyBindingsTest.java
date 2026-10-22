package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import io.github.lokiencoded.sayso.config.Modifier;
import io.github.lokiencoded.sayso.config.Shortcut;
import io.github.lokiencoded.sayso.core.Command;
import io.github.lokiencoded.sayso.core.Settings;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingsTest {

    private static final Set<Modifier> CTRL_ALT = EnumSet.of(Modifier.CTRL, Modifier.ALT);

    private final KeyBindings defaults = new KeyBindings(new Settings().shortcuts());

    @Test
    void defaultShortcuts() {
        assertEquals(Optional.of(Command.READ_SELECTION), defaults.lookup(NativeKeyEvent.VC_R, CTRL_ALT));
        assertEquals(Optional.of(Command.READ_PAGE), defaults.lookup(NativeKeyEvent.VC_P, CTRL_ALT));
        assertEquals(Optional.of(Command.STOP), defaults.lookup(NativeKeyEvent.VC_X, CTRL_ALT));
    }

    @Test
    void ignoredWithoutBothModifiers() {
        assertTrue(defaults.lookup(NativeKeyEvent.VC_R, EnumSet.of(Modifier.CTRL)).isEmpty());
        assertTrue(defaults.lookup(NativeKeyEvent.VC_R, EnumSet.noneOf(Modifier.class)).isEmpty());
    }

    /** Ctrl+Alt+S belongs to HP System Information and IntelliJ Settings, so the defaults leave it alone. */
    @Test
    void ctrlAltSIsNotOurs() {
        assertTrue(defaults.lookup(NativeKeyEvent.VC_S, CTRL_ALT).isEmpty());
    }

    @Test
    void usesShortcutsFromSettings() {
        Settings settings = new Settings();
        settings.setShortcut(Command.STOP, Shortcut.parse("ctrl+shift+F12").orElseThrow());
        KeyBindings custom = new KeyBindings(settings.shortcuts());

        assertEquals(Optional.of(Command.STOP),
                custom.lookup(NativeKeyEvent.VC_F12, EnumSet.of(Modifier.CTRL, Modifier.SHIFT)));
        assertTrue(custom.lookup(NativeKeyEvent.VC_X, CTRL_ALT).isEmpty());
    }
}
