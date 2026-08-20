package rpa.rpaman;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only list of the service accounts an RPA runs under, maintained through
 * Add, Edit and Delete.
 * <p>
 * Each action writes straight to the database, so there is nothing to save and
 * nothing to lose when navigating away.
 */
public class ServiceAccountsPanel extends JPanel {

    private static final String[] COLUMNS = {
            "Environment", "Account ID", "Alias", "App Name", "Email", "Description"
    };
    private static final int[] WIDTHS = {110, 190, 170, 170, 230, 320};

    private final DatabaseManager dbManager;
    private final javax.swing.table.DefaultTableModel tableModel;
    private final JTable table;
    private final JLabel summaryLabel;

    /** Backing rows, index-aligned with the table model so ids stay available. */
    private final List<ServiceAccount> accounts = new ArrayList<>();

    private String loadedProject;

    public ServiceAccountsPanel(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setOpaque(false);
        setLayout(new BorderLayout(0, UiFactory.GAP));

        tableModel = new javax.swing.table.DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // edited through the dialog, not in place
            }
        };
        table = new JTable(tableModel);
        UiFactory.styleTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(26);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        for (int i = 0; i < WIDTHS.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelected();
            }
        });

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);

        // ---------------------------------------------------------- toolbar
        JButton editBtn = UiFactory.primary("Edit", AppIcons.sliders(15, "App.onAccent"));
        editBtn.addActionListener(e -> editSelected());

        JButton deleteBtn = UiFactory.secondary("Delete",
                AppIcons.trash(15, "App.subtleForeground"));
        deleteBtn.addActionListener(e -> deleteSelected());

        JButton copySelectedBtn = UiFactory.secondary("Copy Selected",
                AppIcons.clipboard(15, "App.subtleForeground"));
        copySelectedBtn.setToolTipText(
                "<html>Copy the selected account as:<br>"
                        + "RPA Name, Account ID, Alias, App Name</html>");
        copySelectedBtn.addActionListener(e -> copySelectedDetails());

        JButton copyBtn = UiFactory.secondary("Copy as CSV",
                AppIcons.clipboard(15, "App.subtleForeground"));
        copyBtn.setToolTipText("Copy the selected account, or all of them when nothing is selected");
        copyBtn.addActionListener(e -> copyToClipboard());

        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttons.add(editBtn);
        buttons.add(deleteBtn);
        buttons.add(copySelectedBtn);
        buttons.add(copyBtn);

        summaryLabel = UiFactory.subtitle("");
        JPanel toolbar = UiFactory.transparent(new BorderLayout(0, 6));
        toolbar.add(buttons, BorderLayout.NORTH);
        toolbar.add(summaryLabel, BorderLayout.CENTER);

        add(tableCard, BorderLayout.CENTER);
        add(toolbar, BorderLayout.SOUTH);
    }

    // ------------------------------------------------------------------ load

    public void load(String projectName) {
        loadedProject = projectName;

        accounts.clear();
        accounts.addAll(dbManager.getServiceAccounts(projectName));

        tableModel.setRowCount(0);
        for (ServiceAccount account : accounts) {
            tableModel.addRow(new Object[]{
                    account.environment, account.accountId, account.alias,
                    account.appName, account.email, account.description});
        }
        updateSummary();
    }

    // --------------------------------------------------------------- actions

    private void editSelected() {
        ServiceAccount selected = selectedAccount();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a service account to edit.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ServiceAccountDialog dialog = new ServiceAccountDialog(
                SwingUtilities.getWindowAncestor(this), loadedProject, selected);
        dialog.setVisible(true);
        if (!dialog.isSaved()) return;

        dbManager.updateServiceAccount(selected);
        load(loadedProject);
        selectByAccountId(selected.accountId);
    }

    private void deleteSelected() {
        ServiceAccount selected = selectedAccount();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a service account to delete.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String label = selected.accountId.isEmpty() ? "this service account"
                : "\"" + selected.accountId + "\"";
        int choice = JOptionPane.showConfirmDialog(this,
                "Delete " + label + "?",
                "Delete Service Account", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        dbManager.deleteServiceAccount(selected.id);
        load(loadedProject);
    }

    /** Copies just the selected account as a labelled block. */
    private void copySelectedDetails() {
        ServiceAccount selected = selectedAccount();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Select a service account to copy.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        CsvClipboard.copyPlain(this, selected.detailsBlock(), "the service account details");
    }

    /**
     * Copies the selected account, or every account for the project when
     * nothing is selected. The RPA name leads each row so the CSV still makes
     * sense once it is out of the app.
     */
    private void copyToClipboard() {
        if (accounts.isEmpty()) {
            JOptionPane.showMessageDialog(this, "There are no service accounts to copy.",
                    "Nothing to copy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        ServiceAccount selected = selectedAccount();
        List<ServiceAccount> toCopy = new ArrayList<>();
        if (selected != null) {
            toCopy.add(selected);
        } else {
            toCopy.addAll(accounts);
        }

        StringBuilder csv = new StringBuilder();
        csv.append(CsvClipboard.row(
                "RPA Name", "Environment", "Account ID", "Alias", "App Name", "Email", "Description"));
        for (ServiceAccount account : toCopy) {
            csv.append(CsvClipboard.row(
                    account.projectName, account.environment, account.accountId,
                    account.alias, account.appName, account.email, account.description));
        }

        CsvClipboard.copy(this, csv.toString(),
                CsvClipboard.plural(toCopy.size(), "service account"));
    }

    private ServiceAccount selectedAccount() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return modelRow < accounts.size() ? accounts.get(modelRow) : null;
    }

    private void selectByAccountId(String accountId) {
        for (int i = 0; i < accounts.size(); i++) {
            if (accounts.get(i).accountId.equals(accountId)) {
                table.setRowSelectionInterval(i, i);
                table.scrollRectToVisible(table.getCellRect(i, 0, true));
                return;
            }
        }
    }

    private void updateSummary() {
        int total = accounts.size();
        summaryLabel.setText(total + " service account" + (total == 1 ? "" : "s")
                + ".   Double-click a row to edit.   Add new ones from the Service Accounts menu."
                + "   Tracking only - no passwords are stored.");
    }
}
