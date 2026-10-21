package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.NativeInputEvent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModifiersTest {

    @Test
    void ctrlOnlyIsNotAlt() {
        int ctrlV = NativeInputEvent.CTRL_L_MASK;
        assertTrue(Modifiers.ctrl(ctrlV));
        assertFalse(Modifiers.alt(ctrlV));   // plain Ctrl+V must never count as Ctrl+Alt+V
    }

    @Test
    void eitherSideCounts() {
        assertTrue(Modifiers.ctrl(NativeInputEvent.CTRL_R_MASK));
        assertTrue(Modifiers.alt(NativeInputEvent.ALT_R_MASK));
    }

    @Test
    void noModifiers() {
        assertFalse(Modifiers.ctrl(0));
        assertFalse(Modifiers.alt(0));
    }
}
