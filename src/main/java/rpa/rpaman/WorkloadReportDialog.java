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
 * Who is carrying what: each developer with the projects and change requests
 * they own plus their open adhoc work, and a final group for anything nobody
 * has picked up.
 */
public class WorkloadReportDialog extends JDialog {

    private static final String UNASSIGNED = "(unassigned)";

    private final DatabaseManager dbManager;
    private final WorkloadModel model = new WorkloadModel();
    private final JLabel summaryLabel;
    private final JCheckBox includeClosedBox;

    public WorkloadReportDialog(Frame owner, DatabaseManager dbManager, Runnable onManageDevelopers) {
        super(owner, "Workload by Developer", true);
        this.dbManager = dbManager;

        setSize(1000, 700);
        setMinimumSize(new Dimension(720, 500));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle("Workload by Developer",
                AppIcons.user(17, "App.accent"));
        summaryLabel = UiFactory.subtitle("");

        includeClosedBox = new JCheckBox("Include closed items");
        includeClosedBox.setOpaque(false);
        includeClosedBox.addActionListener(e -> load());

        JPanel header = UiFactory.transparent(new BorderLayout(0, UiFactory.HEADER_GAP));
        header.add(titleLabel, BorderLayout.NORTH);
        JPanel headerRow = UiFactory.transparent(new BorderLayout(12, 0));
        headerRow.add(summaryLabel, BorderLayout.CENTER);
        headerRow.add(includeClosedBox, BorderLayout.EAST);
        header.add(headerRow, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- table
        JTable table = new JTable(model);
        UiFactory.styleTable(table);
        table.setRowHeight(28);
        table.setDefaultRenderer(Object.class, new WorkloadCellRenderer());
        table.getColumnModel().getColumn(0).setPreferredWidth(360);
        table.getColumnModel().getColumn(1).setPreferredWidth(150);
        table.getColumnModel().getColumn(2).setPreferredWidth(140);
        table.getColumnModel().getColumn(3).setPreferredWidth(110);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JPanel footer = UiFactory.transparent(new BorderLayout(8, 0));
        if (onManageDevelopers != null) {
            JButton manageBtn = UiFactory.secondary("Manage Developers...",
                    AppIcons.sliders(15, "App.subtleForeground"));
            manageBtn.addActionListener(e -> {
                dispose();
                onManageDevelopers.run();
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
        boolean includeClosed = includeClosedBox.isSelected();

        Map<String, List<Object[]>> byDeveloper = new LinkedHashMap<>();
        for (Developer developer : dbManager.getDevelopers()) {
            byDeveloper.put(developer.name, new ArrayList<>());
        }
        List<Object[]> unassigned = new ArrayList<>();

        // Projects
        Map<String, String> owners = dbManager.getProjectOwners();
        for (String project : dbManager.getProjectNames()) {
            String owner = owners.getOrDefault(project, "");
            Object[] row = {project, "Project", "", ""};
            bucket(byDeveloper, unassigned, owner).add(row);
        }

        // Change requests
        for (ChangeRequest cr : dbManager.getAllChangeRequests()) {
            if (!includeClosed && ChangeRequest.isClosed(cr.status)) continue;
            String label = cr.crNumber.isEmpty() ? cr.title : cr.crNumber + "  " + cr.title;
            Object[] row = {label + "   [" + cr.projectName + "]",
                    "Change Request", cr.status, cr.targetDate};
            bucket(byDeveloper, unassigned, cr.assignedTo).add(row);
        }

        // Adhoc items
        for (AdhocItem item : dbManager.getAdhocItems(null)) {
            if (!includeClosed && AdhocItem.isClosed(item.status)) continue;
            Object[] row = {item.title, "Adhoc", item.status, item.dueDate};
            bucket(byDeveloper, unassigned, item.developerName).add(row);
        }

        model.setWorkload(byDeveloper, unassigned);

        summaryLabel.setText(byDeveloper.size() + " developer"
                + (byDeveloper.size() == 1 ? "" : "s")
                + ", " + model.getItemCount() + " item" + (model.getItemCount() == 1 ? "" : "s")
                + (unassigned.isEmpty() ? "." : ", " + unassigned.size() + " unassigned."));
    }

    /** Routes a row to its owner's list, or to the unassigned pile. */
    private List<Object[]> bucket(Map<String, List<Object[]>> byDeveloper,
                                  List<Object[]> unassigned, String owner) {
        if (owner == null || owner.trim().isEmpty()) return unassigned;
        List<Object[]> rows = byDeveloper.get(owner);
        // An owner who is no longer in the developer list still gets a group
        return rows != null ? rows : byDeveloper.computeIfAbsent(owner, k -> new ArrayList<>());
    }

    // ----------------------------------------------------------- table model

    private static final class WorkloadModel extends AbstractTableModel {

        private final String[] columns = {"Developer / Item", "Type", "Status", "Due / Target"};
        private final List<Object[]> rows = new ArrayList<>();
        private int itemCount;

        void setWorkload(Map<String, List<Object[]>> byDeveloper, List<Object[]> unassigned) {
            rows.clear();
            itemCount = 0;

            for (Map.Entry<String, List<Object[]>> entry : byDeveloper.entrySet()) {
                addGroup(entry.getKey(), entry.getValue());
            }
            if (!unassigned.isEmpty()) {
                addGroup(UNASSIGNED, unassigned);
            }
            fireTableDataChanged();
        }

        private void addGroup(String name, List<Object[]> items) {
            int size = items.size();
            rows.add(new Object[]{Boolean.TRUE,
                    name + "   (" + size + (size == 1 ? " item)" : " items)"), "", "", ""});
            for (Object[] item : items) {
                rows.add(new Object[]{Boolean.FALSE, item[0], item[1], item[2], item[3]});
                itemCount++;
            }
            if (items.isEmpty()) {
                rows.add(new Object[]{Boolean.FALSE, "nothing assigned", "", "", ""});
            }
        }

        int getItemCount() {
            return itemCount;
        }

        boolean isGroupRow(int row) {
            return row >= 0 && row < rows.size() && Boolean.TRUE.equals(rows.get(row)[0]);
        }

        boolean isPlaceholderRow(int row) {
            return row >= 0 && row < rows.size()
                    && Boolean.FALSE.equals(rows.get(row)[0])
                    && "nothing assigned".equals(rows.get(row)[1]);
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
            return rows.get(row)[column + 1];
        }
    }

    // -------------------------------------------------------------- renderer

    private final class WorkloadCellRenderer extends DefaultTableCellRenderer {

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
