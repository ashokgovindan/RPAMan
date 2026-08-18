package rpa.rpaman;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Settings page for developers and what they own.
 * <p>
 * Ownership is single-assignee, so ticking a project or change request here
 * takes it from whoever held it before. Those ticks write straight to the
 * database; only the developer list and the adhoc table need an explicit save.
 */
public class DevelopersPanel extends JPanel {

    private static final String[] DEVELOPER_COLUMNS = {"Emp ID", "Name", "Email", "Active"};
    private static final int[] DEVELOPER_WIDTHS = {110, 200, 260, 70};

    private static final String[] PROJECT_COLUMNS = {"Assigned", "RPA Name", "Current Owner"};
    private static final int[] PROJECT_WIDTHS = {80, 260, 200};

    private static final String[] CR_COLUMNS = {
            "Assigned", "RPA Name", "CR Number", "Title", "Status", "Current Owner"};
    private static final int[] CR_WIDTHS = {80, 160, 110, 260, 120, 160};

    private static final String[] ADHOC_COLUMNS = {
            "Title", "Status", "Priority", "Start", "Due", "Completed", "Description"};
    private static final int[] ADHOC_WIDTHS = {220, 120, 100, 100, 100, 100, 300};

    private final DatabaseManager dbManager;

    private final DefaultTableModel developerModel;
    private final JTable developerTable;
    private final List<Developer> developers = new ArrayList<>();

    private final DefaultTableModel projectModel;
    private final JTable projectTable;
    private final List<String> projectNames = new ArrayList<>();

    private final DefaultTableModel crModel;
    private final JTable crTable;
    private final List<ChangeRequest> changeRequests = new ArrayList<>();

    private final DefaultTableModel adhocModel;
    private final JTable adhocTable;

    private final JLabel selectionLabel;
    private final JTabbedPane tabs;

    private String selectedDeveloper;
    /** Guards the table listeners while rows are being repopulated. */
    private boolean loading;

