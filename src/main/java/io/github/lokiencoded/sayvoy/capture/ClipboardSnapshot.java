package io.github.lokiencoded.sayvoy.capture;

import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.util.List;
import java.util.function.Consumer;

/**
 * A copy of what was on the clipboard, taken before we borrow it.
 * Supports text, images and copied files; anything else is not restored.
 * The data is copied eagerly because the original app may stop serving it
 * once we replace the clipboard contents.
 */
record ClipboardSnapshot(DataFlavor flavor, Object data) {

    private static final List<DataFlavor> SUPPORTED = List.of(
            DataFlavor.stringFlavor,
            DataFlavor.imageFlavor,
            DataFlavor.javaFileListFlavor);

    private static final ClipboardSnapshot EMPTY = new ClipboardSnapshot(null, null);

    static ClipboardSnapshot take(Clipboard clipboard) {
        for (DataFlavor flavor : SUPPORTED) {
            try {
                if (clipboard.isDataFlavorAvailable(flavor)) {
                    return new ClipboardSnapshot(flavor, clipboard.getData(flavor));
                }
            } catch (Exception e) {
                // Busy or unreadable: try the next flavor.
            }
        }
        return EMPTY;
    }

    boolean isEmpty() {
        return flavor == null;
    }

    void restore(Consumer<Transferable> setter) {
        if (!isEmpty()) {
            setter.accept(new SingleFlavorTransferable(flavor, data));
        }
    }

    /** Puts one piece of data of one type back on the clipboard. */
    record SingleFlavorTransferable(DataFlavor flavor, Object data) implements Transferable {

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{flavor};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor candidate) {
            return flavor.equals(candidate);
        }

        @Override
        public Object getTransferData(DataFlavor requested) throws UnsupportedFlavorException {
            if (!isDataFlavorSupported(requested)) {
                throw new UnsupportedFlavorException(requested);
            }
            return data;
        }
    }
}
