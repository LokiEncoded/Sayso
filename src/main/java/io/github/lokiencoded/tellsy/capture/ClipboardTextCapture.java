package io.github.lokiencoded.tellsy.capture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.AWTException;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.event.KeyEvent;
import java.io.IOException;
import java.util.function.BooleanSupplier;

/**
 * The "clipboard trick": press Ctrl+C for the user, read what landed on the
 * clipboard, then put back whatever they had copied before. Works in almost
 * every app, which makes it the right first step.
 */
public final class ClipboardTextCapture implements TextCapture, TextOutput {

    private static final Logger log = LoggerFactory.getLogger(ClipboardTextCapture.class);

    private static final String MARKER = "__tellsy_nothing_copied__";
    private static final long MODIFIER_WAIT_MS = 1_000;
    private static final long COPY_WAIT_MS = 600;
    private static final long POLL_MS = 20;
    private static final long PASTE_SETTLE_MS = 500;

    private final BooleanSupplier modifiersHeld;
    private final Robot robot;
    private final Clipboard clipboard;

    /**
     * @param modifiersHeld tells us whether the user is still holding Ctrl/Alt
     *                      from the hotkey; we wait for them to let go first.
     */
    public ClipboardTextCapture(BooleanSupplier modifiersHeld) throws AWTException {
        this.modifiersHeld = modifiersHeld;
        this.robot = new Robot();
        this.robot.setAutoDelay(10);
        this.clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    @Override
    public String captureSelection() {
        return capture(false).text();
    }

    @Override
    public CapturedText captureAll() {
        return capture(true);
    }

    /**
     * Types text into the focused app the way Wispr Flow does: put it on the clipboard, press
     * Ctrl+V, then put back whatever the user had copied. Much faster and more reliable than
     * simulating each keystroke, and it handles any language or emoji.
     */
    @Override
    public synchronized void paste(String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        // Same reason as capture(): Ctrl+V pressed while Ctrl+Alt are held would arrive as
        // Ctrl+Alt+V, which is Tellsy's own "next voice" shortcut.
        waitUntil(() -> !modifiersHeld.getAsBoolean(), MODIFIER_WAIT_MS);
        ClipboardSnapshot saved = ClipboardSnapshot.take(clipboard);
        setClipboard(new StringSelection(text));
        sleep(30);
        pressCtrlAnd(KeyEvent.VK_V);
        // Some apps (Electron ones like WhatsApp and Slack) read the clipboard a moment after
        // the keystroke, so wait before putting the user's own clipboard back.
        sleep(PASTE_SETTLE_MS);
        saved.restore(this::setClipboard);
    }

    private synchronized CapturedText capture(boolean selectAll) {
        // The hotkey fires while Ctrl+Alt are still down. If we pressed Ctrl+C now,
        // the app would receive Ctrl+Alt+C. So wait until the user lets go.
        waitUntil(() -> !modifiersHeld.getAsBoolean(), MODIFIER_WAIT_MS);

        ClipboardSnapshot saved = ClipboardSnapshot.take(clipboard);
        try {
            setClipboard(new StringSelection(MARKER));
            if (selectAll) {
                pressCtrlAnd(KeyEvent.VK_A);
                sleep(50);
            }
            pressCtrlAnd(KeyEvent.VK_C);
            String text = waitForCopiedText().strip();
            // Read the HTML version while the copy is still on the clipboard (browsers provide one).
            return text.isEmpty() ? CapturedText.EMPTY : new CapturedText(text, readHtml());
        } finally {
            saved.restore(this::setClipboard);
        }
    }

    /** The HTML version of what was copied, or null if the app didn't provide one. */
    private String readHtml() {
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.allHtmlFlavor)) {
                return (String) clipboard.getData(DataFlavor.allHtmlFlavor);
            }
        } catch (IllegalStateException | UnsupportedFlavorException | IOException | ClassCastException e) {
            log.debug("Could not read clipboard HTML", e);
        }
        return null;
    }

    private String waitForCopiedText() {
        long deadline = System.currentTimeMillis() + COPY_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            sleep(POLL_MS);
            String current = readText();
            if (current != null && !MARKER.equals(current)) {
                return current;
            }
        }
        log.debug("Nothing was copied within {} ms", COPY_WAIT_MS);
        return "";
    }

    private String readText() {
        try {
            if (clipboard.isDataFlavorAvailable(DataFlavor.stringFlavor)) {
                return (String) clipboard.getData(DataFlavor.stringFlavor);
            }
        } catch (IllegalStateException e) {
            // Another app is using the clipboard right now; try again on the next poll.
        } catch (UnsupportedFlavorException | IOException e) {
            log.debug("Could not read clipboard text", e);
        }
        return null;
    }

    private void setClipboard(Transferable contents) {
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                clipboard.setContents(contents, null);
                return;
            } catch (IllegalStateException busy) {
                sleep(POLL_MS);
            }
        }
        log.warn("Clipboard stayed busy; could not update it");
    }

    private void pressCtrlAnd(int keyCode) {
        robot.keyPress(KeyEvent.VK_CONTROL);
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
        robot.keyRelease(KeyEvent.VK_CONTROL);
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < deadline) {
            sleep(POLL_MS);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
