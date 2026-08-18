package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.LocalDate;
import java.util.List;

/**
 * Form used to raise a deployment request against an RPA.
 * <p>
 * Submitting inserts a {@link Deployment} with status "Requested", stamped with
 * the current Windows user so each person's requests are attributable.
 */
public class DeploymentRequestDialog extends JDialog {

    private final DatabaseManager dbManager;

    private final DatePicker requestDatePicker;
    private final JComboBox<String> rpaCombo;
    private final JTextField ritmField;
    private final JComboBox<String> environmentCombo;
    private final JTextArea changeDescriptionArea;
    private final JTextArea tasksArea;
    private final JTextField testLogPathField;
    private final JTextField codeAnalysisPathField;
    private final JComboBox<String> codeMovedCombo;
    private final JTextField requestedByField;

    private boolean submitted;

    public DeploymentRequestDialog(Frame owner, DatabaseManager dbManager, String preselectedProject) {
        super(owner, "Submit Deployment Request", true);
        this.dbManager = dbManager;

        setSize(720, 720);
        setMinimumSize(new Dimension(560, 560));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        // ------------------------------------------------------------ header
        JLabel titleLabel = UiFactory.sectionTitle("Deployment Request",
                AppIcons.upload(17, "App.accent"));
        JLabel subtitle = UiFactory.subtitle(
                "Raise a request for the selected RPA. It appears under that project's "
                        + "Deployments node.");
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

        // Request date + RPA name share a row
        gbc.gridy = y++;
        form.add(UiFactory.fieldLabel("Request Date"), gbc);

        requestDatePicker = UiFactory.datePicker();
        requestDatePicker.setDate(LocalDate.now());
        gbc.gridy = y++;
        form.add(requestDatePicker, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("RPA Name"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        List<String> projects = dbManager.getProjectNames();
        rpaCombo = UiFactory.comboBox(projects.toArray(new String[0]));
        if (preselectedProject != null && projects.contains(preselectedProject)) {
            rpaCombo.setSelectedItem(preselectedProject);
        }
        gbc.gridy = y++;
        form.add(rpaCombo, gbc);

        // RITM + environment
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("RITM #  /  Environment"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        ritmField = UiFactory.textField("RITM number (optional)");
        environmentCombo = UiFactory.comboBox(Deployment.ENVIRONMENTS);
        environmentCombo.setSelectedItem("PROD");

        JPanel ritmRow = UiFactory.transparent(new BorderLayout(8, 0));
        ritmRow.add(ritmField, BorderLayout.CENTER);
        ritmRow.add(environmentCombo, BorderLayout.EAST);
        gbc.gridy = y++;
        form.add(ritmRow, gbc);

        // Change description
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Change Description"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        changeDescriptionArea = UiFactory.textArea(4, 20);
        gbc.gridy = y++;
        form.add(UiFactory.scroll(changeDescriptionArea), gbc);

        // Tasks to deploy
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Tasks to Deploy"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        tasksArea = UiFactory.textArea(4, 20);
        gbc.gridy = y++;
        form.add(UiFactory.scroll(tasksArea), gbc);

        // Paths
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Test Log Path"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        testLogPathField = UiFactory.textField("Path to the test log");
        gbc.gridy = y++;
        form.add(pathRow(testLogPathField, "Select test log"), gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Code Analysis Path"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        codeAnalysisPathField = UiFactory.textField("Path to the code analysis report");
        gbc.gridy = y++;
        form.add(pathRow(codeAnalysisPathField, "Select code analysis report"), gbc);

        // Code moved to test + requested by
        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Code Moved to Test  /  Requested By"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        codeMovedCombo = UiFactory.comboBox(Deployment.YES_NO);
        codeMovedCombo.setSelectedItem("No");
        requestedByField = UiFactory.textField("Requested by");
        requestedByField.setText(System.getProperty("user.name", ""));

        JPanel movedRow = UiFactory.transparent(new BorderLayout(8, 0));
        movedRow.add(codeMovedCombo, BorderLayout.WEST);
        movedRow.add(requestedByField, BorderLayout.CENTER);
        gbc.gridy = y;
        form.add(movedRow, gbc);

        root.add(UiFactory.formScroll(form), BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton submitBtn = UiFactory.primary("Submit Request",
                AppIcons.check(15, "App.onAccent"));
        submitBtn.addActionListener(e -> submit());

        JButton cancelBtn = UiFactory.secondary("Cancel", null);
        cancelBtn.addActionListener(e -> dispose());

        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.add(cancelBtn);
        footer.add(submitBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(submitBtn);
        getRootPane().setBorder(new EmptyBorder(0, 0, 0, 0));
    }

    /** True when a deployment was actually created. */
    public boolean isSubmitted() {
        return submitted;
    }

    // ------------------------------------------------------------------ form

    private JPanel pathRow(JTextField field, String dialogTitle) {
        JButton browseBtn = UiFactory.secondary("Browse...",
                AppIcons.folder(15, "App.subtleForeground"));
        browseBtn.addActionListener(e -> choosePath(field, dialogTitle));

        JPanel row = UiFactory.transparent(new BorderLayout(8, 0));
        row.add(field, BorderLayout.CENTER);
        row.add(browseBtn, BorderLayout.EAST);
        return row;
    }

    private void choosePath(JTextField field, String dialogTitle) {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(dialogTitle);
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);

        String current = field.getText().trim();
        if (!current.isEmpty()) {
            File file = new File(current);
            if (file.exists()) {
                chooser.setSelectedFile(file);
            } else if (file.getParentFile() != null && file.getParentFile().isDirectory()) {
                chooser.setCurrentDirectory(file.getParentFile());
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                field.setText(selected.getAbsolutePath());
                field.setCaretPosition(0);
            }
        }
    }

    private void submit() {
        Object project = rpaCombo.getSelectedItem();
        if (project == null || project.toString().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select the RPA this request is for.\n"
                            + "If the list is empty, create a project first via File > New Project.",
                    "RPA required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (changeDescriptionArea.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Describe the change being deployed.",
                    "Change description required", JOptionPane.WARNING_MESSAGE);
            changeDescriptionArea.requestFocusInWindow();
            return;
        }

        Deployment deployment = new Deployment();
        deployment.projectName = project.toString();
        deployment.requestedDate = requestDatePicker.getDateStringOrEmptyString();
        deployment.ritmNumber = ritmField.getText().trim();
        deployment.environment = String.valueOf(environmentCombo.getSelectedItem());
        deployment.changeDescription = changeDescriptionArea.getText().trim();
        deployment.tasksToDeploy = tasksArea.getText().trim();
        deployment.testLogPath = testLogPathField.getText().trim();
        deployment.codeAnalysisPath = codeAnalysisPathField.getText().trim();
        deployment.codeMovedToTest = String.valueOf(codeMovedCombo.getSelectedItem());
        deployment.requestedBy = requestedByField.getText().trim();
        deployment.status = "Requested";

        // A readable label for the deployment picker on change requests
        String date = deployment.requestedDate.isEmpty()
                ? LocalDate.now().toString() : deployment.requestedDate;
        deployment.name = "Request " + date;

        if (!dbManager.addDeployment(deployment)) {
            JOptionPane.showMessageDialog(this,
                    "The request could not be saved. Check the console for details.",
                    "Submit failed", JOptionPane.ERROR_MESSAGE);
            return;
        }

        submitted = true;
        JOptionPane.showMessageDialog(this,
                "Deployment request submitted for \"" + deployment.projectName + "\".",
                "Request submitted", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}