    public DevelopersPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setOpaque(false);
        setLayout(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------- developers
        developerModel = new DefaultTableModel(DEVELOPER_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 3 ? Boolean.class : String.class;
            }
        };
        developerTable = table(developerModel, DEVELOPER_WIDTHS);
        developerTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        developerTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || loading) return;
            onDeveloperSelected();
        });

        JButton addBtn = UiFactory.secondary("Add Developer",
                AppIcons.plus(15, "App.subtleForeground"));
        addBtn.addActionListener(e -> addDeveloper());

        JButton removeBtn = UiFactory.secondary("Remove Developer",
                AppIcons.trash(15, "App.subtleForeground"));
        removeBtn.addActionListener(e -> removeDeveloper());

        JButton saveBtn = UiFactory.primary("Save Developers",
                AppIcons.save(15, "App.onAccent"));
        saveBtn.addActionListener(e -> saveDevelopers(true));

        JButton copyCsvBtn = UiFactory.secondary("Copy All as CSV",
                AppIcons.clipboard(15, "App.subtleForeground"));
        copyCsvBtn.setToolTipText("Copy every developer, with their assignment counts, "
                + "to the clipboard as CSV");
        copyCsvBtn.addActionListener(e -> copyDevelopersAsCsv());

        JPanel developerButtons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        developerButtons.add(saveBtn);
        developerButtons.add(addBtn);
        developerButtons.add(removeBtn);
        developerButtons.add(copyCsvBtn);

        JPanel developerCard = UiFactory.card(new BorderLayout(), 5);
        developerCard.add(UiFactory.bareScroll(developerTable), BorderLayout.CENTER);

        JPanel top = UiFactory.transparent(new BorderLayout(0, 8));
        top.add(developerCard, BorderLayout.CENTER);
        top.add(developerButtons, BorderLayout.SOUTH);

        // ------------------------------------------------------ assignments
        projectModel = new DefaultTableModel(PROJECT_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 && selectedDeveloper != null;
            }
        };
        projectTable = table(projectModel, PROJECT_WIDTHS);
        projectModel.addTableModelListener(e -> {
            if (loading || e.getColumn() != 0) return;
            applyProjectAssignment(e.getFirstRow());
        });

        crModel = new DefaultTableModel(CR_COLUMNS, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0 && selectedDeveloper != null;
            }
        };
        crTable = table(crModel, CR_WIDTHS);
        crTable.getColumnModel().getColumn(4).setCellRenderer(UiFactory.badgeRenderer());
        crModel.addTableModelListener(e -> {
            if (loading || e.getColumn() != 0) return;
            applyChangeRequestAssignment(e.getFirstRow());
        });

        adhocModel = new DefaultTableModel(ADHOC_COLUMNS, 0);
        adhocTable = table(adhocModel, ADHOC_WIDTHS);
        setComboEditor(adhocTable, 1, AdhocItem.STATUSES);
        setComboEditor(adhocTable, 2, AdhocItem.PRIORITIES);

        tabs = new JTabbedPane();
        tabs.addTab("Projects", tabCard(projectTable, null));
        tabs.addTab("Change Requests", tabCard(crTable, null));
        tabs.addTab("Adhoc Items", tabCard(adhocTable, adhocButtons()));

        selectionLabel = UiFactory.subtitle("Select a developer to manage their work.");

        JPanel bottom = UiFactory.transparent(new BorderLayout(0, 6));
        bottom.add(selectionLabel, BorderLayout.NORTH);
        bottom.add(tabs, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, bottom);
        split.setResizeWeight(0.38);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(8);
        split.setContinuousLayout(true);

        add(split, BorderLayout.CENTER);

        load();
    }

    // ------------------------------------------------------------------ load

    /** Reloads developers and, if one is still selected, their assignments. */
    public final void load() {
        stopEditing();
        loading = true;
        try {
            developers.clear();
            developers.addAll(dbManager.getDevelopers());

            developerModel.setRowCount(0);
            for (Developer d : developers) {
                developerModel.addRow(new Object[]{d.empId, d.name, d.email, d.active});
            }
        } finally {
            loading = false;
        }

        selectedDeveloper = null;
        selectionLabel.setText("Select a developer to manage their work.");
        loadAssignments();

        if (!developers.isEmpty()) {
            developerTable.setRowSelectionInterval(0, 0);
            onDeveloperSelected();
        }
    }

    private void onDeveloperSelected() {
        int row = developerTable.getSelectedRow();
        if (row < 0 || row >= developers.size()) {
            selectedDeveloper = null;
            selectionLabel.setText("Select a developer to manage their work.");
        } else {
            // Read from the table so an unsaved rename still points at the row
            selectedDeveloper = developers.get(developerTable.convertRowIndexToModel(row)).name;
            selectionLabel.setText("Managing work for " + selectedDeveloper
                    + ".   Ticking an item takes it from its current owner.");
        }
        loadAssignments();
    }

    private void loadAssignments() {
        loading = true;
        try {
            Map<String, String> owners = dbManager.getProjectOwners();

            projectNames.clear();
            projectNames.addAll(dbManager.getProjectNames());
            projectModel.setRowCount(0);
            for (String project : projectNames) {
                String owner = owners.getOrDefault(project, "");
                projectModel.addRow(new Object[]{
                        owner.equals(selectedDeveloper) && selectedDeveloper != null,
                        project,
                        owner});
            }

            changeRequests.clear();
            changeRequests.addAll(dbManager.getAllChangeRequests());
            crModel.setRowCount(0);
            for (ChangeRequest cr : changeRequests) {
                crModel.addRow(new Object[]{
                        !cr.assignedTo.isEmpty() && cr.assignedTo.equals(selectedDeveloper),
                        cr.projectName,
                        cr.crNumber,
                        cr.title,
                        cr.status,
                        cr.assignedTo});
            }

            adhocModel.setRowCount(0);
            if (selectedDeveloper != null) {
                for (AdhocItem item : dbManager.getAdhocItems(selectedDeveloper)) {
                    adhocModel.addRow(new Object[]{
                            item.title, item.status, item.priority,
                            item.startDate, item.dueDate, item.completedDate, item.description});
                }
            }
        } finally {
            loading = false;
        }
    }

    // ------------------------------------------------------------ assignment

    private void applyProjectAssignment(int row) {
        if (selectedDeveloper == null || row < 0 || row >= projectNames.size()) return;

        boolean assigned = Boolean.TRUE.equals(projectModel.getValueAt(row, 0));
        String project = projectNames.get(row);
        String currentOwner = String.valueOf(projectModel.getValueAt(row, 2));

        if (assigned) {
            dbManager.setProjectOwner(project, selectedDeveloper);
        } else if (selectedDeveloper.equals(currentOwner)) {
            dbManager.setProjectOwner(project, "");
        }
        loadAssignments();
    }

    private void applyChangeRequestAssignment(int row) {
        if (selectedDeveloper == null || row < 0 || row >= changeRequests.size()) return;

        boolean assigned = Boolean.TRUE.equals(crModel.getValueAt(row, 0));
        ChangeRequest cr = changeRequests.get(row);

        if (assigned) {
            dbManager.setChangeRequestOwner(cr.id, selectedDeveloper);
        } else if (selectedDeveloper.equals(cr.assignedTo)) {
            dbManager.setChangeRequestOwner(cr.id, "");
        }
        loadAssignments();
    }

    // ---------------------------------------------------------- developer CRUD

    private void addDeveloper() {
        String name = JOptionPane.showInputDialog(this,
                "Developer name:", "Add Developer", JOptionPane.PLAIN_MESSAGE);
        if (name == null) return;
        name = name.trim();
        if (name.isEmpty()) return;

        if (!dbManager.addDeveloper(name, "", "")) {
            JOptionPane.showMessageDialog(this, "\"" + name + "\" already exists.",
                    "Duplicate developer", JOptionPane.WARNING_MESSAGE);
            return;
        }
        load();
        selectByName(name);
    }

    private void removeDeveloper() {
        int row = developerTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a developer to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = developers.get(developerTable.convertRowIndexToModel(row)).name;

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove \"" + name + "\"?\n"
                        + "Their adhoc items are deleted, and any project or change request "
                        + "they own becomes unassigned.",
                "Remove Developer", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        dbManager.removeDeveloper(name);
        load();
    }

    /** Writes developer edits and the visible developer's adhoc list. */
    public void saveDevelopers(boolean notifyUser) {
        stopEditing();

        for (int row = 0; row < developerModel.getRowCount() && row < developers.size(); row++) {
            Developer d = developers.get(row);
            String name = text(developerModel, row, 1);
            if (name.isEmpty()) continue;
            dbManager.updateDeveloper(d.id, name,
                    text(developerModel, row, 0),
                    text(developerModel, row, 2),
                    Boolean.TRUE.equals(developerModel.getValueAt(row, 3)));
        }

        saveAdhocItems();

        String keep = selectedDeveloper;
        load();
        selectByName(keep);

        if (notifyUser) {
            JOptionPane.showMessageDialog(this, "Developer details saved.",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveAdhocItems() {
        if (selectedDeveloper == null) return;

        List<AdhocItem> items = new ArrayList<>();
        for (int row = 0; row < adhocModel.getRowCount(); row++) {
            AdhocItem item = new AdhocItem();
            item.developerName = selectedDeveloper;
            item.title = text(adhocModel, row, 0);
            item.status = text(adhocModel, row, 1);
            item.priority = text(adhocModel, row, 2);
            item.startDate = text(adhocModel, row, 3);
            item.dueDate = text(adhocModel, row, 4);
            item.completedDate = text(adhocModel, row, 5);
            item.description = text(adhocModel, row, 6);
            if (!item.title.isEmpty()) items.add(item);
        }
        dbManager.saveAdhocItems(selectedDeveloper, items);
    }

    // ------------------------------------------------------------ csv export

    /**
     * Copies every developer to the clipboard as CSV, including what they own
     * so the export is useful on its own in a spreadsheet.
     */
    private void copyDevelopersAsCsv() {
        // Persist pending edits first so the export matches what is on screen
        saveDevelopers(false);

        List<Developer> all = dbManager.getDevelopers();
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no developers to export.",
                    "Nothing to copy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        Map<String, String> owners = dbManager.getProjectOwners();
        List<ChangeRequest> allChangeRequests = dbManager.getAllChangeRequests();
        List<AdhocItem> allAdhoc = dbManager.getAdhocItems(null);

        StringBuilder csv = new StringBuilder();
        csv.append("Emp ID,Name,Email,Active,Projects,Change Requests,Open Adhoc,Assigned Projects\n");

        for (Developer developer : all) {
            List<String> projects = new ArrayList<>();
            for (Map.Entry<String, String> entry : owners.entrySet()) {
                if (developer.name.equals(entry.getValue())) projects.add(entry.getKey());
            }

            int crCount = 0;
            for (ChangeRequest cr : allChangeRequests) {
                if (developer.name.equals(cr.assignedTo)) crCount++;
            }

            int openAdhoc = 0;
            for (AdhocItem item : allAdhoc) {
                if (developer.name.equals(item.developerName) && !AdhocItem.isClosed(item.status)) {
                    openAdhoc++;
                }
            }

            csv.append(escape(developer.empId)).append(',')
                    .append(escape(developer.name)).append(',')
                    .append(escape(developer.email)).append(',')
                    .append(developer.active ? "Yes" : "No").append(',')
                    .append(projects.size()).append(',')
                    .append(crCount).append(',')
                    .append(openAdhoc).append(',')
                    .append(escape(String.join("; ", projects)))
                    .append('\n');
        }

        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(csv.toString()), null);
            JOptionPane.showMessageDialog(this,
                    "Copied " + all.size() + " developer" + (all.size() == 1 ? "" : "s")
                            + " to the clipboard as CSV.",
                    "Copied", JOptionPane.INFORMATION_MESSAGE);
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not write to the clipboard:\n" + ex.getMessage(),
                    "Copy failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Quotes a CSV field only when it needs it, doubling any inner quotes. */
    private static String escape(String value) {
        String text = value == null ? "" : value;
        if (text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")) {
            return '"' + text.replace("\"", "\"\"") + '"';
        }
        return text;
    }

    private void selectByName(String name) {
        if (name == null) return;
        for (int i = 0; i < developers.size(); i++) {
            if (name.equals(developers.get(i).name)) {
                developerTable.setRowSelectionInterval(i, i);
                onDeveloperSelected();
                return;
            }
        }
    }

    // --------------------------------------------------------------- widgets

    private JPanel adhocButtons() {
        JButton addBtn = UiFactory.secondary("Add Item", AppIcons.plus(15, "App.subtleForeground"));
        addBtn.addActionListener(e -> {
            if (selectedDeveloper == null) {
                JOptionPane.showMessageDialog(this, "Select a developer first.",
                        "No developer selected", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            adhocModel.addRow(new Object[]{"", "New", "Medium", "", "", "", ""});
            int last = adhocModel.getRowCount() - 1;
            adhocTable.setRowSelectionInterval(last, last);
            adhocTable.editCellAt(last, 0);
            Component editor = adhocTable.getEditorComponent();
            if (editor != null) editor.requestFocusInWindow();
        });

        JButton removeBtn = UiFactory.secondary("Remove Item",
                AppIcons.trash(15, "App.subtleForeground"));
        removeBtn.addActionListener(e -> {
            int row = adhocTable.getSelectedRow();
            if (row < 0) return;
            stopEditing();
            adhocModel.removeRow(adhocTable.convertRowIndexToModel(row));
        });

        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        return buttons;
    }

    private JPanel tabCard(JTable table, JPanel buttons) {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, 8));
        panel.setBorder(new javax.swing.border.EmptyBorder(8, 0, 0, 0));

        JPanel card = UiFactory.card(new BorderLayout(), 5);
        card.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        panel.add(card, BorderLayout.CENTER);
        if (buttons != null) panel.add(buttons, BorderLayout.SOUTH);
        return panel;
    }

    private JTable table(DefaultTableModel model, int[] widths) {
        JTable table = new JTable(model);
        UiFactory.styleTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        return table;
    }

    private void setComboEditor(JTable target, int columnIndex, String[] values) {
        JComboBox<String> combo = new JComboBox<>(values);
        target.getColumnModel().getColumn(columnIndex).setCellEditor(new DefaultCellEditor(combo));
    }

    private static String text(DefaultTableModel model, int row, int column) {
        Object value = model.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }

    private void stopEditing() {
        for (JTable table : new JTable[]{developerTable, projectTable, crTable, adhocTable}) {
            if (table != null && table.isEditing() && table.getCellEditor() != null) {
                table.getCellEditor().stopCellEditing();
            }
        }
    }
}
