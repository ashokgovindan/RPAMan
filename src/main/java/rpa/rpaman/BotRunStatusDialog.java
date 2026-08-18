package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs each project's configured Received/Pending/Processed queries against its
 * own database and shows one row per machine.
 * <p>
 * Queries may contain the placeholders {@code {machine}} and {@code {user}},
 * which are replaced with the values of the row being refreshed.
 */
public class BotRunStatusDialog extends JDialog {

    private static final String EMPTY = "-";

    private final DatabaseManager dbManager;
    private final List<String> projectNames;

    private final DefaultTableModel tableModel;
    private final JTable statusTable;
    private final JButton refreshBtn;
    private final Map<Integer, String> rowErrors = new HashMap<>();

    public BotRunStatusDialog(Frame owner, DatabaseManager dbManager, List<String> projectNames) {
        super(owner, "Bot Run Status", true);
        this.dbManager = dbManager;
        this.projectNames = (projectNames == null) ? new ArrayList<>() : new ArrayList<>(projectNames);

        setSize(1060, 660);
        setMinimumSize(new Dimension(820, 480));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JPanel header = UiFactory.transparent(new BorderLayout(0, 10));
        JLabel titleLabel = UiFactory.sectionTitle("Bot Run Status Report", null);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 19f));
        header.add(titleLabel, BorderLayout.NORTH);

        refreshBtn = UiFactory.primary("Refresh Status", AppIcons.refresh(15, "App.onAccent"));
        refreshBtn.addActionListener(e -> refresh());

        JLabel note = UiFactory.subtitle("Note: Queries should return a single value.");
        note.setFont(note.getFont().deriveFont(Font.ITALIC, 12f));
        note.setBorder(new EmptyBorder(0, 12, 0, 0));

        JPanel toolbar = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        toolbar.add(refreshBtn);
        toolbar.add(note);
        header.add(toolbar, BorderLayout.CENTER);
        header.setBorder(new EmptyBorder(0, 2, 0, 2));
        root.add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- table
        String[] columns = {"RPA Name", "Machine", "User", "Received", "Pending", "Processed"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        statusTable = new JTable(tableModel) {
            @Override
            public String getToolTipText(MouseEvent event) {
                int viewRow = rowAtPoint(event.getPoint());
                if (viewRow >= 0) {
                    String error = rowErrors.get(convertRowIndexToModel(viewRow));
                    if (error != null && !error.isEmpty()) return error;
                }
                return super.getToolTipText(event);
            }
        };
        UiFactory.styleTable(statusTable);
        statusTable.getColumnModel().getColumn(0).setPreferredWidth(200);
        statusTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        statusTable.getColumnModel().getColumn(2).setPreferredWidth(120);
        statusTable.getColumnModel().getColumn(3).setPreferredWidth(110);
        statusTable.getColumnModel().getColumn(4).setPreferredWidth(110);
        statusTable.getColumnModel().getColumn(5).setPreferredWidth(190);
        statusTable.getColumnModel().getColumn(5).setCellRenderer(new ProcessedRenderer());
        ToolTipManager.sharedInstance().registerComponent(statusTable);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(statusTable), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> dispose());
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        refresh();
    }

    // ----------------------------------------------------------------- refresh

    private void refresh() {
        refreshBtn.setEnabled(false);
        refreshBtn.setText("Refreshing...");

        new SwingWorker<List<StatusRow>, Void>() {
            @Override
            protected List<StatusRow> doInBackground() {
                return collectRows();
            }

            @Override
            protected void done() {
                List<StatusRow> rows;
                try {
                    rows = get();
                } catch (Exception ex) {
                    rows = new ArrayList<>();
                }

                tableModel.setRowCount(0);
                rowErrors.clear();
                int index = 0;
                for (StatusRow row : rows) {
                    tableModel.addRow(new Object[]{
                            row.project, row.machine, row.user,
                            row.received, row.pending, row.processed});
                    if (!row.error.isEmpty()) rowErrors.put(index, row.error);
                    index++;
                }

                refreshBtn.setText("Refresh Status");
                refreshBtn.setEnabled(true);
            }
        }.execute();
    }

    /** Builds one row per machine, running the project's queries for each. */
    private List<StatusRow> collectRows() {
        List<StatusRow> rows = new ArrayList<>();

        for (String project : projectNames) {
            String[] config = dbManager.getProjectConfig(project);
            String dbPath = config[0];

            List<String[]> machines = dbManager.getMachines(project);
            if (machines.isEmpty()) machines.add(new String[]{"", ""});

            for (String[] machine : machines) {
                StatusRow row = new StatusRow();
                row.project = project;
                row.machine = machine.length > 0 ? machine[0] : "";
                row.user = machine.length > 1 ? machine[1] : "";

                StringBuilder errors = new StringBuilder();
                row.received = runQuery(dbPath, config[1], row, errors, "Received");
                row.pending = runQuery(dbPath, config[2], row, errors, "Pending");
                row.processed = runQuery(dbPath, config[3], row, errors, "Processed");
                row.error = errors.toString();

                rows.add(row);
            }
        }
        return rows;
    }

    private String runQuery(String dbPath, String sql, StatusRow row,
                            StringBuilder errors, String label) {
        if (dbPath == null || dbPath.trim().isEmpty()) return EMPTY;
        if (sql == null || sql.trim().isEmpty()) return EMPTY;

        String resolved = sql.replace("{machine}", row.machine).replace("{user}", row.user);
        try {
            String value = dbManager.queryExternalScalar(dbPath, resolved);
            return (value == null || value.isEmpty()) ? EMPTY : value;
        } catch (Exception ex) {
            if (errors.length() > 0) errors.append('\n');
            errors.append(label).append(": ").append(ex.getMessage());
            return EMPTY;
        }
    }

    // -------------------------------------------------------------- row model

    private static final class StatusRow {
        String project = "";
        String machine = "";
        String user = "";
        String received = EMPTY;
        String pending = EMPTY;
        String processed = EMPTY;
        String error = "";
    }

    // --------------------------------------------------------------- renderer

    /** Draws the processed count as a filled progress bar plus the value. */
    private final class ProcessedRenderer extends DefaultTableCellRenderer {

        private String text = EMPTY;
        private double fraction;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            text = value == null ? EMPTY : value.toString();
            setText("");
            setBorder(new EmptyBorder(0, 8, 0, 8));

            int modelRow = table.convertRowIndexToModel(row);
            fraction = computeFraction(
                    asNumber(tableModel.getValueAt(modelRow, 3)),
                    asNumber(text));
            return this;
        }

        private double computeFraction(Double received, Double processed) {
            if (received == null || processed == null || received <= 0) return 0;
            return Math.max(0, Math.min(1, processed / received));
        }

        private Double asNumber(Object value) {
            if (value == null) return null;
            try {
                return Double.valueOf(value.toString().trim());
            } catch (NumberFormatException ex) {
                return null;
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                int barWidth = Math.max(40, getWidth() / 2 - 12);
                int barHeight = Math.min(getHeight() - 10, 16);
                int x = 8;
                int y = (getHeight() - barHeight) / 2;

                RoundRectangle2D track = new RoundRectangle2D.Double(x, y, barWidth, barHeight, 6, 6);
                g.setColor(ThemeManager.color("App.stripe", Color.LIGHT_GRAY));
                g.fill(track);
                g.setColor(ThemeManager.border());
                g.draw(track);

                if (fraction > 0) {
                    double fillWidth = Math.max(6, barWidth * fraction);
                    g.setColor(ThemeManager.color("App.badgeDoneFg", new Color(0x1E7A4C)));
                    g.fill(new RoundRectangle2D.Double(x + 1, y + 1, fillWidth - 2, barHeight - 2, 5, 5));
                }

                g.setFont(getFont().deriveFont(Font.BOLD, 12f));
                FontMetrics metrics = g.getFontMetrics();
                int textX = x + barWidth + 16;
                int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                g.setColor(getForeground());
                g.drawString(text, textX, baseline);
            } finally {
                g.dispose();
            }
        }
    }
}
