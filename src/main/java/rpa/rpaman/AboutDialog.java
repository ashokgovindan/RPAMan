package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * About page: a painted hero banner over cards summarising what is stored and
 * what the app is running on.
 * <p>
 * The environment block doubles as a support snippet — "Copy Details" puts the
 * whole thing on the clipboard.
 */
public class AboutDialog extends JDialog {

    private final DatabaseManager dbManager;

    public AboutDialog(Frame owner, DatabaseManager dbManager) {
        super(owner, "About " + AppInfo.NAME, true);
        this.dbManager = dbManager;

        setSize(660, 720);
        setMinimumSize(new Dimension(520, 560));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));
        root.setBorder(new EmptyBorder(0, 0, UiFactory.GAP, 0));

        root.add(new HeroBanner(), BorderLayout.NORTH);

        JPanel content = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        content.setBorder(new EmptyBorder(UiFactory.GAP, UiFactory.GAP, 0, UiFactory.GAP));
        content.add(buildStatsCard(), BorderLayout.NORTH);

        JPanel lower = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        lower.add(buildEnvironmentCard(), BorderLayout.NORTH);
        lower.add(buildCreditsCard(), BorderLayout.CENTER);
        content.add(lower, BorderLayout.CENTER);

        root.add(UiFactory.formScroll(content), BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton copyBtn = UiFactory.secondary("Copy Details",
                AppIcons.clipboard(15, "App.subtleForeground"));
        copyBtn.setToolTipText("Copy version and environment details to the clipboard");
        copyBtn.addActionListener(e -> copyDetails());

        JButton closeBtn = UiFactory.primary("Close", null);
        closeBtn.addActionListener(e -> dispose());

        JPanel footer = UiFactory.transparent(new BorderLayout(8, 0));
        footer.setBorder(new EmptyBorder(0, UiFactory.GAP, 0, UiFactory.GAP));
        footer.add(copyBtn, BorderLayout.WEST);
        JPanel closeHolder = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeHolder.add(closeBtn);
        footer.add(closeHolder, BorderLayout.CENTER);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(closeBtn);
        getRootPane().registerKeyboardAction(e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    // ------------------------------------------------------------------ cards

    private JPanel buildStatsCard() {
        JPanel card = UiFactory.card(new GridBagLayout(), 18);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 0);
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int y = 0;
        gbc.gridy = y++;
        gbc.insets = new Insets(0, 0, 12, 0);
        card.add(UiFactory.sectionTitle("At a glance", AppIcons.grid(16, "App.accent")), gbc);
        gbc.insets = new Insets(5, 0, 5, 0);

        Map<String, Integer> counts = dbManager.getRecordCounts();
        gbc.gridwidth = 1;
        int column = 0;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            JPanel tile = statTile(String.valueOf(entry.getValue()), entry.getKey());
            gbc.gridx = column;
            gbc.gridy = y;
            gbc.insets = new Insets(5, column == 0 ? 0 : 10, 5, 0);
            card.add(tile, gbc);

            column++;
            if (column == 2) {
                column = 0;
                y++;
            }
        }
        return card;
    }

    /** A big number over its label, used for the record counts. */
    private JPanel statTile(String value, String label) {
        JPanel tile = new JPanel(new BorderLayout(0, 2)) {
            @Override
            protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    g.setColor(ThemeManager.color("App.stripe", ThemeManager.card()));
                    g.fill(new RoundRectangle2D.Double(0, 0, getWidth(), getHeight(), 10, 10));
                } finally {
                    g.dispose();
                }
                super.paintComponent(graphics);
            }
        };
        tile.setOpaque(false);
        tile.setBorder(new EmptyBorder(10, 14, 10, 14));

        JLabel number = new JLabel(value);
        number.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 20f));
        ThemeManager.styleOnThemeChange(number, l -> l.setForeground(ThemeManager.accent()));

        JLabel caption = new JLabel(label);
        caption.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 12f));
        ThemeManager.styleOnThemeChange(caption, l -> l.setForeground(ThemeManager.subtle()));

        tile.add(number, BorderLayout.NORTH);
        tile.add(caption, BorderLayout.CENTER);
        return tile;
    }

    private JPanel buildEnvironmentCard() {
        JPanel card = UiFactory.card(new GridBagLayout(), 18);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(0, 0, 12, 0);
        gbc.gridy = 0;
        card.add(UiFactory.sectionTitle("Environment", AppIcons.monitor(16, "App.accent")), gbc);

        int y = 1;
        for (Map.Entry<String, String> entry : environment().entrySet()) {
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            gbc.gridx = 0;
            gbc.gridy = y;
            gbc.insets = new Insets(4, 0, 4, 14);
            JLabel key = new JLabel(entry.getKey());
            key.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 12f));
            ThemeManager.styleOnThemeChange(key, l -> l.setForeground(ThemeManager.subtle()));
            card.add(key, gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            gbc.insets = new Insets(4, 0, 4, 0);
            JLabel value = new JLabel(entry.getValue());
            value.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 12f));
            value.setToolTipText(entry.getValue());
            card.add(value, gbc);
            y++;
        }
        return card;
    }

    private JPanel buildCreditsCard() {
        JPanel card = UiFactory.card(new BorderLayout(0, 8), 18);
        card.add(UiFactory.sectionTitle("Built with", AppIcons.layers(16, "App.accent")),
                BorderLayout.NORTH);

        JLabel credits = new JLabel("<html><div style='line-height:150%'>"
                + "Java Swing on the Nimbus look and feel<br>"
                + "SQLite via the Xerial JDBC driver<br>"
                + "Selenium WebDriver for the Control Room reader"
                + "</div></html>");
        credits.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 12f));
        ThemeManager.styleOnThemeChange(credits, l -> l.setForeground(ThemeManager.subtle()));
        card.add(credits, BorderLayout.CENTER);
        return card;
    }

    // ------------------------------------------------------------ environment

    private Map<String, String> environment() {
        Map<String, String> info = new LinkedHashMap<>();
        info.put("Version", AppInfo.VERSION);
        info.put("Java", System.getProperty("java.version", "?")
                + "  (" + System.getProperty("java.vendor", "?") + ")");
        info.put("Operating system", System.getProperty("os.name", "?")
                + " " + System.getProperty("os.version", "")
                + "  " + System.getProperty("os.arch", ""));
        info.put("Theme", ThemeManager.current().displayName);

        Runtime runtime = Runtime.getRuntime();
        long used = runtime.totalMemory() - runtime.freeMemory();
        info.put("Memory", formatBytes(used) + " of " + formatBytes(runtime.maxMemory()));

        File database = dbManager.getDatabaseFile();
        info.put("Database", database.getAbsolutePath());
        info.put("Database size", database.exists() ? formatBytes(database.length()) : "not created yet");
        info.put("User", System.getProperty("user.name", "?"));
        return info;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void copyDetails() {
        StringBuilder text = new StringBuilder();
        text.append(AppInfo.NAME).append(" - ").append(AppInfo.TITLE).append('\n');
        for (Map.Entry<String, String> entry : environment().entrySet()) {
            text.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        text.append('\n');
        for (Map.Entry<String, Integer> entry : dbManager.getRecordCounts().entrySet()) {
            text.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(text.toString()), null);
            JOptionPane.showMessageDialog(this, "Details copied to the clipboard.",
                    "Copied", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write to the clipboard:\n" + ex.getMessage(),
                    "Copy failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    // ------------------------------------------------------------------ hero

    /** Accent gradient banner with the app mark, name, tagline and version chip. */
    private static final class HeroBanner extends JPanel {

        HeroBanner() {
            setOpaque(true);
            setPreferredSize(new Dimension(0, 150));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int width = getWidth();
                int height = getHeight();

                Color accent = ThemeManager.accent();
                Color deep = ThemeManager.blend(accent, Color.BLACK, 0.78f);
                g.setPaint(new GradientPaint(0, 0, deep, width, height, accent));
                g.fillRect(0, 0, width, height);

                // Soft highlight so the flat fill has some depth
                g.setColor(new Color(255, 255, 255, 26));
                g.fill(new Ellipse2D.Double(width * 0.55, -height * 0.9, width * 0.9, height * 1.7));

                Color onAccent = ThemeManager.color("App.onAccent", Color.WHITE);
                paintMark(g, 28, height / 2 - 28, 56, onAccent);

                g.setColor(onAccent);
                g.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 24f));
                g.drawString(AppInfo.NAME, 104, height / 2 - 12);

                g.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 14f));
                g.drawString(AppInfo.TITLE, 104, height / 2 + 8);

                g.setColor(new Color(onAccent.getRed(), onAccent.getGreen(), onAccent.getBlue(), 190));
                g.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 11.5f));
                g.drawString(AppInfo.TAGLINE, 104, height / 2 + 30);

                paintVersionChip(g, width, onAccent);
            } finally {
                g.dispose();
            }
        }

        /** Rounded badge holding a small bot face, drawn rather than loaded. */
        private void paintMark(Graphics2D g, int x, int y, int size, Color color) {
            g.setColor(new Color(255, 255, 255, 38));
            g.fill(new RoundRectangle2D.Double(x, y, size, size, 16, 16));
            g.setColor(new Color(255, 255, 255, 90));
            g.setStroke(new BasicStroke(1.2f));
            g.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5, size - 1.0, size - 1.0, 16, 16));

            double scale = size / 56.0;
            Graphics2D mark = (Graphics2D) g.create();
            try {
                mark.translate(x, y);
                mark.scale(scale, scale);
                mark.setColor(color);
                mark.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                // antenna
                mark.drawLine(28, 11, 28, 17);
                mark.fill(new Ellipse2D.Double(25.4, 7.4, 5.2, 5.2));
                // head
                mark.draw(new RoundRectangle2D.Double(14, 17, 28, 22, 8, 8));
                // eyes
                mark.fill(new Ellipse2D.Double(21, 25, 4.4, 4.4));
                mark.fill(new Ellipse2D.Double(30.6, 25, 4.4, 4.4));
                // mouth
                mark.drawLine(23, 33, 33, 33);
                // arms
                mark.drawLine(10, 24, 10, 32);
                mark.drawLine(46, 24, 46, 32);
            } finally {
                mark.dispose();
            }
        }

        private void paintVersionChip(Graphics2D g, int width, Color onAccent) {
            String label = "Version " + AppInfo.VERSION;
            g.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 11f));
            FontMetrics metrics = g.getFontMetrics();

            int chipWidth = metrics.stringWidth(label) + 22;
            int chipHeight = 22;
            int x = width - chipWidth - 24;
            int y = 22;
            if (x < 110) return; // too narrow to place without overlapping the title

            g.setColor(new Color(255, 255, 255, 46));
            g.fill(new RoundRectangle2D.Double(x, y, chipWidth, chipHeight, chipHeight, chipHeight));
            g.setColor(onAccent);
            g.drawString(label, x + 11,
                    y + (chipHeight - metrics.getHeight()) / 2 + metrics.getAscent());
        }
    }
}
