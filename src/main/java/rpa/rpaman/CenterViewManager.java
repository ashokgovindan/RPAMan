package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * The middle pane. Shows either the database configuration of a project or the
 * details of a single template node, always populated from the database.
 * <p>
 * Edits are flushed to disk whenever the user navigates away, so nothing is
 * lost when jumping between tree nodes.
 */
public class CenterViewManager extends JPanel {

    private static final String DEFAULT = "DEFAULT";
    private static final String DB_CONFIG = "DB_CONFIG";
    private static final String TEMPLATE_DETAILS = "TEMPLATE_DETAILS";
    private static final String CHANGE_REQUESTS = "CHANGE_REQUESTS";
    private static final String DEPLOYMENTS = "DEPLOYMENTS";
    private static final String SERVICE_ACCOUNTS = "SERVICE_ACCOUNTS";

    /** Pinned heights so the layout cannot squash these inputs to one line. */
    private static final int QUERY_BOX_HEIGHT = 78;
    private static final int COMMENTS_BOX_HEIGHT = 110;

    private final DatabaseManager dbManager;
    private final CardLayout cardLayout;
    private final JPanel cardsPanel;

    // --- project database configuration view
    private JLabel projectDbLabel;
    private JTextField dbPathField;
    private JTextArea receivedQueryArea;
    private JTextArea pendingQueryArea;
    private JTextArea processedQueryArea;
    private JPanel machinesListPanel;
    private final List<MachineRow> machineRows = new ArrayList<>();
    /** Every {project, machine, user} row in the database, for clash detection. */
    private final List<String[]> machineMappings = new ArrayList<>();
    /** Project the machine rows belong to, so its own mappings are ignored. */
    private String machinesProject;
    private JPanel applicationsPanel;
    private final List<JCheckBox> applicationChecks = new ArrayList<>();

    // --- template node view
    private JLabel templateSubtitleLabel;
    private DatePicker templateDatePicker;
    private JTextArea commentsArea;

    // --- delivery views
    private JLabel changeRequestsSubtitle;
    private ChangeRequestsPanel changeRequestsPanel;
    private JLabel deploymentsSubtitle;
    private DeploymentsPanel deploymentsPanel;
    private JLabel serviceAccountsSubtitle;
    private ServiceAccountsPanel serviceAccountsPanel;
    private String loadedCrProject;
    private String loadedDeploymentProject;
    private String loadedServiceAccountProject;

    // --- what is currently loaded, so edits go back to the right row
    private String loadedProject;
    private String loadedTemplateProject;
    private String loadedTemplateNode;

    public CenterViewManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setLayout(new BorderLayout());
        setOpaque(true);
        setBackground(ThemeManager.color("App.canvas", getBackground()));
        setBorder(new EmptyBorder(UiFactory.GAP, UiFactory.GAP, UiFactory.GAP, UiFactory.GAP));
        ThemeManager.onThemeChanged(() ->
                setBackground(ThemeManager.color("App.canvas", getBackground())));

        cardLayout = new CardLayout();
        cardsPanel = UiFactory.transparent(cardLayout);

        cardsPanel.add(createDefaultView(), DEFAULT);
        cardsPanel.add(createDatabaseConfigView(), DB_CONFIG);
        cardsPanel.add(createTemplateDetailsView(), TEMPLATE_DETAILS);
        cardsPanel.add(createChangeRequestsView(), CHANGE_REQUESTS);
        cardsPanel.add(createDeploymentsView(), DEPLOYMENTS);
        cardsPanel.add(createServiceAccountsView(), SERVICE_ACCOUNTS);

