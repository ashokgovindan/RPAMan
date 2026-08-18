package rpa.rpaman;

import javax.swing.SwingUtilities;

/**
 * Application entry point.
 * <p>
 * The look and feel is installed before any Swing component is created so the
 * whole UI picks up the saved theme on the first paint.
 */
public class Main {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.uiScale.enabled", "true");

        DatabaseManager dbManager = new DatabaseManager();
        ThemeManager.init(dbManager);

        SwingUtilities.invokeLater(() -> new RPAProjectManager(dbManager).setVisible(true));
    }
}
