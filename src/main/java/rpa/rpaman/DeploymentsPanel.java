package rpa.rpaman;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Deployment requests for one RPA, including the RITM raised for each.
 * <p>
 * Only the selected project's deployments are shown; new requests are normally
 * raised through {@link DeploymentRequestDialog}.
 */
public class DeploymentsPanel extends JPanel {

    private static final String[] COLUMNS = {
            "Deployment", "RITM #", "Environment", "Status", "Requested", "Deployed",
            "Requested By", "Code Moved to Test", "Change Description", "Tasks to Deploy",
            "Test Log Path", "Code Analysis Path", "Notes"
    };
    private static final int[] WIDTHS = {
            180, 120, 110, 120, 100, 100, 130, 140, 260, 260, 200, 200, 200
    };

    private final DatabaseManager dbManager;
    private final DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summaryLabel;

    /** Backing rows, index-aligned with the table model, so ids survive edits. */
    private final List<Deployment> deployments = new ArrayList<>();

    private String loadedProject;

    public DeploymentsPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setOpaque(false);
        setLayout(new BorderLayout(0, UiFactory.GAP));

        tableModel = new DefaultTableModel(COLUMNS, 0);
        table = new JTable(tableModel);
        UiFactory.styleTable(table);
        // Thirteen columns will not fit, so keep natural widths and scroll sideways
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);
        for (int i = 0; i < WIDTHS.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
        }
        setComboEditor(2, Deployment.ENVIRONMENTS);
        setComboEditor(3, Deployment.STATUSES);
        setComboEditor(7, Deployment.YES_NO);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);

        // ---------------------------------------------------------- toolbar
        JButton submitBtn = UiFactory.secondary("Submit Deployment Request...",
                AppIcons.upload(15, "App.subtleForeground"));
        submitBtn.addActionListener(e -> submitRequest());

        JButton addBtn = UiFactory.secondary("Add Row",
                AppIcons.plus(15, "App.subtleForeground"));
        addBtn.addActionListener(e -> addRow());

        JButton removeBtn = UiFactory.secondary("Remove Selected",
                AppIcons.trash(15, "App.subtleForeground"));
        removeBtn.addActionListener(e -> removeSelectedRow());

        JButton saveBtn = UiFactory.primary("Save Deployments",
                AppIcons.save(15, "App.onAccent"));
        saveBtn.addActionListener(e -> save(true));

        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(saveBtn);
        buttons.add(submitBtn);
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

    /** Shows only the deployments belonging to {@code projectName}. */
    public void load(String projectName) {
        stopEditing();
        loadedProject = projectName;

        deployments.clear();
        deployments.addAll(dbManager.getDeployments(projectName));

        tableModel.setRowCount(0);
        for (Deployment d : deployments) {
            tableModel.addRow(toRow(d));
        }
        updateSummary();
    }

    private Object[] toRow(Deployment d) {
        return new Object[]{
                d.name, d.ritmNumber, d.environment, d.status, d.requestedDate, d.deployedDate,
                d.requestedBy, d.codeMovedToTest, d.changeDescription, d.tasksToDeploy,
                d.testLogPath, d.codeAnalysisPath, d.notes
        };
    }

    // ------------------------------------------------------------------ save

    public void save(boolean notifyUser) {
        if (loadedProject == null) return;
        stopEditing();

        for (int row = 0; row < tableModel.getRowCount() && row < deployments.size(); row++) {
            Deployment d = deployments.get(row);
            d.projectName = loadedProject;
            d.name = text(row, 0);
            d.ritmNumber = text(row, 1);
            d.environment = text(row, 2);
            d.status = text(row, 3);
            d.requestedDate = text(row, 4);
            d.deployedDate = text(row, 5);
            d.requestedBy = text(row, 6);
            d.codeMovedToTest = text(row, 7);
            d.changeDescription = text(row, 8);
            d.tasksToDeploy = text(row, 9);
            d.testLogPath = text(row, 10);
            d.codeAnalysisPath = text(row, 11);
            d.notes = text(row, 12);
        }

        dbManager.saveDeployments(loadedProject, deployments);
        int count = deployments.size();
        load(loadedProject);

        if (notifyUser) {
            JOptionPane.showMessageDialog(this,
                    "Saved " + count + " deployment" + (count == 1 ? "" : "s") + ".",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // --------------------------------------------------------------- editing

    /** Opens the request form pre-filled with this project, then reloads. */
    private void submitRequest() {
        if (loadedProject == null) return;
        save(false);

        Window owner = SwingUtilities.getWindowAncestor(this);
        Frame frame = (owner instanceof Frame) ? (Frame) owner : null;

        DeploymentRequestDialog dialog =
                new DeploymentRequestDialog(frame, dbManager, loadedProject);
        dialog.setVisible(true);

        if (dialog.isSubmitted()) {
            load(loadedProject);
        }
    }

    private void addRow() {
        if (loadedProject == null) return;
        stopEditing();

        Deployment deployment = new Deployment();
        deployment.projectName = loadedProject;
        deployment.requestedBy = System.getProperty("user.name", "");
        deployments.add(deployment);
        tableModel.addRow(toRow(deployment));

        int last = tableModel.getRowCount() - 1;
        table.setRowSelectionInterval(last, last);
        table.scrollRectToVisible(table.getCellRect(last, 0, true));
        table.editCellAt(last, 0);
        Component editor = table.getEditorComponent();
        if (editor != null) editor.requestFocusInWindow();

        updateSummary();
    }

    private void removeSelectedRow() {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a deployment to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        stopEditing();

        int modelRow = table.convertRowIndexToModel(row);
        String label = text(modelRow, 0);
        if (label.isEmpty()) label = text(modelRow, 1);

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove " + (label.isEmpty() ? "this deployment" : "\"" + label + "\"") + "?\n"
                        + "Any change request linked to it will be unlinked.",
                "Remove Deployment", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        deployments.remove(modelRow);
        tableModel.removeRow(modelRow);
        updateSummary();
    }

    private void updateSummary() {
        int total = tableModel.getRowCount();
        int deployed = 0;
        int withoutRitm = 0;
        for (int row = 0; row < total; row++) {
            if ("Deployed".equalsIgnoreCase(text(row, 3))) deployed++;
            if (text(row, 1).isEmpty()) withoutRitm++;
        }
        summaryLabel.setText(total + " deployment" + (total == 1 ? "" : "s")
                + "  -  " + deployed + " deployed, " + withoutRitm + " without a RITM."
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
