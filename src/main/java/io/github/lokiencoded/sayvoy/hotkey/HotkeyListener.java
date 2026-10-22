package io.github.lokiencoded.sayvoy.hotkey;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.dispatcher.VoidDispatchService;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import io.github.lokiencoded.sayvoy.config.Modifier;
import io.github.lokiencoded.sayvoy.core.Command;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.Map;
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
    /** Keys whose shortcut has fired and not yet been let go, with the command they fired. */
    private final Map<Integer, Command> firedKeys = new ConcurrentHashMap<>();

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

        // Run listeners on the hook thread itself (not a separate dispatch thread). Only then can a
        // listener swallow a key before Windows delivers it to the app; see consume().
        GlobalScreen.setEventDispatcher(new VoidDispatchService());
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
        if (onKeyPressed(event.getKeyCode(), event.getModifiers())) {
            consume(event);
        }
    }

    @Override
    public void nativeKeyReleased(NativeKeyEvent event) {
        if (onKeyReleased(event.getKeyCode())) {
            consume(event);
        }
    }

    /**
     * Stops a key from reaching the app. Without this, holding Ctrl+Alt+D in a text box types
     * "ḍḍḍḍ" (on Windows, Ctrl+Alt acts as AltGr). JNativeHook supports this on Windows through
     * its private "reserved" flag, read back by the native hook after the listener returns.
     */
    private static void consume(NativeKeyEvent event) {
        if (RESERVED == null) {
            return;
        }
        try {
            RESERVED.setShort(event, (short) 0x01);
        } catch (IllegalAccessException e) {
            log.debug("Could not swallow a shortcut key", e);
        }
    }

    private static final Field RESERVED = reservedField();

    private static Field reservedField() {
        try {
            Field field = NativeInputEvent.class.getDeclaredField("reserved");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            log.warn("This JNativeHook version can't swallow keys; shortcuts may also type into apps");
            return null;
        }
    }

    @Override
    public void nativeKeyTyped(NativeKeyEvent event) {
        // Not needed: we only care about presses and releases.
    }

    /**
     * The decision logic, separate from JNativeHook's event class so it can be unit tested.
     *
     * @return true if this key press belongs to one of our shortcuts and must not reach the app
     */
    boolean onKeyPressed(int keyCode, int modifierMask) {
        Optional<Command> command = bindings.lookup(keyCode, Modifier.fromMask(modifierMask));
        if (command.isEmpty()) {
            return false;
        }
        // The event says this is one of our shortcuts. Double-check with Windows, because
        // event-based modifier tracking can get stuck after a missed key-up.
        if (bindings.lookup(keyCode, reallyHeld.get()).isEmpty()) {
            releaseAll();
            log.debug("Ignored {}: Windows reports different modifier keys held", command.get());
            return false;
        }
        // Holding a key repeats "pressed" events; act only on the first one (but swallow them all).
        if (firedKeys.putIfAbsent(keyCode, command.get()) == null) {
            sink.accept(command.get());
        }
        return true;
    }

    /** A key-up was missed somewhere: forget held keys, and end anything being said. */
    private void releaseAll() {
        for (Integer key : firedKeys.keySet()) {
            onKeyReleased(key);
        }
    }

    /**
     * Letting go of a hold-to-talk key (Ask, Reply) ends what was being said.
     *
     * @return true if this was the key of one of our shortcuts (so its release is swallowed too)
     */
    boolean onKeyReleased(int keyCode) {
        Command released = firedKeys.remove(keyCode);
        if (released != null && released.whenReleased() != null) {
            sink.accept(released.whenReleased());
        }
        return released != null;
    }
}
