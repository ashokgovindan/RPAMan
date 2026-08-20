package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.regex.Pattern;

public class RightTasksPane extends JPanel {

    /** What the status dropdown returns to once an entry is saved. */
    private static final String DEFAULT_STATUS = "To Do";

    private final DatabaseManager dbManager;
    private final DefaultTableModel tableModel;
    private final JTable taskTable;

    // Form controls, held so the edit and reset helpers can reach them
    private final JTextField descField;
    private final DatePicker datePicker;
    private final JComboBox<String> statusCombo;
    private final JButton addTaskBtn;

    private int selectedTaskId = -1;

    public RightTasksPane(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setLayout(new BorderLayout(0, UiFactory.GAP));
        setOpaque(true);
        setBackground(ThemeManager.color("App.canvas", getBackground()));
        setBorder(new EmptyBorder(UiFactory.GAP, UiFactory.GAP, UiFactory.GAP, UiFactory.GAP));
        ThemeManager.onThemeChanged(() ->
                setBackground(ThemeManager.color("App.canvas", getBackground())));

        // ------------------------------------------------- heading + search
        JLabel titleLabel = UiFactory.sectionTitle("Tasks", AppIcons.checklist(17, "App.accent"));
        JTextField searchField = UiFactory.searchField("Search tasks...");

        add(UiFactory.headerBlock(titleLabel, searchField), BorderLayout.NORTH);

        // ------------------------------------------------------------ table
        String[] columns = {"ID", "Description", "Due Date", "Status"};
        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        taskTable = new JTable(tableModel);
        taskTable.removeColumn(taskTable.getColumnModel().getColumn(0));
        UiFactory.styleTable(taskTable);

        // Filter across description, due date and status as the user types
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(tableModel);
        taskTable.setRowSorter(sorter);
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                String query = searchField.getText().trim();
                if (query.isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote(query), 1, 2, 3));
                }
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
        // Status column (view index 2 after the hidden ID column) renders as a pill
        taskTable.getColumnModel().getColumn(2).setCellRenderer(UiFactory.statusRenderer());
        taskTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        taskTable.getColumnModel().getColumn(1).setPreferredWidth(110);
        taskTable.getColumnModel().getColumn(2).setPreferredWidth(110);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(taskTable), BorderLayout.CENTER);
        add(tableCard, BorderLayout.CENTER);

        // ------------------------------------------------------- entry form
        JPanel formPanel = UiFactory.card(new GridBagLayout(), 14);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.weightx = 1.0;

        descField = UiFactory.textField("Task description");
        datePicker = UiFactory.datePicker();
        statusCombo = UiFactory.comboBox(new String[]{DEFAULT_STATUS, "In Progress", "Done"});
        addTaskBtn = UiFactory.primary("Add Task", AppIcons.plus(15, "App.onAccent"));

        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 2;
        formPanel.add(UiFactory.fieldLabel("New Task"), gbc);

        gbc.gridy = 1;
        formPanel.add(descField, gbc);

        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.insets = new Insets(4, 0, 4, 6);
        formPanel.add(datePicker, gbc);

        gbc.gridx = 1;
        gbc.insets = new Insets(4, 6, 4, 0);
        formPanel.add(statusCombo, gbc);

        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.insets = new Insets(12, 0, 0, 0);
        formPanel.add(addTaskBtn, gbc);

        add(formPanel, BorderLayout.SOUTH);

        loadTasksFromDatabase();

        // --------------------------------------------------------- behaviour
        // Double-click loads a task into the form; a single click just selects,
        // so clicking around the list no longer disturbs what is being typed.
        taskTable.setToolTipText("Double-click a task to edit it");
        taskTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) editSelectedTask();
            }
        });

        addTaskBtn.addActionListener(e -> {
            String desc = descField.getText();
            String date = datePicker.getDateStringOrEmptyString(); // format YYYY-MM-DD
            String status = (String) statusCombo.getSelectedItem();

            if (desc == null || desc.trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please enter a task description.",
                        "Task description required",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            if (selectedTaskId == -1) {
                dbManager.addTask(desc, date, status);
            } else {
                dbManager.updateTask(selectedTaskId, desc, date, status);
            }

            resetForm();
            loadTasksFromDatabase();
        });
    }

    /** Loads the double-clicked task into the form for editing. */
    private void editSelectedTask() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return;

        // The table is filtered/sortable, so map the view row back to the model
        int row = taskTable.convertRowIndexToModel(viewRow);
        selectedTaskId = (int) tableModel.getValueAt(row, 0);
        descField.setText(asText(tableModel.getValueAt(row, 1)));

        String dueDate = asText(tableModel.getValueAt(row, 2));
        if (dueDate.isEmpty()) {
            datePicker.clear();
        } else {
            datePicker.setText(dueDate);
        }

        // Keep the task's real status so it is visible and only changes on purpose
        statusCombo.setSelectedItem(tableModel.getValueAt(row, 3));

        addTaskBtn.setText("Update Task");
        addTaskBtn.setIcon(AppIcons.check(15, "App.onAccent"));
        descField.requestFocusInWindow();
    }

    /** Returns the form to its defaults, ready for the next new task. */
    private void resetForm() {
        selectedTaskId = -1;
        addTaskBtn.setText("Add Task");
        addTaskBtn.setIcon(AppIcons.plus(15, "App.onAccent"));
        descField.setText("");
        datePicker.clear();
        statusCombo.setSelectedItem(DEFAULT_STATUS);
        taskTable.clearSelection();
    }

    private static String asText(Object value) {
        return value == null ? "" : value.toString();
    }

    private void loadTasksFromDatabase() {
        tableModel.setRowCount(0);
        List<Object[]> tasks = dbManager.getAllTasks();
        for (Object[] task : tasks) {
            tableModel.addRow(task);
        }
    }
}
