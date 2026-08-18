package rpa.rpaman;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-project report split into two stacked sections: work still in flight on
 * top, finished work underneath.
 * <p>
 * Subclasses only decide which rows go where; the layout, sorting, counts and
 * refresh handling live here.
 */
public abstract class StatusSplitDialog extends JDialog {

    protected final DatabaseManager dbManager;

    private final String[] columns;
    private final int[] widths;
    private final int statusColumn;

    private final DefaultTableModel activeModel;
    private final DefaultTableModel closedModel;
    private final JTable activeTable;
    private final JTable closedTable;
    private final JLabel activeLabel;
    private final JLabel closedLabel;
    private final String activeTitle;
    private final String closedTitle;

    protected StatusSplitDialog(Frame owner, DatabaseManager dbManager,
                                String windowTitle, String heading, Icon icon,
                                String activeTitle, String closedTitle,
                                String[] columns, int[] widths, int statusColumn) {
        super(owner, windowTitle, true);
        this.dbManager = dbManager;
        this.columns = columns;
        this.widths = widths;
        this.statusColumn = statusColumn;
        this.activeTitle = activeTitle;
        this.closedTitle = closedTitle;

        setSize(1180, 760);
        setMinimumSize(new Dimension(820, 560));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle(heading, icon);
        JButton refreshBtn = UiFactory.secondary("Refresh", AppIcons.refresh(15, "App.subtleForeground"));
        refreshBtn.addActionListener(e -> refresh());

        JPanel header = UiFactory.transparent(new BorderLayout(12, 0));
        header.add(titleLabel, BorderLayout.WEST);
        JPanel headerRight = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        headerRight.add(refreshBtn);
        header.add(headerRight, BorderLayout.EAST);
        root.add(header, BorderLayout.NORTH);

        // ----------------------------------------------------------- sections
        activeModel = readOnlyModel();
        activeTable = buildTable(activeModel);
        activeLabel = UiFactory.fieldLabel(activeTitle);

        closedModel = readOnlyModel();
        closedTable = buildTable(closedModel);
        closedLabel = UiFactory.fieldLabel(closedTitle);

        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,
                section(activeLabel, activeTable), section(closedLabel, closedTable));
        split.setResizeWeight(0.55);
        split.setBorder(null);
        split.setOpaque(false);
        split.setDividerSize(8);
        split.setContinuousLayout(true);
        root.add(split, BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> dispose());
        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        footer.add(closeBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
    }

    /** Subclasses must call this once their own fields are initialised. */
    protected final void refresh() {
        activeModel.setRowCount(0);
        closedModel.setRowCount(0);

        List<Object[]> active = new ArrayList<>();
        List<Object[]> closed = new ArrayList<>();
        loadRows(active, closed);

        for (Object[] row : active) activeModel.addRow(row);
        for (Object[] row : closed) closedModel.addRow(row);

        activeLabel.setText(activeTitle + "   (" + active.size() + ")");
        closedLabel.setText(closedTitle + "   (" + closed.size() + ")");
    }

    /**
     * Fills the two lists with display rows matching the configured columns.
     *
     * @param active rows still in progress or not yet started
     * @param closed rows that have reached a terminal status
     */
    protected abstract void loadRows(List<Object[]> active, List<Object[]> closed);

    // ---------------------------------------------------------------- widgets

    private DefaultTableModel readOnlyModel() {
        return new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
    }

    private JTable buildTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        UiFactory.styleTable(table);
        // Reports are wide, so keep column widths and scroll sideways
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(28);
        table.setRowSorter(new TableRowSorter<>(model));

        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
        if (statusColumn >= 0 && statusColumn < table.getColumnCount()) {
            table.getColumnModel().getColumn(statusColumn)
                    .setCellRenderer(UiFactory.badgeRenderer());
        }
        return table;
    }

    private JPanel section(JLabel label, JTable table) {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, 6));
        panel.add(label, BorderLayout.NORTH);

        JPanel card = UiFactory.card(new BorderLayout(), 5);
        card.add(UiFactory.bareScroll(table), BorderLayout.CENTER);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }
}
