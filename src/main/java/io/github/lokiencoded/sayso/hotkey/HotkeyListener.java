package io.github.lokiencoded.sayso.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import io.github.lokiencoded.sayso.config.Modifier;
import io.github.lokiencoded.sayso.core.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * Listens to the keyboard system-wide (in every app, not just ours) and turns
 * our shortcuts into Commands. It never does the work itself: it only hands
 * the command to a sink (the queue), so key handling stays instant.
 */
public final class HotkeyListener implements NativeKeyListener {

    private static final Logger log = LoggerFactory.getLogger(HotkeyListener.class);

    private final KeyBindings bindings;
    private final Consumer<Command> sink;
    private final Supplier<Set<Modifier>> reallyHeld;
    private final Set<Integer> firedKeys = ConcurrentHashMap.newKeySet();

    public HotkeyListener(KeyBindings bindings, Consumer<Command> sink) {
        this(bindings, sink, WindowsKeyState::heldModifiers);
    }

    /** For tests: lets us fake what Windows reports about the modifier keys. */
    HotkeyListener(KeyBindings bindings, Consumer<Command> sink, Supplier<Set<Modifier>> reallyHeld) {
        this.bindings = bindings;
        this.sink = sink;
        this.reallyHeld = reallyHeld;
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

    /** True while the user is physically holding any modifier key (asked from Windows, never guessed). */
    public boolean modifiersHeld() {
        return !reallyHeld.get().isEmpty();
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
        Optional<Command> command = bindings.lookup(keyCode, Modifier.fromMask(modifierMask));
        if (command.isEmpty()) {
            return;
        }
        // The event says this is one of our shortcuts. Double-check with Windows, because
        // event-based modifier tracking can get stuck after a missed key-up.
        if (bindings.lookup(keyCode, reallyHeld.get()).isEmpty()) {
            firedKeys.clear();
            log.debug("Ignored {}: Windows reports different modifier keys held", command.get());
            return;
        }
        // Holding a key repeats "pressed" events; act only on the first one.
        if (firedKeys.add(keyCode)) {
            sink.accept(command.get());
        }
    }
}
