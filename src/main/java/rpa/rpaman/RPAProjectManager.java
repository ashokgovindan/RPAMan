package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RPAProjectManager extends JFrame {

    /** Horizontal band, from the start of a row, that counts as the checkbox. */
    private static final int CHECKBOX_HIT_WIDTH = 26;

    private final List<String> projectNames = new ArrayList<>();
    private final DatabaseManager dbManager;

    private List<String> rpaTemplateNodes;
    private DefaultTreeModel treeModel;
    private DefaultMutableTreeNode rootNode;
    private JTree tree;
    private CenterViewManager centerViewManager;
    private JMenu urlsMenu;
    private JTextField projectSearchField;
    private String treeFilter = "";
    private boolean suppressSelectionEvents;

    public RPAProjectManager() {
        this(new DatabaseManager());
    }

    public RPAProjectManager(DatabaseManager dbManager) {
        this.dbManager = dbManager;

        setTitle(AppInfo.windowTitle());
        setSize(1400, 820);
        setMinimumSize(new Dimension(1040, 640));
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);

        // Persist whatever is on screen before the window goes away
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (centerViewManager != null) centerViewManager.flushPendingEdits();
                dispose();
                System.exit(0);
            }
        });

        // Projects and the global template list drive the whole tree
        projectNames.addAll(dbManager.getProjectNames());
        rpaTemplateNodes = dbManager.getTemplateItems();

        setupMenuBar();
        setupMainLayout();
    }

    private void setupMainLayout() {
        centerViewManager = new CenterViewManager(dbManager);
        RightTasksPane rightPane = new RightTasksPane(dbManager);
        JPanel leftPane = createLeftPane();

        JSplitPane rightSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, centerViewManager, rightPane);
        rightSplit.setResizeWeight(0.65);
        styleSplit(rightSplit);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftPane, rightSplit);
        mainSplit.setResizeWeight(0.2);
        styleSplit(mainSplit);

        JPanel root = new JPanel(new BorderLayout());
        root.setBackground(ThemeManager.color("App.canvas", root.getBackground()));
        root.add(mainSplit, BorderLayout.CENTER);
        ThemeManager.onThemeChanged(() ->
                root.setBackground(ThemeManager.color("App.canvas", root.getBackground())));

        setContentPane(root);
    }

    private void styleSplit(JSplitPane split) {
        split.setBorder(null);
        split.setDividerSize(6);
        split.setContinuousLayout(true);
        split.setOpaque(false);
    }

    private JPanel createLeftPane() {
        JPanel panel = UiFactory.pane(new BorderLayout(0, UiFactory.GAP));

        JLabel titleLabel = UiFactory.sectionTitle("Projects", AppIcons.folder(17, "App.accent"));

        projectSearchField = UiFactory.searchField("Search projects...");
        JTextField searchField = projectSearchField;
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            private void apply() {
                treeFilter = searchField.getText();
                rebuildTreeNodes();
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

        JPanel topPanel = UiFactory.headerBlock(titleLabel, searchField);

        // Build Tree
        rootNode = new DefaultMutableTreeNode("RPA Projects");
        treeModel = new DefaultTreeModel(rootNode);
        tree = new JTree(treeModel);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(27);
        tree.setOpaque(false);
        tree.setBorder(new EmptyBorder(4, 2, 4, 2));
        tree.setCellRenderer(new CheckBoxTreeCellRenderer());
        tree.putClientProperty("JTree.lineStyle", "None");
        ToolTipManager.sharedInstance().registerComponent(tree);

        // Listener 1: Toggle the checkbox only when the checkbox itself is clicked,
        // so selecting a node to edit its details never changes its state.
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = tree.getRowForLocation(e.getX(), e.getY());
                if (row == -1) return;

                Rectangle bounds = tree.getRowBounds(row);
                if (bounds == null) return;
                boolean onCheckBox = e.getX() >= bounds.x
                        && e.getX() <= bounds.x + CHECKBOX_HIT_WIDTH;
                if (!onCheckBox) return;

                TreePath path = tree.getPathForRow(row);
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();

                if (node.getUserObject() instanceof CheckableItem) {
                    CheckableItem item = (CheckableItem) node.getUserObject();
                    // Toggle UI state
                    item.setSelected(!item.isSelected());
                    treeModel.nodeChanged(node);

                    // Save to Database immediately
                    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                    String projectName = parent.getUserObject().toString();
                    dbManager.saveNodeState(projectName, item.getText(), item.isSelected());
                }
            }
        });

        // Listener 2: Change center pane views based on selection
        tree.addTreeSelectionListener(e -> {
            if (suppressSelectionEvents) return;

            DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();
            if (node == null) {
                centerViewManager.showDefault();
                return;
            }

            if (node.isRoot()) {
                centerViewManager.showDefault();
            } else if (node.getUserObject() instanceof CheckableItem) {
                // It is a Template Node — details belong to (project, node)
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                String projectName = (parent == null) ? "" : parent.getUserObject().toString();
                centerViewManager.showTemplateDetails(projectName,
                        ((CheckableItem) node.getUserObject()).getText());
            } else if (node.getUserObject() instanceof SectionItem) {
                // Change Requests / Deployments section of a project
                DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();
                String projectName = (parent == null) ? "" : parent.getUserObject().toString();
                switch (((SectionItem) node.getUserObject()).getKind()) {
                    case CHANGE_REQUESTS:
                        centerViewManager.showChangeRequests(projectName);
                        break;
                    case SERVICE_ACCOUNTS:
                        centerViewManager.showServiceAccounts(projectName);
                        break;
                    case DEPLOYMENTS:
                    default:
                        centerViewManager.showDeployments(projectName);
                        break;
                }
            } else {
                // It is a Project Node
                centerViewManager.showDatabaseConfig(node.getUserObject().toString());
            }
        });

        rebuildTreeNodes();

        JPanel treeCard = UiFactory.card(new BorderLayout(), 6);
        treeCard.add(UiFactory.bareScroll(tree), BorderLayout.CENTER);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(treeCard, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Rebuilds the tree from the project list, honouring the search box.
     * <p>
     * A project is kept when its own name matches (all its nodes are shown) or
     * when at least one of its template nodes matches (only those are shown).
     */
    private void rebuildTreeNodes() {
        String query = treeFilter == null ? "" : treeFilter.trim().toLowerCase();

        suppressSelectionEvents = true;
        try {
            rootNode.removeAllChildren();

            for (String projectName : projectNames) {
                boolean projectMatches = query.isEmpty()
                        || projectName.toLowerCase().contains(query);

                List<String> visibleNodes = new ArrayList<>();
                for (String templateName : rpaTemplateNodes) {
                    if (projectMatches || templateName.toLowerCase().contains(query)) {
                        visibleNodes.add(templateName);
                    }
                }
                boolean sectionMatches = "change requests".contains(query)
                        || "deployments".contains(query)
                        || "service accounts".contains(query);
                if (!projectMatches && visibleNodes.isEmpty() && !sectionMatches) continue;

                DefaultMutableTreeNode projectNode = new DefaultMutableTreeNode(projectName);
                for (String templateName : visibleNodes) {
                    CheckableItem item = new CheckableItem(templateName);
                    item.setSelected(dbManager.isNodeSelected(projectName, templateName));
                    projectNode.add(new DefaultMutableTreeNode(item));
                }
                for (SectionItem.Kind kind : SectionItem.Kind.values()) {
                    if (projectMatches || kind.label.toLowerCase().contains(query)) {
                        projectNode.add(new DefaultMutableTreeNode(new SectionItem(kind)));
                    }
                }
                rootNode.add(projectNode);
            }

            treeModel.reload();
            for (int i = 0; i < tree.getRowCount(); i++) tree.expandRow(i);
        } finally {
            suppressSelectionEvents = false;
        }
    }

    private void setupMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(UiFactory.separator(4, 4));

        JMenu fileMenu = new JMenu("File");

        JMenuItem newProjectItem = new JMenuItem("New Project...",
                AppIcons.plus(16, "App.subtleForeground"));
        newProjectItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_N,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        newProjectItem.addActionListener(e -> addNewProject());
        fileMenu.add(newProjectItem);

        JMenuItem deploymentRequestItem = new JMenuItem("Submit Deployment Request...",
                AppIcons.upload(16, "App.subtleForeground"));
        deploymentRequestItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_D,
                Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        deploymentRequestItem.addActionListener(e -> submitDeploymentRequest());
        fileMenu.add(deploymentRequestItem);

        fileMenu.addSeparator();

        JMenuItem exitItem = new JMenuItem("Exit", AppIcons.exit(16, "App.subtleForeground"));
        exitItem.addActionListener(e -> {
            if (centerViewManager != null) centerViewManager.flushPendingEdits();
            System.exit(0);
        });
        fileMenu.add(exitItem);

        urlsMenu = new JMenu("URLs");
        rebuildUrlsMenu();

        JMenu viewMenu = new JMenu("View");
        JMenuItem settingsItem = new JMenuItem("Settings...",
                AppIcons.sliders(16, "App.subtleForeground"));
        settingsItem.addActionListener(e -> openSettings());

        JMenuItem botStatusItem = new JMenuItem("Bot Run Status",
                AppIcons.monitor(16, "App.subtleForeground"));
        botStatusItem.addActionListener(e -> openBotRunStatus());

        JMenuItem aaHistoryItem = new JMenuItem("AA Run History (24 hr)",
                AppIcons.refresh(16, "App.subtleForeground"));
        aaHistoryItem.addActionListener(e -> openAaRunHistory());

        JMenuItem appUsageItem = new JMenuItem("App Usage",
                AppIcons.grid(16, "App.subtleForeground"));
        appUsageItem.addActionListener(e -> openAppUsage());

        JMenuItem workloadItem = new JMenuItem("Workload by Developer",
                AppIcons.user(16, "App.subtleForeground"));
        workloadItem.addActionListener(e -> openWorkloadReport());

        JMenu themeMenu = new JMenu("Themes");
        themeMenu.setIcon(AppIcons.palette(16, "App.subtleForeground"));
        ButtonGroup themeGroup = new ButtonGroup();
        Map<ThemeManager.Theme, JRadioButtonMenuItem> themeItems = new LinkedHashMap<>();
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(theme.displayName, iconFor(theme));
            item.setSelected(theme == ThemeManager.current());
            item.addActionListener(e -> ThemeManager.setTheme(theme));
            themeGroup.add(item);
            themeMenu.add(item);
            themeItems.put(theme, item);
        }
        // Keep the menu in sync when the theme is changed from Settings > Appearance
        ThemeManager.onThemeChanged(() -> {
            JRadioButtonMenuItem item = themeItems.get(ThemeManager.current());
            if (item != null) item.setSelected(true);
        });

        viewMenu.add(botStatusItem);
        viewMenu.add(aaHistoryItem);
        viewMenu.add(appUsageItem);
        viewMenu.add(workloadItem);
        viewMenu.addSeparator();
        viewMenu.add(settingsItem);
        viewMenu.add(themeMenu);

        // --- top-level Change Requests menu
        JMenu changeRequestsMenu = new JMenu("Change Requests");
        JMenuItem allChangeRequestsItem = new JMenuItem("All Change Requests...",
                AppIcons.inbox(16, "App.subtleForeground"));
        allChangeRequestsItem.addActionListener(e -> openChangeRequestsReport());
        changeRequestsMenu.add(allChangeRequestsItem);

        // --- top-level Deployments menu
        JMenu deploymentsMenu = new JMenu("Deployments");
        JMenuItem allDeploymentsItem = new JMenuItem("All Deployments...",
                AppIcons.upload(16, "App.subtleForeground"));
        allDeploymentsItem.addActionListener(e -> openDeploymentsReport());
        deploymentsMenu.add(allDeploymentsItem);
        deploymentsMenu.addSeparator();

        JMenuItem submitFromMenu = new JMenuItem("Submit Deployment Request...",
                AppIcons.plus(16, "App.subtleForeground"));
        submitFromMenu.addActionListener(e -> submitDeploymentRequest());
        deploymentsMenu.add(submitFromMenu);

        JMenu helpMenu = new JMenu("Help");
        JMenuItem aboutItem = new JMenuItem("About " + AppInfo.NAME,
                AppIcons.info(16, "App.subtleForeground"));
        aboutItem.addActionListener(e -> new AboutDialog(this, dbManager).setVisible(true));
        helpMenu.add(aboutItem);

        menuBar.add(fileMenu);
        menuBar.add(changeRequestsMenu);
        menuBar.add(deploymentsMenu);
        menuBar.add(urlsMenu);
        menuBar.add(viewMenu);
        menuBar.add(helpMenu);
        setJMenuBar(menuBar);
    }

    // -------------------------------------------------------------- projects

    /** Prompts for a name, creates the project and selects it in the tree. */
    private void addNewProject() {
        String input = JOptionPane.showInputDialog(this,
                "Project name:", "New Project", JOptionPane.PLAIN_MESSAGE);
        if (input == null) return;

        String name = input.trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a project name.",
                    "Project name required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (dbManager.projectExists(name) || !dbManager.addProject(name)) {
            JOptionPane.showMessageDialog(this, "A project named \"" + name + "\" already exists.",
                    "Duplicate project", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Give the new project the full set of template nodes
        for (String templateName : rpaTemplateNodes) {
            dbManager.saveNodeState(name, templateName, false);
        }

        projectNames.clear();
        projectNames.addAll(dbManager.getProjectNames());

        // Clear the filter so the new project is visible straight away
        if (projectSearchField != null) projectSearchField.setText("");
        treeFilter = "";
        rebuildTreeNodes();
        selectProjectInTree(name);
    }

    /** Opens the deployment request form for any RPA. */
    private void submitDeploymentRequest() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();

        if (projectNames.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Create a project first via File > New Project.",
                    "No projects", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        DeploymentRequestDialog dialog = new DeploymentRequestDialog(this, dbManager, null);
        dialog.setVisible(true);

        if (dialog.isSubmitted() && centerViewManager != null) {
            centerViewManager.refreshDeployments();
        }
    }

    /** Selects a project node so the center pane opens its configuration. */
    private void selectProjectInTree(String projectName) {
        for (int i = 0; i < rootNode.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) rootNode.getChildAt(i);
            if (projectName.equals(child.getUserObject())) {
                TreePath path = new TreePath(treeModel.getPathToRoot(child));
                tree.setSelectionPath(path);
                tree.scrollPathToVisible(path);
                return;
            }
        }
    }

    // ------------------------------------------------------------- settings

    /** All change requests, every project, split by open vs closed. */
    private void openChangeRequestsReport() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new ChangeRequestsReportDialog(this, dbManager).setVisible(true);
    }

    /** All deployment requests, every project, split by open vs closed. */
    private void openDeploymentsReport() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new DeploymentsReportDialog(this, dbManager).setVisible(true);
    }

    private void openBotRunStatus() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new BotRunStatusDialog(this, dbManager, projectNames).setVisible(true);
    }

    /** Scrapes the Control Room's Historical activity and shows it by device. */
    private void openAaRunHistory() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new AaRunHistoryDialog(this, dbManager).setVisible(true);
    }

    /** Shows what each developer owns, plus anything unassigned. */
    private void openWorkloadReport() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new WorkloadReportDialog(this, dbManager,
                () -> openSettings(SettingsDialog.PAGE_DEVELOPERS)).setVisible(true);
    }

    /** Lists every application and the RPAs that use it. */
    private void openAppUsage() {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();
        new AppUsageDialog(this, dbManager,
                () -> openSettings(SettingsDialog.PAGE_APPLICATIONS)).setVisible(true);
    }

    private void openSettings() {
        openSettings(SettingsDialog.PAGE_TEMPLATES);
    }

    private void openSettings(String initialPage) {
        if (centerViewManager != null) centerViewManager.flushPendingEdits();

        SettingsDialog dialog = new SettingsDialog(this, dbManager, projectNames, initialPage);
        dialog.setVisible(true);

        if (dialog.isTemplatesChanged()) reloadTree();
        if (dialog.isUrlsChanged()) rebuildUrlsMenu();
        if (dialog.isApplicationsChanged() && centerViewManager != null) {
            // The Details View shows a checkbox per application, so rebuild it
            centerViewManager.reloadCurrentProject();
        }
    }

    /** Rebuilds the project tree after the global template list changed. */
    private void reloadTree() {
        rpaTemplateNodes = dbManager.getTemplateItems();
        rebuildTreeNodes();
        centerViewManager.showDefault();
    }

    // ----------------------------------------------------------------- urls

    /** Rebuilds the URLs menu: one submenu per category, plain items otherwise. */
    private void rebuildUrlsMenu() {
        urlsMenu.removeAll();

        List<String[]> items = dbManager.getUrlItems();
        if (items.isEmpty()) {
            JMenuItem empty = new JMenuItem("No URLs configured");
            empty.setEnabled(false);
            urlsMenu.add(empty);
        } else {
            Map<String, JMenu> categories = new LinkedHashMap<>();
            for (String[] item : items) {
                String category = item[0] == null ? "" : item[0].trim();
                String name = item[1];
                String url = item[2];

                JMenuItem menuItem = new JMenuItem(name, AppIcons.globe(16, "App.subtleForeground"));
                menuItem.setToolTipText(url == null || url.isEmpty() ? "No URL set" : url);
                menuItem.addActionListener(e -> openUrl(name, url));

                if (category.isEmpty()) {
                    urlsMenu.add(menuItem);
                } else {
                    JMenu submenu = categories.get(category);
                    if (submenu == null) {
                        submenu = new JMenu(category);
                        submenu.setIcon(AppIcons.folder(16, "App.subtleForeground"));
                        categories.put(category, submenu);
                        urlsMenu.add(submenu);
                    }
                    submenu.add(menuItem);
                }
            }
        }

        urlsMenu.addSeparator();
        JMenuItem manageItem = new JMenuItem("Manage URLs...",
                AppIcons.sliders(16, "App.subtleForeground"));
        manageItem.addActionListener(e -> openSettings(SettingsDialog.PAGE_URLS));
        urlsMenu.add(manageItem);

        urlsMenu.revalidate();
        urlsMenu.repaint();
    }

    /** Opens a configured URL in the default browser. */
    private void openUrl(String name, String url) {
        if (url == null || url.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "No URL is configured for \"" + name + "\".\n"
                            + "Set one in View > Settings > URLs.",
                    "Missing URL", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String target = url.trim();
        if (!target.matches("(?i)^[a-z][a-z0-9+.\\-]*://.*")) {
            target = "https://" + target;
        }

        try {
            if (!Desktop.isDesktopSupported()
                    || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                throw new UnsupportedOperationException("Browsing is not supported on this system");
            }
            Desktop.getDesktop().browse(new URI(target));
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Could not open:\n" + target + "\n\n" + ex.getMessage(),
                    "Unable to open URL", JOptionPane.ERROR_MESSAGE);
        }
    }

    private Icon iconFor(ThemeManager.Theme theme) {
        switch (theme) {
            case DARK:
                return AppIcons.moon(16, "App.subtleForeground");
            case BLUE:
                return AppIcons.droplet(16, "App.subtleForeground");
            case LIGHT:
            default:
                return AppIcons.sun(16, "App.subtleForeground");
        }
    }

    public static void main(String[] args) {
        DatabaseManager db = new DatabaseManager();
        ThemeManager.init(db);
        SwingUtilities.invokeLater(() -> new RPAProjectManager(db).setVisible(true));
    }
}