        add(cardsPanel, BorderLayout.CENTER);
    }

    // -------------------------------------------------------------- navigation

    public void showDefault() {
        flushPendingEdits();
        clearLoaded();
        cardLayout.show(cardsPanel, DEFAULT);
    }

    private void clearLoaded() {
        loadedProject = null;
        loadedTemplateProject = null;
        loadedTemplateNode = null;
        loadedCrProject = null;
        loadedDeploymentProject = null;
        loadedServiceAccountProject = null;
    }

    /** Service accounts the RPA runs under. */
    public void showServiceAccounts(String projectName) {
        flushPendingEdits();
        clearLoaded();

        serviceAccountsSubtitle.setText(
                "Project: " + projectName + "   |   Service Accounts");
        serviceAccountsPanel.load(projectName);

        loadedServiceAccountProject = projectName;
        cardLayout.show(cardsPanel, SERVICE_ACCOUNTS);
    }

    /** Change requests received for one RPA. */
    public void showChangeRequests(String projectName) {
        flushPendingEdits();
        clearLoaded();

        changeRequestsSubtitle.setText("Project: " + projectName + "   |   Change Requests");
        changeRequestsPanel.load(projectName);

        loadedCrProject = projectName;
        cardLayout.show(cardsPanel, CHANGE_REQUESTS);
    }

    /** Deployment requests and their RITMs for one RPA. */
    public void showDeployments(String projectName) {
        flushPendingEdits();
        clearLoaded();

        deploymentsSubtitle.setText("Project: " + projectName + "   |   Deployments & RITMs");
        deploymentsPanel.load(projectName);

        loadedDeploymentProject = projectName;
        cardLayout.show(cardsPanel, DEPLOYMENTS);
    }

    /** Loads and displays the stored configuration of {@code projectName}. */
    public void showDatabaseConfig(String projectName) {
        flushPendingEdits();
        loadedTemplateProject = null;
        loadedTemplateNode = null;

        projectDbLabel.setText("Project: " + projectName);

        String[] config = dbManager.getProjectConfig(projectName);
        dbPathField.setText(config[0]);
        dbPathField.setCaretPosition(0);
        receivedQueryArea.setText(config[1]);
        pendingQueryArea.setText(config[2]);
        processedQueryArea.setText(config[3]);
        receivedQueryArea.setCaretPosition(0);
        pendingQueryArea.setCaretPosition(0);
        processedQueryArea.setCaretPosition(0);

        machinesProject = projectName;
        machineMappings.clear();
        machineMappings.addAll(dbManager.getAllMachines());

        setMachines(dbManager.getMachines(projectName));
        setApplications(dbManager.getApplications(),
                dbManager.getProjectApplications(projectName));

        loadedProject = projectName;
        cardLayout.show(cardsPanel, DB_CONFIG);
    }

    /** Re-reads the visible project, e.g. after the application list changed. */
    public void reloadCurrentProject() {
        if (loadedProject != null) showDatabaseConfig(loadedProject);
    }

    /** Reloads the deployments view, e.g. after a request was submitted. */
    public void refreshDeployments() {
        if (loadedDeploymentProject != null && deploymentsPanel != null) {
            deploymentsPanel.load(loadedDeploymentProject);
        }
    }

    /** Loads and displays the completion date and comments of one template node. */
    public void showTemplateDetails(String projectName, String templateName) {
        flushPendingEdits();
        loadedProject = null;

        templateSubtitleLabel.setText("Project: " + projectName + "   |   Node: " + templateName);

        String[] details = dbManager.getTemplateDetails(projectName, templateName);
        String storedDate = details[0];
        if (storedDate.isEmpty()) {
            templateDatePicker.clear();
        } else {
            templateDatePicker.setText(storedDate);
        }
        commentsArea.setText(details[1]);
        commentsArea.setCaretPosition(0);

        loadedTemplateProject = projectName;
        loadedTemplateNode = templateName;
        cardLayout.show(cardsPanel, TEMPLATE_DETAILS);
    }

    /** Writes whatever is on screen back to the database. Safe to call anytime. */
    public void flushPendingEdits() {
        if (loadedTemplateProject != null && loadedTemplateNode != null) {
            saveTemplateDetails(false);
        }
        if (loadedProject != null) {
            saveProjectConfig(false);
        }
        if (loadedCrProject != null && changeRequestsPanel != null) {
            changeRequestsPanel.save(false);
        }
        if (loadedDeploymentProject != null && deploymentsPanel != null) {
            deploymentsPanel.save(false);
        }
        // Service accounts write on each Add/Edit/Delete, so nothing to flush
    }

    // ------------------------------------------------------------------ saving

    private void saveTemplateDetails(boolean notifyUser) {
        if (loadedTemplateProject == null || loadedTemplateNode == null) return;

        dbManager.saveTemplateDetails(
                loadedTemplateProject,
                loadedTemplateNode,
                templateDatePicker.getDateStringOrEmptyString(),
                commentsArea.getText());

        if (notifyUser) {
            JOptionPane.showMessageDialog(this,
                    "Saved details for \"" + loadedTemplateNode + "\".",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void saveProjectConfig(boolean notifyUser) {
        if (loadedProject == null) return;

        dbManager.saveProjectConfig(
                loadedProject,
                dbPathField.getText().trim(),
                receivedQueryArea.getText(),
                pendingQueryArea.getText(),
                processedQueryArea.getText());

        List<String[]> machines = new ArrayList<>();
        for (MachineRow row : machineRows) {
            String machine = row.machineField.getText().trim();
            String user = row.userField.getText().trim();
            if (!machine.isEmpty() || !user.isEmpty()) {
                machines.add(new String[]{machine, user});
            }
        }
        dbManager.replaceMachines(loadedProject, machines);

        List<String> applications = new ArrayList<>();
        for (JCheckBox check : applicationChecks) {
            if (check.isSelected()) applications.add(check.getText());
        }
        dbManager.replaceProjectApplications(loadedProject, applications);

        if (notifyUser) {
            JOptionPane.showMessageDialog(this,
                    "Saved configuration for \"" + loadedProject + "\".",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ------------------------------------------------------------------- views

    /**
     * Shared "Details View" heading block. Uses the same fixed-height header as
     * the tree and tasks panes so all three content areas line up.
     */
    private JPanel header(Icon icon, JLabel subtitleLabel) {
        return UiFactory.headerBlock(UiFactory.sectionTitle("Details View", icon), subtitleLabel);
    }

    private JPanel createDefaultView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        JLabel subtitle = UiFactory.subtitle("Select an item from the tree");
        subtitle.setFont(subtitle.getFont().deriveFont(Font.ITALIC, 12f));
        panel.add(header(AppIcons.info(18, "App.accent"), subtitle), BorderLayout.NORTH);

        JPanel empty = UiFactory.card(new GridBagLayout(), 30);
        JLabel hint = new JLabel("Pick a project or a template node to see its details.");
        hint.setIcon(AppIcons.layers(32, "App.subtleForeground"));
        hint.setHorizontalTextPosition(SwingConstants.CENTER);
        hint.setVerticalTextPosition(SwingConstants.BOTTOM);
        hint.setIconTextGap(12);
        hint.setForeground(ThemeManager.subtle());
        ThemeManager.onThemeChanged(() -> hint.setForeground(ThemeManager.subtle()));
        empty.add(hint);

        JPanel wrapper = UiFactory.transparent(new BorderLayout());
        wrapper.add(empty, BorderLayout.CENTER);
        panel.add(wrapper, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createChangeRequestsView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        changeRequestsSubtitle = UiFactory.subtitle("Change Requests");
        panel.add(header(AppIcons.inbox(18, "App.accent"), changeRequestsSubtitle),
                BorderLayout.NORTH);

        changeRequestsPanel = new ChangeRequestsPanel(dbManager);
        panel.add(changeRequestsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDeploymentsView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        deploymentsSubtitle = UiFactory.subtitle("Deployments");
        panel.add(header(AppIcons.upload(18, "App.accent"), deploymentsSubtitle),
                BorderLayout.NORTH);

        deploymentsPanel = new DeploymentsPanel(dbManager);
        panel.add(deploymentsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createServiceAccountsView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        serviceAccountsSubtitle = UiFactory.subtitle("Service Accounts");
        panel.add(header(AppIcons.key(18, "App.accent"), serviceAccountsSubtitle),
                BorderLayout.NORTH);

        serviceAccountsPanel = new ServiceAccountsPanel(dbManager);
        panel.add(serviceAccountsPanel, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createDatabaseConfigView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        projectDbLabel = UiFactory.subtitle("Project: ");
        panel.add(header(AppIcons.database(18, "App.accent"), projectDbLabel), BorderLayout.NORTH);

        JPanel formPanel = UiFactory.card(new GridBagLayout(), 16);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.insets = new Insets(4, 0, 4, 0);

        JLabel cardTitle = UiFactory.sectionTitle("Database Configuration (Bot Status)",
                AppIcons.database(16, "App.accent"));
        cardTitle.setFont(cardTitle.getFont().deriveFont(Font.BOLD, 13f));
        gbc.gridy = 0;
        gbc.insets = new Insets(0, 0, 12, 0);
        formPanel.add(cardTitle, gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        // --- DB path with a file picker
        gbc.gridy = 1;
        formPanel.add(UiFactory.fieldLabel("DB Path"), gbc);

        dbPathField = UiFactory.textField("DB Path");
        JButton browseBtn = UiFactory.secondary("Browse...", AppIcons.folder(15, "App.subtleForeground"));
        browseBtn.setToolTipText("Choose a database file");
        browseBtn.addActionListener(e -> chooseDatabaseFile());

        JPanel dbPathRow = UiFactory.transparent(new BorderLayout(8, 0));
        dbPathRow.add(dbPathField, BorderLayout.CENTER);
        dbPathRow.add(browseBtn, BorderLayout.EAST);
        gbc.gridy = 2;
        formPanel.add(dbPathRow, gbc);

        // --- machines / users
        gbc.gridy = 3;
        gbc.insets = new Insets(14, 0, 4, 0);
        formPanel.add(UiFactory.fieldLabel("Machines"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        machinesListPanel = UiFactory.transparent(new BorderLayout());
        machinesListPanel.setLayout(new BoxLayout(machinesListPanel, BoxLayout.Y_AXIS));
        gbc.gridy = 4;
        formPanel.add(machinesListPanel, gbc);

        JButton addMachineBtn = UiFactory.secondary("Add Machine/User",
                AppIcons.plus(15, "App.subtleForeground"));
        addMachineBtn.addActionListener(e -> {
            addMachineRow("", "");
            machineRows.get(machineRows.size() - 1).machineField.requestFocusInWindow();
        });

        JButton pasteMachinesBtn = UiFactory.secondary("Paste from Clipboard",
                AppIcons.clipboard(15, "App.subtleForeground"));
        pasteMachinesBtn.setToolTipText("<html>One machine per line, as<br>"
                + "<b>Machine&lt;tab&gt;User</b>, <b>Machine,User</b>, "
                + "<b>Machine User</b> or <b>Machine-User</b></html>");
        pasteMachinesBtn.addActionListener(e -> pasteMachinesFromClipboard());

        JPanel machineButtons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        machineButtons.add(addMachineBtn);
        machineButtons.add(pasteMachinesBtn);
        gbc.gridy = 5;
        formPanel.add(machineButtons, gbc);

        // --- applications used by this RPA
        gbc.gridy = 6;
        gbc.insets = new Insets(14, 0, 4, 0);
        formPanel.add(UiFactory.fieldLabel("Applications Used"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);

        applicationsPanel = UiFactory.transparent(new GridLayout(0, 3, 10, 2));
        gbc.gridy = 7;
        formPanel.add(applicationsPanel, gbc);

        // --- queries
        receivedQueryArea = UiFactory.textArea(3, 20);
        pendingQueryArea = UiFactory.textArea(3, 20);
        processedQueryArea = UiFactory.textArea(3, 20);

        gbc.gridy = 8;
        gbc.insets = new Insets(14, 0, 4, 0);
        formPanel.add(UiFactory.fieldLabel("Received Query"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.gridy = 9;
        formPanel.add(UiFactory.fixedHeightScroll(receivedQueryArea, QUERY_BOX_HEIGHT), gbc);

        gbc.gridy = 10;
        gbc.insets = new Insets(14, 0, 4, 0);
        formPanel.add(UiFactory.fieldLabel("Pending Query"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.gridy = 11;
        formPanel.add(UiFactory.fixedHeightScroll(pendingQueryArea, QUERY_BOX_HEIGHT), gbc);

        gbc.gridy = 12;
        gbc.insets = new Insets(14, 0, 4, 0);
        formPanel.add(UiFactory.fieldLabel("Processed Query"), gbc);
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.gridy = 13;
        formPanel.add(UiFactory.fixedHeightScroll(processedQueryArea, QUERY_BOX_HEIGHT), gbc);

        // --- actions
        gbc.gridy = 14;
        gbc.insets = new Insets(18, 0, 0, 0);
        JPanel actions = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 8, 0));
        JButton saveConfigsBtn = UiFactory.primary("Save All Bot Configs",
                AppIcons.save(15, "App.onAccent"));
        saveConfigsBtn.addActionListener(e -> saveProjectConfig(true));
        JButton removeConfigBtn = UiFactory.secondary("Remove Database Config",
                AppIcons.trash(15, "App.subtleForeground"));
        removeConfigBtn.addActionListener(e -> removeDatabaseConfig());
        actions.add(saveConfigsBtn);
        actions.add(removeConfigBtn);
        formPanel.add(actions, gbc);

        // Soaks up any spare height so GridBag keeps the rows at their
        // natural size and top-aligned, instead of centring them.
        gbc.gridy = 15;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        formPanel.add(Box.createGlue(), gbc);

        panel.add(UiFactory.formScroll(formPanel), BorderLayout.CENTER);
        return panel;
    }

    private JPanel createTemplateDetailsView() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));

        templateSubtitleLabel = UiFactory.subtitle("Template Node: ");
        panel.add(header(AppIcons.file(18, "App.accent"), templateSubtitleLabel), BorderLayout.NORTH);

        JPanel cardPanel = UiFactory.card(new GridBagLayout(), 18);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 0, 4, 0);
        gbc.weightx = 1.0;
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;

        gbc.gridy = 0;
        cardPanel.add(UiFactory.fieldLabel("Completion Date"), gbc);

        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.LINE_START;
        templateDatePicker = UiFactory.datePicker();
        cardPanel.add(templateDatePicker, gbc);

        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridy = 2;
        gbc.insets = new Insets(14, 0, 4, 0);
        cardPanel.add(UiFactory.fieldLabel("Comments"), gbc);

        gbc.gridy = 3;
        gbc.insets = new Insets(4, 0, 4, 0);
        commentsArea = UiFactory.textArea(5, 20);
        cardPanel.add(UiFactory.fixedHeightScroll(commentsArea, COMMENTS_BOX_HEIGHT), gbc);

        gbc.gridy = 4;
        gbc.insets = new Insets(16, 0, 0, 0);
        gbc.fill = GridBagConstraints.NONE;
        JButton saveBtn = UiFactory.primary("Save Details", AppIcons.save(15, "App.onAccent"));
        saveBtn.addActionListener(e -> saveTemplateDetails(true));
        cardPanel.add(saveBtn, gbc);

        gbc.gridy = 5;
        gbc.weighty = 1.0;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        cardPanel.add(Box.createGlue(), gbc);

        panel.add(UiFactory.formScroll(cardPanel), BorderLayout.CENTER);
        return panel;
    }

    // ---------------------------------------------------------- applications

    /** Renders one checkbox per known application, ticking those in use. */
    private void setApplications(List<String> allApplications, List<String> selected) {
        applicationChecks.clear();
        applicationsPanel.removeAll();

        if (allApplications.isEmpty()) {
            // HTML so the hint wraps instead of being clipped in a narrow pane
            JLabel hint = UiFactory.subtitle("<html>No applications defined yet - "
                    + "add them in View &gt; Settings &gt; Applications.</html>");
            applicationsPanel.add(hint);
        } else {
            for (String application : allApplications) {
                JCheckBox check = new JCheckBox(application);
                check.setOpaque(false);
                check.setFocusPainted(false);
                check.setSelected(selected.contains(application));
                applicationChecks.add(check);
                applicationsPanel.add(check);
            }
        }

        applicationsPanel.revalidate();
        applicationsPanel.repaint();
    }

    // -------------------------------------------------------------- machines

    private void setMachines(List<String[]> machines) {
        machineRows.clear();
        machinesListPanel.removeAll();
        for (String[] machine : machines) {
            addMachineRow(machine.length > 0 ? machine[0] : "", machine.length > 1 ? machine[1] : "");
        }
        machinesListPanel.revalidate();
        machinesListPanel.repaint();
    }

    /**
     * Reads machine/user pairs from the clipboard, one per line, and adds a row
     * for each. Duplicates of rows already on screen are ignored.
     */
    private void pasteMachinesFromClipboard() {
        String clipboard = readClipboardText();
        if (clipboard == null || clipboard.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "The clipboard is empty, or does not contain text.",
                    "Nothing to paste", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        List<String[]> parsed = new ArrayList<>();
        int unparsed = 0;
        for (String line : clipboard.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;

            String[] pair = parseMachineUser(trimmed);
            if (pair == null) {
                unparsed++;
                continue;
            }
            if (isHeaderLine(pair)) continue;
            parsed.add(pair);
        }

        if (parsed.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Could not read any machine/user pairs from the clipboard.\n\n"
                            + "Use one line per machine, for example:\n"
                            + "    Mach2\tUser2\n"
                            + "    Mach2,User2\n"
                            + "    Mach2 User2\n"
                            + "    Mach2-User2",
                    "Nothing recognised", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Reuse the blank rows already on screen rather than leaving gaps
        removeEmptyMachineRows();

        int added = 0;
        int duplicates = 0;
        for (String[] pair : parsed) {
            if (machineRowExists(pair[0], pair[1])) {
                duplicates++;
                continue;
            }
            addMachineRow(pair[0], pair[1]);
            added++;
        }

        if (duplicates > 0 || unparsed > 0) {
            StringBuilder message = new StringBuilder("Added " + added + " row"
                    + (added == 1 ? "" : "s") + ".");
            if (duplicates > 0) {
                message.append("\nSkipped ").append(duplicates).append(" already in the list.");
            }
            if (unparsed > 0) {
                message.append("\nSkipped ").append(unparsed).append(" unreadable line")
                        .append(unparsed == 1 ? "" : "s").append(".");
            }
            JOptionPane.showMessageDialog(this, message.toString(),
                    "Paste complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private String readClipboardText() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            return data == null ? null : data.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Splits one pasted line into machine and user.
     * <p>
     * Separators are tried most-specific first. Hyphens come last and split on
     * the <em>last</em> one, because machine names such as {@code LP-214846003}
     * routinely contain hyphens while trailing user names rarely do.
     *
     * @return a two-element array, or {@code null} when the line yields nothing
     */
    static String[] parseMachineUser(String line) {
        String text = line.trim();
        if (text.isEmpty()) return null;

        if (text.contains("\t")) return split(text, text.indexOf('\t'), 1);
        if (text.contains(",")) return split(text, text.indexOf(','), 1);

        // "LP-214846003 - User1": a hyphen padded with spaces is the separator
        int paddedHyphen = text.indexOf(" - ");
        if (paddedHyphen >= 0) return split(text, paddedHyphen, 3);

        java.util.regex.Matcher whitespace =
                java.util.regex.Pattern.compile("\\s+").matcher(text);
        if (whitespace.find()) {
            return split(text, whitespace.start(), whitespace.end() - whitespace.start());
        }

        int lastHyphen = text.lastIndexOf('-');
        if (lastHyphen > 0 && lastHyphen < text.length() - 1) {
            // "LP-214846003" is an asset tag, not Machine-User, so an all-digit
            // tail is treated as part of the machine name rather than a user.
            String tail = text.substring(lastHyphen + 1);
            if (!tail.matches("\\d+")) {
                return split(text, lastHyphen, 1);
            }
        }

        // A bare machine name with no user is still worth adding
        return new String[]{text, ""};
    }

    private static String[] split(String text, int at, int separatorLength) {
        String machine = text.substring(0, at).trim();
        String user = text.substring(at + separatorLength).trim();
        // Trailing columns from a spreadsheet paste are dropped
        int extra = user.indexOf('\t');
        if (extra >= 0) user = user.substring(0, extra).trim();
        if (machine.isEmpty() && user.isEmpty()) return null;
        return new String[]{machine, user};
    }

    /** Recognises a copied spreadsheet header so it does not become a row. */
    private static boolean isHeaderLine(String[] pair) {
        return "machine".equalsIgnoreCase(pair[0])
                && ("user".equalsIgnoreCase(pair[1]) || pair[1].isEmpty());
    }

    private boolean machineRowExists(String machine, String user) {
        for (MachineRow row : machineRows) {
            if (row.machineField.getText().trim().equalsIgnoreCase(machine)
                    && row.userField.getText().trim().equalsIgnoreCase(user)) {
                return true;
            }
        }
        return false;
    }

    private void removeEmptyMachineRows() {
        for (MachineRow row : new ArrayList<>(machineRows)) {
            if (row.machineField.getText().trim().isEmpty()
                    && row.userField.getText().trim().isEmpty()) {
                machineRows.remove(row);
                machinesListPanel.remove(row);
            }
        }
        machinesListPanel.revalidate();
        machinesListPanel.repaint();
    }

    private void addMachineRow(String machineName, String userName) {
        MachineRow row = new MachineRow(machineName, userName);
        row.removeButton.addActionListener(e -> {
            machineRows.remove(row);
            machinesListPanel.remove(row);
            machinesListPanel.revalidate();
            machinesListPanel.repaint();
        });

        // Re-check as the user types, so a clash shows up immediately
        DocumentListener watcher = new DocumentListener() {
            private void check() {
                validateMachineRow(row);
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                check();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                check();
            }
        };
        row.machineField.getDocument().addDocumentListener(watcher);
        row.userField.getDocument().addDocumentListener(watcher);

        machineRows.add(row);
        machinesListPanel.add(row);
        validateMachineRow(row);

        machinesListPanel.revalidate();
        machinesListPanel.repaint();
    }

    /**
     * Flags a machine or user that another project already claims: red outline
     * on the offending field, plus a line naming the other project.
     */
    private void validateMachineRow(MachineRow row) {
        String machine = row.machineField.getText().trim();
        String user = row.userField.getText().trim();

        List<String> machineClashes = new ArrayList<>();
        List<String> userClashes = new ArrayList<>();

        for (String[] mapping : machineMappings) {
            String project = mapping[0];
            if (machinesProject != null && project.equalsIgnoreCase(machinesProject)) continue;

            if (!machine.isEmpty() && machine.equalsIgnoreCase(mapping[1])
                    && !machineClashes.contains(project)) {
                machineClashes.add(project);
            }
            if (!user.isEmpty() && user.equalsIgnoreCase(mapping[2])
                    && !userClashes.contains(project)) {
                userClashes.add(project);
            }
        }

        UiFactory.setFieldError(row.machineField, !machineClashes.isEmpty(),
                machineClashes.isEmpty() ? null
                        : "Machine also mapped to: " + String.join(", ", machineClashes));
        UiFactory.setFieldError(row.userField, !userClashes.isEmpty(),
                userClashes.isEmpty() ? null
                        : "User also mapped to: " + String.join(", ", userClashes));

        StringBuilder message = new StringBuilder();
        if (!machineClashes.isEmpty()) {
            message.append("Machine also in ").append(String.join(", ", machineClashes));
        }
        if (!userClashes.isEmpty()) {
            if (message.length() > 0) message.append("      ");
            message.append("User also in ").append(String.join(", ", userClashes));
        }
        row.setConflict(message.toString());
    }

    // ---------------------------------------------------------------- actions

    private void chooseDatabaseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select database file");
        chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setAcceptAllFileFilterUsed(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
                "Database files (*.db, *.sqlite, *.sqlite3, *.mdb, *.accdb)",
                "db", "sqlite", "sqlite3", "mdb", "accdb"));

        String current = dbPathField.getText().trim();
        if (!current.isEmpty()) {
            File file = new File(current);
            File parent = file.getParentFile();
            if (file.exists()) {
                chooser.setSelectedFile(file);
            } else if (parent != null && parent.isDirectory()) {
                chooser.setCurrentDirectory(parent);
            }
        }

        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File selected = chooser.getSelectedFile();
            if (selected != null) {
                dbPathField.setText(selected.getAbsolutePath());
                dbPathField.setCaretPosition(0);
            }
        }
    }

    private void removeDatabaseConfig() {
        if (loadedProject == null) return;

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove the stored database configuration for \"" + loadedProject + "\"?",
                "Remove Database Config",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        dbManager.deleteProjectConfig(loadedProject);
        dbPathField.setText("");
        receivedQueryArea.setText("");
        pendingQueryArea.setText("");
        processedQueryArea.setText("");
        setMachines(new ArrayList<>());
    }

    private JPanel leftAligned(Component component) {
        JPanel holder = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 0, 0));
        holder.add(component);
        return holder;
    }

    // ---------------------------------------------------------------- row type

    /** One "Machine: [ ] User: [ ] [x]" line. */
    private static final class MachineRow extends JPanel {
        private final JTextField machineField = UiFactory.textField("Machine name");
        private final JTextField userField = UiFactory.textField("User name");
        private final JButton removeButton;
        /** Second line, shown only when this machine or user clashes elsewhere. */
        private final JLabel conflictLabel = new JLabel();

        MachineRow(String machineName, String userName) {
            super(new GridBagLayout());
            setOpaque(false);
            setBorder(new EmptyBorder(0, 0, 6, 0));

            machineField.setText(machineName);
            machineField.setColumns(10);
            userField.setText(userName);
            userField.setColumns(10);

            removeButton = UiFactory.compactIconButton(AppIcons.close(13, "App.subtleForeground"),
                    "Remove this machine/user");

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.gridy = 0;
            gbc.anchor = GridBagConstraints.LINE_START;
            gbc.insets = new Insets(0, 0, 0, 6);

            gbc.gridx = 0;
            add(UiFactory.inlineLabel("Machine:"), gbc);

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 0.5;
            gbc.insets = new Insets(0, 0, 0, 12);
            add(machineField, gbc);

            gbc.gridx = 2;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0;
            gbc.insets = new Insets(0, 0, 0, 6);
            add(UiFactory.inlineLabel("User:"), gbc);

            gbc.gridx = 3;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 0.5;
            gbc.insets = new Insets(0, 0, 0, 8);
            add(userField, gbc);

            gbc.gridx = 4;
            gbc.fill = GridBagConstraints.NONE;
            gbc.weightx = 0;
            gbc.insets = new Insets(0, 0, 0, 0);
            add(removeButton, gbc);

            conflictLabel.setVisible(false);
            conflictLabel.setIcon(AppIcons.warning(13, "App.badgeErrorFg"));
            conflictLabel.setIconTextGap(5);
            conflictLabel.setFont(conflictLabel.getFont().deriveFont(Font.ITALIC, 11f));

            gbc.gridx = 0;
            gbc.gridy = 1;
            gbc.gridwidth = GridBagConstraints.REMAINDER;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            gbc.insets = new Insets(3, 2, 0, 0);
            add(conflictLabel, gbc);
        }

        /** Shows or hides the clash line; an empty message hides it. */
        void setConflict(String message) {
            boolean clashing = message != null && !message.isEmpty();
            conflictLabel.setText(clashing ? message : "");
            conflictLabel.setForeground(ThemeManager.color("App.badgeErrorFg", Color.RED));
            conflictLabel.setVisible(clashing);
            revalidate();
            repaint();
        }

        /** Keeps BoxLayout from stretching the row vertically. */
        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
        }
    }
}
