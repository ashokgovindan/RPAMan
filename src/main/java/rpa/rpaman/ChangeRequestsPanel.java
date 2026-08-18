package rpa.rpaman;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Editable list of change requests for one RPA.
 * <p>
 * Each row can be linked to a deployment, which is how a CR is traced through
 * to the release that delivered it.
 */
public class ChangeRequestsPanel extends JPanel {

    private static final String NO_DEPLOYMENT = "(not linked)";

    /** Model columns, in display order. */
    private static final String[] COLUMNS = {
            "CR Number", "Title", "Assigned To", "Requested By", "Received", "Priority",
            "Status", "Target", "Delivered", "Deployment", "Notes"
    };
    private static final int[] WIDTHS = {110, 240, 150, 130, 100, 90, 130, 100, 100, 170, 240};

    private final DatabaseManager dbManager;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summaryLabel;

    /** Deployment ids in the same order as the picker's entries; index 0 is "none". */
    private final List<Integer> deploymentIds = new ArrayList<>();
    private final JComboBox<String> deploymentEditor = new JComboBox<>();

    /** Developer picker for the Assigned To column; first entry means unassigned. */
    private final JComboBox<String> developerEditor = new JComboBox<>();

    private String loadedProject;

    public ChangeRequestsPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setOpaque(false);
        setLayout(new BorderLayout(0, UiFactory.GAP));

