package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableRowSorter;
import java.awt.*;
import java.util.List;
import java.util.regex.Pattern;

public class RightTasksPane extends JPanel {

    private final DatabaseManager dbManager;
    private final DefaultTableModel tableModel;
    private final JTable taskTable;
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

        JTextField descField = UiFactory.textField("Task description");
        DatePicker datePicker = UiFactory.datePicker();
        JComboBox<String> statusCombo = UiFactory.comboBox(new String[]{"To Do", "In Progress", "Done"});
        JButton addTaskBtn = UiFactory.primary("Add Task", AppIcons.plus(15, "App.onAccent"));

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
        taskTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting() || taskTable.getSelectedRow() == -1) return;

            // The table is filtered/sortable, so map the view row back to the model
            int row = taskTable.convertRowIndexToModel(taskTable.getSelectedRow());
            selectedTaskId = (int) tableModel.getValueAt(row, 0);
            descField.setText(asText(tableModel.getValueAt(row, 1)));

            String dueDate = asText(tableModel.getValueAt(row, 2));
            if (dueDate.isEmpty()) {
                datePicker.clear();
            } else {
                datePicker.setText(dueDate);
            }

            statusCombo.setSelectedItem(tableModel.getValueAt(row, 3));
            addTaskBtn.setText("Update Task");
            addTaskBtn.setIcon(AppIcons.check(15, "App.onAccent"));
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
                selectedTaskId = -1;
            }

            addTaskBtn.setText("Add Task");
            addTaskBtn.setIcon(AppIcons.plus(15, "App.onAccent"));
            descField.setText("");
            datePicker.clear();
            taskTable.clearSelection();
            loadTasksFromDatabase();
        });
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
