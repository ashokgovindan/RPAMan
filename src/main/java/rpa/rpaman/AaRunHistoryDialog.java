package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shows the last 24 hours of Automation Anywhere runs, grouped by device.
 * <p>
 * Opens on whatever is already cached in SQLite and immediately kicks off a
 * fresh scrape of the Control Room, so the window is useful straight away even
 * while Chrome is still starting.
 */
public class AaRunHistoryDialog extends JDialog {

    private static final int WINDOW_HOURS = 24;

    private final DatabaseManager dbManager;
    private final AaRunHistoryService service;

    private final GroupedModel model = new GroupedModel();
    private final JTable table;
    private final JButton refreshBtn;
    private final JLabel statusLabel;

    private SwingWorker<List<AaActivity>, String> worker;

    public AaRunHistoryDialog(Frame owner, DatabaseManager dbManager) {
        super(owner, "AA Run History (last " + WINDOW_HOURS + " hours)", true);
        this.dbManager = dbManager;
        this.service = new AaRunHistoryService(dbManager);

        setSize(940, 680);
        setMinimumSize(new Dimension(720, 480));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle("Automation Anywhere Run History", null);
        statusLabel = UiFactory.subtitle("Loading cached runs...");

        refreshBtn = UiFactory.primary("Refresh from Control Room",
                AppIcons.refresh(15, "App.onAccent"));
        refreshBtn.addActionListener(e -> startRefresh());

        JPanel toolbar = UiFactory.transparent(new BorderLayout(12, 0));
        toolbar.add(refreshBtn, BorderLayout.WEST);
        toolbar.add(statusLabel, BorderLayout.CENTER);

        JPanel header = UiFactory.transparent(new BorderLayout(0, UiFactory.HEADER_GAP));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(toolbar, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- table
        table = new JTable(model);
        UiFactory.styleTable(table);
        table.setRowHeight(30);
        table.setDefaultRenderer(Object.class, new RunCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(340);
        table.getColumnModel().getColumn(1).setPreferredWidth(140);
        table.getColumnModel().getColumn(2).setPreferredWidth(190);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);

        loadFromDatabase();
        SwingUtilities.invokeLater(this::startRefresh);
    }

    // ------------------------------------------------------------------ data

    /** Cutoff for the display window; the scrape itself always pulls 30 days. */
    private String cutoffIso() {
        return LocalDateTime.now().minusHours(WINDOW_HOURS).toString();
    }

    private void loadFromDatabase() {
        List<AaActivity> activities = dbManager.getActivitiesSince(cutoffIso());
        model.setActivities(activities);

        if (activities.isEmpty()) {
            statusLabel.setText("No runs cached for the last " + WINDOW_HOURS + " hours.");
        } else {
            statusLabel.setText(activities.size() + " runs across "
                    + model.getDeviceCount() + " devices (cached).");
        }
    }

    // --------------------------------------------------------------- refresh

    private void startRefresh() {
        if (worker != null && !worker.isDone()) return;

        refreshBtn.setEnabled(false);
        refreshBtn.setText("Refreshing...");
        statusLabel.setText("Starting Chrome...");

        worker = new SwingWorker<List<AaActivity>, String>() {
            @Override
            protected List<AaActivity> doInBackground() throws Exception {
                return service.fetchAndStore(this::publish);
            }

            @Override
            protected void process(List<String> messages) {
                if (!messages.isEmpty()) {
                    statusLabel.setText(messages.get(messages.size() - 1));
                }
            }

            @Override
            protected void done() {
                refreshBtn.setText("Refresh from Control Room");
                refreshBtn.setEnabled(true);
                try {
                    List<AaActivity> scraped = get();
                    loadFromDatabase();
                    statusLabel.setText("Scraped " + scraped.size() + " rows. Showing "
                            + model.getRunCount() + " runs from the last " + WINDOW_HOURS
                            + " hours across " + model.getDeviceCount() + " devices.");
                } catch (Exception ex) {
                    Throwable cause = ex.getCause() == null ? ex : ex.getCause();
                    statusLabel.setText("Refresh failed. Showing cached runs.");
                    JOptionPane.showMessageDialog(AaRunHistoryDialog.this,
                            cause.getMessage(),
                            "Could not read the Control Room",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    // ----------------------------------------------------------- table model

    /** Flattens devices and their runs into one list of group and detail rows. */
    private static final class GroupedModel extends AbstractTableModel {

        private final String[] columns = {"Device / Automation", "Status", "Started"};
        private final List<Object> rows = new ArrayList<>();
        private int deviceCount;
        private int runCount;

        void setActivities(List<AaActivity> activities) {
            rows.clear();

            Map<String, List<AaActivity>> byDevice = new LinkedHashMap<>();
            for (AaActivity activity : activities) {
                byDevice.computeIfAbsent(activity.deviceLabel(), key -> new ArrayList<>())
                        .add(activity);
            }

            for (Map.Entry<String, List<AaActivity>> entry : byDevice.entrySet()) {
                int size = entry.getValue().size();
                rows.add(entry.getKey() + "   (" + size + (size == 1 ? " run)" : " runs)"));
                rows.addAll(entry.getValue());
            }

            deviceCount = byDevice.size();
            runCount = activities.size();
            fireTableDataChanged();
        }

        int getDeviceCount() {
            return deviceCount;
        }

        int getRunCount() {
            return runCount;
        }

        boolean isGroupRow(int row) {
            return row >= 0 && row < rows.size() && rows.get(row) instanceof String;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return columns.length;
        }

        @Override
        public String getColumnName(int column) {
            return columns[column];
        }

        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Object getValueAt(int row, int column) {
            Object entry = rows.get(row);
            if (entry instanceof String) {
                return column == 0 ? entry : "";
            }
            AaActivity activity = (AaActivity) entry;
            switch (column) {
                case 0:
                    return activity.automationName.isEmpty()
                            ? activity.activityName : activity.automationName;
                case 1:
                    return activity.status;
                case 2:
                    return activity.startedDisplay;
                default:
                    return "";
            }
        }
    }

    // -------------------------------------------------------------- renderer

    /** Paints device headers as accent bands and statuses as coloured pills. */
    private final class RunCellRenderer extends DefaultTableCellRenderer {

        private boolean groupRow;
        private boolean statusPill;
        private String pillText = "";

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);

            groupRow = model.isGroupRow(table.convertRowIndexToModel(row));
            String text = value == null ? "" : value.toString();

            statusPill = !groupRow && column == 1 && !text.isEmpty();
            pillText = text;

            setBorder(new EmptyBorder(0, groupRow ? 12 : 22, 0, 10));
            setText(statusPill ? "" : text);

            if (groupRow) {
                setFont(table.getFont().deriveFont(Font.BOLD, 13f));
                setForeground(ThemeManager.accent());
            } else {
                setFont(table.getFont().deriveFont(Font.PLAIN, 12f));
                setForeground(ThemeManager.color("Table.foreground", Color.DARK_GRAY));
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            if (groupRow) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(ThemeManager.color("App.accentSoft", ThemeManager.card()));
                    g.fillRect(0, 0, getWidth(), getHeight());
                } finally {
                    g.dispose();
                }
            }
            super.paintComponent(graphics);

            if (!statusPill || pillText.isEmpty()) return;

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g.setFont(getFont().deriveFont(Font.BOLD, 11f));
                FontMetrics metrics = g.getFontMetrics();

                int pillWidth = metrics.stringWidth(pillText) + 20;
                int pillHeight = Math.min(getHeight() - 8, 20);
                int x = 22;
                int y = (getHeight() - pillHeight) / 2;

                g.setColor(badgeBackground(pillText));
                g.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, pillHeight, pillHeight));
                g.setColor(badgeForeground(pillText));
                int baseline = y + (pillHeight - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(pillText, x + 10, baseline);
            } finally {
                g.dispose();
            }
        }

        private Color badgeBackground(String status) {
            String key = status.toLowerCase();
            if (key.contains("fail") || key.contains("error")) {
                return ThemeManager.color("App.badgeErrorBg", Color.PINK);
            }
            if (key.contains("complete") || key.contains("success")) {
                return ThemeManager.color("App.badgeDoneBg", Color.LIGHT_GRAY);
            }
            if (key.contains("run") || key.contains("progress") || key.contains("queue")) {
                return ThemeManager.color("App.badgeProgressBg", Color.LIGHT_GRAY);
            }
            return ThemeManager.color("App.badgeTodoBg", Color.LIGHT_GRAY);
        }

        private Color badgeForeground(String status) {
            String key = status.toLowerCase();
            if (key.contains("fail") || key.contains("error")) {
                return ThemeManager.color("App.badgeErrorFg", Color.RED);
            }
            if (key.contains("complete") || key.contains("success")) {
                return ThemeManager.color("App.badgeDoneFg", Color.DARK_GRAY);
            }
            if (key.contains("run") || key.contains("progress") || key.contains("queue")) {
                return ThemeManager.color("App.badgeProgressFg", Color.DARK_GRAY);
            }
            return ThemeManager.color("App.badgeTodoFg", Color.DARK_GRAY);
        }
    }
}
