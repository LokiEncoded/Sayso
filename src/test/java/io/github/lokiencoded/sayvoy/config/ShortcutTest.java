package io.github.lokiencoded.sayvoy.config;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShortcutTest {

    @Test
    void parsesModifiersAndKey() {
        Shortcut shortcut = Shortcut.parse("ctrl+alt+R").orElseThrow();
        assertEquals(EnumSet.of(Modifier.CTRL, Modifier.ALT), shortcut.modifiers());
        assertEquals(NativeKeyEvent.VC_R, shortcut.keyCode());
    }

    @Test
    void isForgivingAboutCaseAndSpaces() {
        assertEquals(Shortcut.parse("ctrl+shift+F5"), Shortcut.parse(" Control + SHIFT + f5 "));
    }

    @Test
    void acceptsSymbolsAndNamedKeys() {
        assertEquals(NativeKeyEvent.VC_PERIOD, Shortcut.parse("ctrl+alt+.").orElseThrow().keyCode());
        assertEquals(NativeKeyEvent.VC_PERIOD, Shortcut.parse("ctrl+alt+period").orElseThrow().keyCode());
        assertEquals(NativeKeyEvent.VC_PAGE_UP, Shortcut.parse("win+page up").orElseThrow().keyCode());
    }

    @Test
    void rejectsShortcutsWithoutAModifier() {
        assertEquals(Optional.empty(), Shortcut.parse("R"));
    }

    @Test
    void rejectsUnknownNames() {
        assertEquals(Optional.empty(), Shortcut.parse("ctrl+hyper+R"));
        assertEquals(Optional.empty(), Shortcut.parse("ctrl+alt+NOPE"));
        assertEquals(Optional.empty(), Shortcut.parse("ctrl+alt+shift"));   // a modifier can't be the key
        assertEquals(Optional.empty(), Shortcut.parse(""));
    }

    @Test
    void matchesOnlyTheExactModifiers() {
        Shortcut ctrlAltR = Shortcut.parse("ctrl+alt+R").orElseThrow();
        assertTrue(ctrlAltR.matches(NativeKeyEvent.VC_R, EnumSet.of(Modifier.CTRL, Modifier.ALT)));
        assertFalse(ctrlAltR.matches(NativeKeyEvent.VC_R, EnumSet.of(Modifier.CTRL)));
        assertFalse(ctrlAltR.matches(NativeKeyEvent.VC_R, EnumSet.of(Modifier.CTRL, Modifier.ALT, Modifier.SHIFT)));
        assertFalse(ctrlAltR.matches(NativeKeyEvent.VC_T, EnumSet.of(Modifier.CTRL, Modifier.ALT)));
    }

    @Test
    void roundTripsThroughText() {
        Shortcut original = Shortcut.parse("shift+win+F12").orElseThrow();
        assertEquals(Optional.of(original), Shortcut.parse(original.toSettingsText()));
    }

    @Test
    void labelsAreFriendly() {
        assertEquals("Ctrl+Alt+.", Shortcut.parse("ctrl+alt+PERIOD").orElseThrow().label());
        assertEquals("Ctrl+Alt+R", Shortcut.parse("alt+ctrl+r").orElseThrow().label());
    }
}
