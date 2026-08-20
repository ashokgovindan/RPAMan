package rpa.rpaman;

import javax.swing.*;
import java.awt.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Form for raising a change request against any RPA.
 * <p>
 * Reached from the Change Requests menu, so the project is chosen here rather
 * than inherited from the tree. Delivery fields (delivered date, the deployment
 * that shipped it) are left to the per-project grid once work starts.
 */
public class ChangeRequestDialog extends JDialog {

    private final JComboBox<String> projectCombo;
    private final JTextField crNumberField;
    private final JTextField titleField;
    private final JComboBox<String> assignedToCombo;
    private final JTextField requestedByField;
    private final JComboBox<String> priorityCombo;
    private final JComboBox<String> statusCombo;
    private final DatePicker receivedPicker;
    private final DatePicker targetPicker;
    private final JTextArea notesArea;

    private final ChangeRequest changeRequest = new ChangeRequest();
    private boolean saved;

    public ChangeRequestDialog(Frame owner, DatabaseManager dbManager, String preselectedProject) {
        super(owner, "New Change Request", true);

        setSize(720, 800);
        setMinimumSize(new Dimension(580, 640));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        JLabel titleLabel = UiFactory.sectionTitle("New Change Request",
                AppIcons.inbox(17, "App.accent"));
        JLabel subtitle = UiFactory.subtitle(
                "The request appears under the chosen project's Change Requests node.");
        JPanel header = UiFactory.transparent(new BorderLayout(0, UiFactory.HEADER_GAP));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        root.add(header, BorderLayout.NORTH);

        // -------------------------------------------------------------- form
        JPanel form = UiFactory.card(new GridBagLayout(), 18);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(4, 0, 4, 0);

        int y = 0;

        gbc.gridy = y++;
        form.add(UiFactory.fieldLabel("RPA Name"), gbc);
        List<String> projects = dbManager.getProjectNames();
        projectCombo = UiFactory.comboBox(projects.toArray(new String[0]));
        if (preselectedProject != null && projects.contains(preselectedProject)) {
            projectCombo.setSelectedItem(preselectedProject);
        }
        gbc.gridy = y++;
        form.add(projectCombo, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("CR Number"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        crNumberField = UiFactory.textField("Change request reference");
        gbc.gridy = y++;
        form.add(crNumberField, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Title"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        titleField = UiFactory.textField("Short summary of the change");
        gbc.gridy = y++;
        form.add(titleField, gbc);

        // Priority and status share a row
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Priority  /  Status"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        priorityCombo = UiFactory.comboBox(ChangeRequest.PRIORITIES);
        priorityCombo.setSelectedItem("Medium");
        statusCombo = UiFactory.comboBox(ChangeRequest.STATUSES);
        statusCombo.setSelectedItem("New");

        JPanel priorityRow = UiFactory.transparent(new GridLayout(1, 2, 8, 0));
        priorityRow.add(priorityCombo);
        priorityRow.add(statusCombo);
        gbc.gridy = y++;
        form.add(priorityRow, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Assigned To  /  Requested By"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        assignedToCombo = UiFactory.comboBox(new String[]{""});
        for (String developer : dbManager.getDeveloperNames()) {
            assignedToCombo.addItem(developer);
        }
        requestedByField = UiFactory.textField("Who raised it");

        JPanel ownerRow = UiFactory.transparent(new GridLayout(1, 2, 8, 0));
        ownerRow.add(assignedToCombo);
        ownerRow.add(requestedByField);
        gbc.gridy = y++;
        form.add(ownerRow, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Received  /  Target"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        receivedPicker = UiFactory.datePicker();
        receivedPicker.setDate(LocalDate.now());
        targetPicker = UiFactory.datePicker();

        JPanel dateRow = UiFactory.transparent(new GridLayout(1, 2, 8, 0));
        dateRow.add(receivedPicker);
        dateRow.add(targetPicker);
        gbc.gridy = y++;
        form.add(dateRow, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Notes"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        notesArea = UiFactory.textArea(4, 20);
        gbc.gridy = y;
        form.add(UiFactory.fixedHeightScroll(notesArea, 96), gbc);

        root.add(UiFactory.formScroll(form), BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton createBtn = UiFactory.primary("Create Change Request",
                AppIcons.check(15, "App.onAccent"));
        createBtn.addActionListener(e -> commit());

        JButton cancelBtn = UiFactory.secondary("Cancel", null);
        cancelBtn.addActionListener(e -> dispose());

        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.add(cancelBtn);
        footer.add(createBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(createBtn);
    }

    public boolean isSaved() {
        return saved;
    }

    public ChangeRequest getChangeRequest() {
        return changeRequest;
    }

    private void commit() {
        Object project = projectCombo.getSelectedItem();
        if (project == null || project.toString().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select the RPA this change request is for.\n"
                            + "If the list is empty, create a project first via File > New Project.",
                    "RPA required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (crNumberField.getText().trim().isEmpty() && titleField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Enter a CR number or a title.",
                    "Details required", JOptionPane.WARNING_MESSAGE);
            crNumberField.requestFocusInWindow();
            return;
        }

        changeRequest.projectName = project.toString();
        changeRequest.crNumber = crNumberField.getText().trim();
        changeRequest.title = titleField.getText().trim();
        Object assignee = assignedToCombo.getSelectedItem();
        changeRequest.assignedTo = assignee == null ? "" : assignee.toString().trim();
        changeRequest.requestedBy = requestedByField.getText().trim();
        changeRequest.priority = String.valueOf(priorityCombo.getSelectedItem());
        changeRequest.status = String.valueOf(statusCombo.getSelectedItem());
        changeRequest.receivedDate = receivedPicker.getDateStringOrEmptyString();
        changeRequest.targetDate = targetPicker.getDateStringOrEmptyString();
        changeRequest.notes = notesArea.getText().trim();

        saved = true;
        dispose();
    }
}
