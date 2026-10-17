package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import io.github.lokiencoded.sayso.core.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Listens to the keyboard system-wide (in every app, not just ours) and turns
 * our shortcuts into Commands. It never does the work itself: it only hands
 * the command to a sink (the queue), so key handling stays instant.
 */
public final class HotkeyListener implements NativeKeyListener {

    private static final Logger log = LoggerFactory.getLogger(HotkeyListener.class);

    private final Consumer<Command> sink;
    private final Set<Integer> keysDown = ConcurrentHashMap.newKeySet();
    private volatile boolean ctrlDown;
    private volatile boolean altDown;

    public HotkeyListener(Consumer<Command> sink) {
        this.sink = sink;
    }

    public void start() throws NativeHookException {
        // JNativeHook logs every key press by default; keep only its warnings.
        java.util.logging.Logger hookLogger =
                java.util.logging.Logger.getLogger(GlobalScreen.class.getPackage().getName());
        hookLogger.setLevel(Level.WARNING);
        hookLogger.setUseParentHandlers(false);

        GlobalScreen.registerNativeHook();
        GlobalScreen.addNativeKeyListener(this);
        log.info("Keyboard hook registered");
    }

    public void stop() {
        GlobalScreen.removeNativeKeyListener(this);
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (NativeHookException e) {
            log.warn("Could not unregister keyboard hook", e);
        }
    }

    /** True while the user is still holding Ctrl or Alt. */
    public boolean modifiersHeld() {
        return ctrlDown || altDown;
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        int code = event.getKeyCode();
        updateModifier(code, true);
        // Holding a key repeats "pressed" events; act only on the first one.
        if (!keysDown.add(code)) {
            return;
        }
        KeyBindings.lookup(code, ctrlDown, altDown).ifPresent(sink);
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        int code = event.getKeyCode();
        updateModifier(code, false);
        keysDown.remove(code);
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent event) {
        // Not needed: we only care about presses and releases.
    }

    private void updateModifier(int code, boolean down) {
        if (code == NativeKeyEvent.VC_CONTROL) {
            ctrlDown = down;
        } else if (code == NativeKeyEvent.VC_ALT) {
            altDown = down;
        }
    }
}
