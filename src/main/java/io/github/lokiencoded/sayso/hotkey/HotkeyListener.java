package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import io.github.lokiencoded.sayso.core.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;
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
    private final BooleanSupplier ctrlAndAltReallyDown;
    private final BooleanSupplier ctrlOrAltReallyDown;
    private final Set<Integer> firedKeys = ConcurrentHashMap.newKeySet();

    public HotkeyListener(Consumer<Command> sink) {
        this(sink, Modifiers::windowsCtrlAndAltDown, Modifiers::windowsCtrlOrAltDown);
    }

    /** For tests: lets us fake what Windows reports about Ctrl and Alt. */
    HotkeyListener(Consumer<Command> sink, BooleanSupplier ctrlAndAltReallyDown,
                   BooleanSupplier ctrlOrAltReallyDown) {
        this.sink = sink;
        this.ctrlAndAltReallyDown = ctrlAndAltReallyDown;
        this.ctrlOrAltReallyDown = ctrlOrAltReallyDown;
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

    /** True while the user is physically holding Ctrl or Alt (asked from Windows, never guessed). */
    public boolean modifiersHeld() {
        return ctrlOrAltReallyDown.getAsBoolean();
    }

    @Override
    public void nativeKeyPressed(NativeKeyEvent event) {
        onKeyPressed(event.getKeyCode(), event.getModifiers());
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        firedKeys.remove(event.getKeyCode());
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent event) {
        // Not needed: we only care about presses and releases.
    }

    /** The decision logic, separate from JNativeHook's event class so it can be unit tested. */
    void onKeyPressed(int keyCode, int modifierMask) {
        Optional<Command> command = KeyBindings.lookup(
                keyCode, Modifiers.ctrl(modifierMask), Modifiers.alt(modifierMask));
        if (command.isEmpty()) {
            return;
        }
        // The event says Ctrl+Alt+key. Double-check with Windows, because event-based
        // modifier tracking can get stuck after a missed key-up.
        if (!ctrlAndAltReallyDown.getAsBoolean()) {
            firedKeys.clear();
            log.debug("Ignored {}: Windows reports Ctrl+Alt are not both held", command.get());
            return;
        }
        // Holding a key repeats "pressed" events; act only on the first one.
        if (firedKeys.add(keyCode)) {
            sink.accept(command.get());
        }
    }
}
