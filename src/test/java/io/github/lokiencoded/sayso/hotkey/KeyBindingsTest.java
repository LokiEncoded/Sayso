package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import io.github.lokiencoded.sayso.core.Command;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingsTest {

    @Test
    void ctrlAltRReadsSelection() {
        assertEquals(Optional.of(Command.READ_SELECTION),
                KeyBindings.lookup(NativeKeyEvent.VC_R, true, true));
    }

    @Test
    void ctrlAltPReadsPage() {
        assertEquals(Optional.of(Command.READ_PAGE),
                KeyBindings.lookup(NativeKeyEvent.VC_P, true, true));
    }

    @Test
    void ignoredWithoutBothModifiers() {
        assertTrue(KeyBindings.lookup(NativeKeyEvent.VC_R, true, false).isEmpty());
        assertTrue(KeyBindings.lookup(NativeKeyEvent.VC_R, false, true).isEmpty());
        assertTrue(KeyBindings.lookup(NativeKeyEvent.VC_R, false, false).isEmpty());
    }

    @Test
    void ctrlAltXStops() {
        assertEquals(Optional.of(Command.STOP), KeyBindings.lookup(NativeKeyEvent.VC_X, true, true));
    }

    /** Ctrl+Alt+S belongs to HP System Information and IntelliJ Settings, so we leave it alone. */
    @Test
    void ctrlAltSIsNotOurs() {
        assertTrue(KeyBindings.lookup(NativeKeyEvent.VC_S, true, true).isEmpty());
    }
}
