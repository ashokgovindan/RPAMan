package rpa.rpaman;

import javax.swing.*;
import java.awt.*;

/**
 * Add or edit one service account. Used by {@link ServiceAccountsPanel} for
 * both actions — the title and button label change, the fields do not.
 */
public class ServiceAccountDialog extends JDialog {

    private final JComboBox<String> projectCombo;
    private final JComboBox<String> environmentCombo;
    private final JTextField accountIdField;
    private final JTextField aliasField;
    private final JTextField appNameField;
    private final JTextField emailField;
    private final JTextArea descriptionArea;

    private final ServiceAccount account;
    private boolean saved;

    /** Fixed-project form, used from a project's Service Accounts node. */
    public ServiceAccountDialog(Window owner, String projectName, ServiceAccount existing) {
        this(owner, null, projectName, existing);
    }

    /**
     * @param projectChoices projects to offer in a dropdown, or null to keep
     *                       {@code projectName} fixed
     * @param existing       the account being edited, or null to create one
     */
    public ServiceAccountDialog(Window owner, java.util.List<String> projectChoices,
                                String projectName, ServiceAccount existing) {
        super(owner, existing == null ? "Add Service Account" : "Edit Service Account",
                ModalityType.APPLICATION_MODAL);

        boolean editing = existing != null;
        this.account = editing ? existing : new ServiceAccount();
        this.account.projectName = projectName;

        setSize(660, 760);
        setMinimumSize(new Dimension(540, 600));
        setLocationRelativeTo(owner);

        JPanel root = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        JLabel titleLabel = UiFactory.sectionTitle(
                editing ? "Edit Service Account" : "New Service Account",
                AppIcons.key(17, "App.accent"));
        JLabel subtitle = UiFactory.subtitle("Tracking details only - no password is stored.");
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

        // Only shown when the caller has not already fixed the project
        if (projectChoices != null && !projectChoices.isEmpty()) {
            gbc.gridy = y++;
            form.add(UiFactory.fieldLabel("RPA Name"), gbc);
            projectCombo = UiFactory.comboBox(projectChoices.toArray(new String[0]));
            if (projectName != null && projectChoices.contains(projectName)) {
                projectCombo.setSelectedItem(projectName);
            }
            gbc.gridy = y++;
            form.add(projectCombo, gbc);

            gbc.insets = new Insets(14, 0, 4, 0);
        } else {
            projectCombo = null;
        }

        gbc.gridy = y++;
        form.add(UiFactory.fieldLabel("Environment"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        environmentCombo = UiFactory.comboBox(ServiceAccount.ENVIRONMENTS);
        environmentCombo.setSelectedItem(
                account.environment.isEmpty() ? "PROD" : account.environment);
        gbc.gridy = y++;
        form.add(environmentCombo, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Account ID"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        accountIdField = UiFactory.textField("Service account identifier");
        accountIdField.setText(account.accountId);
        gbc.gridy = y++;
        form.add(accountIdField, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Alias"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        aliasField = UiFactory.textField("Friendly name");
        aliasField.setText(account.alias);
        gbc.gridy = y++;
        form.add(aliasField, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("App Name"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        appNameField = UiFactory.textField("Application this account is for");
        appNameField.setText(account.appName);
        gbc.gridy = y++;
        form.add(appNameField, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Email"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        emailField = UiFactory.textField("Owner or distribution list");
        emailField.setText(account.email);
        gbc.gridy = y++;
        form.add(emailField, gbc);

        gbc.gridy = y++;
        gbc.insets = new Insets(14, 0, 4, 0);
        form.add(UiFactory.fieldLabel("Description"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        descriptionArea = UiFactory.textArea(4, 20);
        descriptionArea.setText(account.description);
        gbc.gridy = y;
        form.add(UiFactory.scroll(descriptionArea), gbc);

        root.add(UiFactory.formScroll(form), BorderLayout.CENTER);

        // ------------------------------------------------------------ footer
        JButton okBtn = UiFactory.primary(editing ? "Save Changes" : "Add Account",
                AppIcons.check(15, "App.onAccent"));
        okBtn.addActionListener(e -> commit());

        JButton cancelBtn = UiFactory.secondary("Cancel", null);
        cancelBtn.addActionListener(e -> dispose());

        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        footer.add(cancelBtn);
        footer.add(okBtn);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(okBtn);
    }

    /** True when the user confirmed and the account was written. */
    public boolean isSaved() {
        return saved;
    }

    public ServiceAccount getAccount() {
        return account;
    }

    private void commit() {
        String accountId = accountIdField.getText().trim();
        if (accountId.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Account ID is required.",
                    "Account ID required", JOptionPane.WARNING_MESSAGE);
            accountIdField.requestFocusInWindow();
            return;
        }

        if (projectCombo != null) {
            Object project = projectCombo.getSelectedItem();
            if (project == null || project.toString().trim().isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Select the RPA this account belongs to.",
                        "RPA required", JOptionPane.WARNING_MESSAGE);
                return;
            }
            account.projectName = project.toString();
        }

        account.environment = String.valueOf(environmentCombo.getSelectedItem());
        account.accountId = accountId;
        account.alias = aliasField.getText().trim();
        account.appName = appNameField.getText().trim();
        account.email = emailField.getText().trim();
        account.description = descriptionArea.getText().trim();

        saved = true;
        dispose();
    }
}