        tableModel = new DefaultTableModel(COLUMNS, 0);
        table = new JTable(tableModel);
        UiFactory.styleTable(table);
        // Ten columns will not fit, so keep natural widths and scroll sideways
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);

        for (int i = 0; i < WIDTHS.length; i++) {
            TableColumn column = table.getColumnModel().getColumn(i);
            column.setPreferredWidth(WIDTHS[i]);
        }
        setComboEditor(5, ChangeRequest.PRIORITIES);
        setComboEditor(6, ChangeRequest.STATUSES);
        table.getColumnModel().getColumn(2).setCellEditor(new DefaultCellEditor(developerEditor));
        table.getColumnModel().getColumn(9).setCellEditor(new DefaultCellEditor(deploymentEditor));

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);

        // ---------------------------------------------------------- toolbar
        JButton addBtn = UiFactory.secondary("Add Change Request",
                AppIcons.plus(15, "App.subtleForeground"));
        addBtn.addActionListener(e -> addRow());

        JButton removeBtn = UiFactory.secondary("Remove Selected",
                AppIcons.trash(15, "App.subtleForeground"));
        removeBtn.addActionListener(e -> removeSelectedRow());

        JButton saveBtn = UiFactory.primary("Save Change Requests",
                AppIcons.save(15, "App.onAccent"));
        saveBtn.addActionListener(e -> save(true));

        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(saveBtn);
        buttons.add(addBtn);
        buttons.add(removeBtn);

        summaryLabel = UiFactory.subtitle("");
        JPanel toolbar = UiFactory.transparent(new BorderLayout(0, 6));
        toolbar.add(buttons, BorderLayout.NORTH);
        toolbar.add(summaryLabel, BorderLayout.CENTER);

        add(tableCard, BorderLayout.CENTER);
        add(toolbar, BorderLayout.SOUTH);
    }

    private void setComboEditor(int columnIndex, String[] values) {
        JComboBox<String> combo = new JComboBox<>(values);
        table.getColumnModel().getColumn(columnIndex).setCellEditor(new DefaultCellEditor(combo));
    }

    // ------------------------------------------------------------------ load

    /** Replaces the contents with the change requests of {@code projectName}. */
    public void load(String projectName) {
        stopEditing();
        loadedProject = projectName;

        // Rebuild the deployment picker from this project's deployments
        deploymentIds.clear();
        deploymentEditor.removeAllItems();
        deploymentIds.add(0);
        deploymentEditor.addItem(NO_DEPLOYMENT);
        for (Deployment deployment : dbManager.getDeployments(projectName)) {
            deploymentIds.add(deployment.id);
            deploymentEditor.addItem(deployment.label());
        }

        // Developers can be added at any time, so refresh the picker on load
        developerEditor.removeAllItems();
        developerEditor.addItem("");
        for (String developer : dbManager.getDeveloperNames()) {
            developerEditor.addItem(developer);
        }

        tableModel.setRowCount(0);
        List<ChangeRequest> requests = dbManager.getChangeRequests(projectName);
        for (ChangeRequest cr : requests) {
            tableModel.addRow(new Object[]{
                    cr.crNumber, cr.title, cr.assignedTo, cr.requestedBy, cr.receivedDate,
                    cr.priority, cr.status, cr.targetDate, cr.deliveredDate,
                    labelFor(cr.deploymentId), cr.notes
            });
        }
        updateSummary();
    }

    private String labelFor(int deploymentId) {
        int index = deploymentIds.indexOf(deploymentId);
        if (index <= 0) return NO_DEPLOYMENT;
        return String.valueOf(deploymentEditor.getItemAt(index));
    }

    private int deploymentIdFor(Object label) {
        if (label == null) return 0;
        for (int i = 1; i < deploymentEditor.getItemCount(); i++) {
            if (label.toString().equals(deploymentEditor.getItemAt(i))) {
                return deploymentIds.get(i);
            }
        }
        return 0;
    }

    // ------------------------------------------------------------------ save

    /** Writes the table back to the database. Safe to call when nothing is loaded. */
    public void save(boolean notifyUser) {
        if (loadedProject == null) return;
        stopEditing();

        List<ChangeRequest> requests = new ArrayList<>();
        for (int row = 0; row < tableModel.getRowCount(); row++) {
            ChangeRequest cr = new ChangeRequest();
            cr.projectName = loadedProject;
            cr.crNumber = text(row, 0);
            cr.title = text(row, 1);
            cr.assignedTo = text(row, 2);
            cr.requestedBy = text(row, 3);
            cr.receivedDate = text(row, 4);
            cr.priority = text(row, 5);
            cr.status = text(row, 6);
            cr.targetDate = text(row, 7);
            cr.deliveredDate = text(row, 8);
            cr.deploymentId = deploymentIdFor(tableModel.getValueAt(row, 9));
            cr.notes = text(row, 10);

            if (!cr.crNumber.isEmpty() || !cr.title.isEmpty()) {
                requests.add(cr);
            }
        }

        dbManager.saveChangeRequests(loadedProject, requests);
        updateSummary();

        if (notifyUser) {
            JOptionPane.showMessageDialog(this,
                    "Saved " + requests.size() + " change request"
                            + (requests.size() == 1 ? "" : "s") + ".",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --------------------------------------------------------------- editing

    private void addRow() {
        if (loadedProject == null) return;
        stopEditing();
        tableModel.addRow(new Object[]{
                "", "", "", "", "", "Medium", "New", "", "", NO_DEPLOYMENT, ""});

        int last = tableModel.getRowCount() - 1;
        table.setRowSelectionInterval(last, last);
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
        table.editCellAt(last, 0);
        Component editor = table.getEditorComponent();
        if (editor != null) editor.requestFocusInWindow();
    }

    private void removeSelectedRow() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a change request to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        stopEditing();

        int modelRow = table.convertRowIndexToModel(row);
        String label = text(modelRow, 0);
        if (label.isEmpty()) label = text(modelRow, 1);

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + (label.isEmpty() ? "this change request" : "\"" + label + "\"") + "?",
                "Remove Change Request", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        tableModel.removeRow(modelRow);
        updateSummary();
    }

    private void updateSummary() {
        int total = tableModel.getRowCount();
        int delivered = 0;
        int open = 0;
        for (int row = 0; row < total; row++) {
            String status = text(row, 6);
            if ("Deployed".equalsIgnoreCase(status)) {
                delivered++;
            } else if (!"Rejected".equalsIgnoreCase(status)) {
                open++;
            }
        }
        summaryLabel.setText(total + " change request" + (total == 1 ? "" : "s")
                + "  -  " + open + " open, " + delivered + " delivered."
                + "   Dates use yyyy-MM-dd.");
    }

    private String text(int row, int column) {
        Object value = tableModel.getValueAt(row, column);
        return value == null ? "" : value.toString().trim();
    }

    private void stopEditing() {
        if (table.isEditing() && table.getCellEditor() != null) {
            table.getCellEditor().stopCellEditing();
        }
    }
}
