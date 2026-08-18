package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import java.awt.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lists every application defined in Settings together with the RPA projects
 * that use it, grouped by application.
 */
public class AppUsageDialog extends JDialog {

    private static final String UNUSED = "(not used by any RPA)";

    private final DatabaseManager dbManager;
    private final UsageModel model = new UsageModel();
    private final JLabel summaryLabel;

    /**
     * @param onManageApplications opens Settings on the Applications page; may be null
     */
    public AppUsageDialog(Frame owner, DatabaseManager dbManager, Runnable onManageApplications) {
        super(owner, "App Usage", true);
        this.dbManager = dbManager;

        setSize(760, 620);
        setMinimumSize(new Dimension(560, 420));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle("Application Usage",
                AppIcons.grid(17, "App.accent"));
        summaryLabel = UiFactory.subtitle("");

        JPanel header = UiFactory.transparent(new BorderLayout(0, UiFactory.HEADER_GAP));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(summaryLabel, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- table
        JTable table = new JTable(model);
        UiFactory.styleTable(table);
        table.setRowHeight(28);
        table.setDefaultRenderer(Object.class, new UsageCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(420);
        table.getColumnModel().getColumn(1).setPreferredWidth(120);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JPanel footer = UiFactory.transparent(new BorderLayout(8, 0));

        if (onManageApplications != null) {
            JButton manageBtn = UiFactory.secondary("Manage Applications...",
                    AppIcons.sliders(15, "App.subtleForeground"));
            manageBtn.addActionListener(e -> {
                dispose();
                onManageApplications.run();
            });
            footer.add(manageBtn, BorderLayout.WEST);
        }

        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> dispose());
        JPanel closeHolder = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        closeHolder.add(closeBtn);
        footer.add(closeHolder, BorderLayout.CENTER);

        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        load();
    }

    // ------------------------------------------------------------------ data

    private void load() {
        Map<String, List<String>> byApplication = new LinkedHashMap<>();
        for (String[] pair : dbManager.getApplicationUsage()) {
            List<String> projects =
                    byApplication.computeIfAbsent(pair[0], key -> new ArrayList<>());
            if (pair.length > 1 && !pair[1].trim().isEmpty()) {
                projects.add(pair[1]);
            }
        }

        model.setUsage(byApplication);

        if (byApplication.isEmpty()) {
            summaryLabel.setText("No applications defined yet - add them in Settings > Applications.");
        } else {
            summaryLabel.setText(byApplication.size() + " application"
                    + (byApplication.size() == 1 ? "" : "s")
                    + ", " + model.getLinkCount() + " RPA link"
                    + (model.getLinkCount() == 1 ? "" : "s") + ".");
        }
    }

    // ----------------------------------------------------------- table model

    /** Application headers followed by the projects that use them. */
    private static final class UsageModel extends AbstractTableModel {

        private final String[] columns = {"Application / RPA Name", "Used By"};
        private final List<Object[]> rows = new ArrayList<>();
        private int linkCount;

        void setUsage(Map<String, List<String>> byApplication) {
            rows.clear();
            linkCount = 0;

            for (Map.Entry<String, List<String>> entry : byApplication.entrySet()) {
                List<String> projects = entry.getValue();
                String badge = projects.isEmpty()
                        ? "none"
                        : projects.size() + (projects.size() == 1 ? " RPA" : " RPAs");
                // {isGroup, text, badge}
                rows.add(new Object[]{Boolean.TRUE, entry.getKey(), badge});

                if (projects.isEmpty()) {
                    rows.add(new Object[]{Boolean.FALSE, UNUSED, ""});
                } else {
                    for (String project : projects) {
                        rows.add(new Object[]{Boolean.FALSE, project, ""});
                        linkCount++;
                    }
                }
            }
            fireTableDataChanged();
        }

        int getLinkCount() {
            return linkCount;
        }

        boolean isGroupRow(int row) {
            return row >= 0 && row < rows.size() && Boolean.TRUE.equals(rows.get(row)[0]);
        }

        boolean isPlaceholderRow(int row) {
            return row >= 0 && row < rows.size()
                    && Boolean.FALSE.equals(rows.get(row)[0])
                    && UNUSED.equals(rows.get(row)[1]);
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
            Object[] entry = rows.get(row);
            return column == 0 ? entry[1] : entry[2];
        }
    }

    // -------------------------------------------------------------- renderer

    private final class UsageCellRenderer extends DefaultTableCellRenderer {

        private boolean groupRow;

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);

            int modelRow = table.convertRowIndexToModel(row);
            groupRow = model.isGroupRow(modelRow);
            boolean placeholder = model.isPlaceholderRow(modelRow);

            setBorder(new EmptyBorder(0, groupRow ? 12 : 30, 0, 10));

            if (groupRow) {
                setFont(table.getFont().deriveFont(Font.BOLD, 13f));
                setForeground(ThemeManager.accent());
            } else {
                setFont(table.getFont().deriveFont(placeholder ? Font.ITALIC : Font.PLAIN, 12f));
                setForeground(placeholder
                        ? ThemeManager.subtle()
                        : ThemeManager.color("Table.foreground", Color.DARK_GRAY));
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
        }
    }
}
