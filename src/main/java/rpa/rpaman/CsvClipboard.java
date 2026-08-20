package rpa.rpaman;

import javax.swing.JOptionPane;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

/**
 * Clipboard helpers: builds CSV text, and copies either CSV or plain text.
 * <p>
 * Shared so every "copy" action quotes fields the same way and reports the same
 * confirmation.
 */
public final class CsvClipboard {

    private CsvClipboard() {
    }

    /**
     * Quotes a field only when it needs it, doubling any inner quotes, so the
     * result survives a paste into Excel even with commas in the data.
     */
    public static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    /** Joins one row of already-raw values into an escaped CSV line. */
    public static String row(String... cells) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) line.append(',');
            line.append(escape(cells[i]));
        }
        return line.append('\n').toString();
    }

    /**
     * Copies CSV text, telling the user what was copied or why it failed.
     *
     * @param description e.g. "4 service accounts"
     */
    public static boolean copy(Component parent, String text, String description) {
        return write(parent, text, "Copied " + description + " to the clipboard as CSV.");
    }

    /** Copies plain text — used for labelled detail blocks, not CSV. */
    public static boolean copyPlain(Component parent, String text, String description) {
        return write(parent, text, "Copied " + description + " to the clipboard.");
    }

    private static boolean write(Component parent, String text, String confirmation) {
        if (text == null || text.trim().isEmpty()) {
            JOptionPane.showMessageDialog(parent, "There is nothing to copy.",
                    "Nothing to copy", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text), null);
            JOptionPane.showMessageDialog(parent, confirmation,
                    "Copied", JOptionPane.INFORMATION_MESSAGE);
            return true;
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(parent,
                    "Could not write to the clipboard:\n" + ex.getMessage(),
                    "Copy failed", JOptionPane.ERROR_MESSAGE);
            return false;
        }
    }

    /** Convenience for "N thing" / "N things". */
    public static String plural(int count, String singular) {
        return count + " " + singular + (count == 1 ? "" : "s");
    }
}
