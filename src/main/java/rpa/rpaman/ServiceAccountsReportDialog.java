package rpa.rpaman;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * Every service account across every RPA, in one sortable table.
 * <p>
 * Service accounts have no life cycle to split on, so unlike the change request
 * and deployment reports this is a single list with a filter box — the usual
 * question being "which project owns this account?".
 */
public class ServiceAccountsReportDialog extends JDialog {

    private static final String[] COLUMNS = {
            "RPA Name", "Environment", "Account ID", "Alias", "App Name", "Email", "Description"
    };
    private static final int[] WIDTHS = {180, 110, 190, 170, 180, 240, 300};

    private final DatabaseManager dbManager;
    private final DefaultTableModel tableModel;
    private final TableRowSorter<DefaultTableModel> sorter;
    private final JTable table;
    private final JLabel summaryLabel;
    /** Index-aligned with the model, so a selected row maps back to its record. */
    private final java.util.List<ServiceAccount> accounts = new java.util.ArrayList<>();

    public ServiceAccountsReportDialog(Frame owner, DatabaseManager dbManager,
                                       Runnable onAddAccount) {
        super(owner, "Service Accounts - All Projects", true);
        this.dbManager = dbManager;

        setSize(1120, 700);
        setMinimumSize(new Dimension(760, 500));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle("Service Accounts across all RPAs",
                AppIcons.key(17, "App.accent"));

        JTextField filterField = UiFactory.searchField("Filter accounts...");
        filterField.setPreferredSize(new Dimension(260, filterField.getPreferredSize().height));

        JPanel titleRow = UiFactory.transparent(new BorderLayout(12, 0));
        titleRow.add(titleLabel, BorderLayout.WEST);
        JPanel headerRight = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        headerRight.add(filterField);
        titleRow.add(headerRight, BorderLayout.EAST);

        // Actions sit on their own row under the title, where they are easy to
        // spot, rather than competing with the summary text in the footer.
        summaryLabel = UiFactory.subtitle("");

        JButton copyBtn = UiFactory.secondary("Copy as CSV",
                AppIcons.clipboard(15, "App.subtleForeground"));
        copyBtn.setToolTipText("Copy the rows currently shown, in the current sort order");
        copyBtn.addActionListener(e -> copyVisibleRows());

        JButton copySelectedBtn = UiFactory.primary("Copy Selected",
                AppIcons.clipboard(15, "App.onAccent"));
        copySelectedBtn.setToolTipText(
                "<html>Copy the selected rows as:<br>"
                        + "RPA Name, Account ID, Alias, App Name</html>");
        copySelectedBtn.addActionListener(e -> copySelectedDetails());

        JPanel actions = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        actions.add(copySelectedBtn);
        actions.add(copyBtn);
        if (onAddAccount != null) {
            JButton addBtn = UiFactory.secondary("Add Service Account...",
                    AppIcons.plus(15, "App.subtleForeground"));
            addBtn.addActionListener(e -> {
                dispose();
                onAddAccount.run();
            });
            actions.add(addBtn);
        }

        JPanel toolbar = UiFactory.transparent(new BorderLayout(0, 4));
        toolbar.add(actions, BorderLayout.NORTH);
        toolbar.add(summaryLabel, BorderLayout.CENTER);

        JPanel header = UiFactory.transparent(new BorderLayout(0, UiFactory.HEADER_GAP));
        header.add(titleRow, BorderLayout.NORTH);
        header.add(toolbar, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // ------------------------------------------------------------- table
        tableModel = new DefaultTableModel(COLUMNS, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(tableModel);
        UiFactory.styleTable(table);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(28);
        for (int i = 0; i < WIDTHS.length; i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(WIDTHS[i]);
        }

        sorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(sorter);
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                String query = filterField.getText().trim();
                sorter.setRowFilter(query.isEmpty() ? null
                        : RowFilter.regexFilter("(?i)" + Pattern.quote(query)));
                updateSummary(table.getRowCount());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }
        });

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        root.add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> dispose());
        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        load();
        updateSummary(table.getRowCount());
    }

    private void load() {
        accounts.clear();
        accounts.addAll(dbManager.getAllServiceAccounts());

        tableModel.setRowCount(0);
        for (ServiceAccount account : accounts) {
            tableModel.addRow(new Object[]{
                    account.projectName, account.environment, account.accountId,
                    account.alias, account.appName, account.email, account.description});
        }
    }

    /** Copies the selected rows as labelled blocks, one per account. */
    private void copySelectedDetails() {
        int[] selectedRows = table.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "Select one or more service accounts to copy.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder text = new StringBuilder();
        int copied = 0;
        for (int viewRow : selectedRows) {
            int modelRow = table.convertRowIndexToModel(viewRow);
            if (modelRow < 0 || modelRow >= accounts.size()) continue;
            if (text.length() > 0) text.append("\n\n");
            text.append(accounts.get(modelRow).detailsBlock());
            copied++;
        }

        CsvClipboard.copyPlain(this, text.toString(), copied == 1
                ? "the service account details"
                : CsvClipboard.plural(copied, "service account"));
    }

    /**
     * Copies what is on screen — filtered and in the displayed sort order —
     * rather than the whole table, so a narrowed search copies just that.
     */
    private void copyVisibleRows() {
        int visible = table.getRowCount();
        if (visible == 0) {
            JOptionPane.showMessageDialog(this, "There are no rows to copy.",
                    "Nothing to copy", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder csv = new StringBuilder();
        csv.append(CsvClipboard.row(COLUMNS));

        for (int row = 0; row < visible; row++) {
            String[] cells = new String[COLUMNS.length];
            for (int column = 0; column < COLUMNS.length; column++) {
                Object value = table.getValueAt(row, column);
                cells[column] = value == null ? "" : value.toString();
            }
            csv.append(CsvClipboard.row(cells));
        }

        CsvClipboard.copy(this, csv.toString(),
                CsvClipboard.plural(visible, "service account"));
    }

    private void updateSummary(int visible) {
        int total = tableModel.getRowCount();
        String text = visible == total
                ? total + " service account" + (total == 1 ? "" : "s") + "."
                : visible + " of " + total + " shown.";
        summaryLabel.setText(text + "   Tracking only - no passwords are stored.");
    }
}
