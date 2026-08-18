package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * IntelliJ-style settings window: a category list on the left, one page per
 * category on the right.
 * <p>
 * Changes are written straight to the database, so the caller only needs to
 * refresh its views once the dialog closes.
 */
public class SettingsDialog extends JDialog {

    public static final String PAGE_TEMPLATES = "Project Templates";
    public static final String PAGE_URLS = "URLs";
    public static final String PAGE_APPLICATIONS = "Applications";
    public static final String PAGE_DEVELOPERS = "Developers";
    public static final String PAGE_APPEARANCE = "Appearance";

    private final DatabaseManager dbManager;
    private final List<String> projectNames;

    private final CardLayout pageLayout = new CardLayout();
    private final JPanel pages = UiFactory.transparent(pageLayout);
    private JList<String> categoryList;

    private DefaultTableModel templatesModel;
    private JTable templatesTable;
    private JTextField newTemplateField;

    private DefaultTableModel urlsModel;
    private JTable urlsTable;
    private JTextField newUrlCategoryField;
    private JTextField newUrlNameField;

    private DefaultTableModel applicationsModel;
    private JTable applicationsTable;
    private JTextField newApplicationField;

    private DevelopersPanel developersPanel;

    private boolean templatesChanged;
    private boolean urlsChanged;
    private boolean applicationsChanged;

    public SettingsDialog(Frame owner, DatabaseManager dbManager, List<String> projectNames) {
        this(owner, dbManager, projectNames, PAGE_TEMPLATES);
    }

    public SettingsDialog(Frame owner, DatabaseManager dbManager, List<String> projectNames,
                          String initialPage) {
        super(owner, "Settings", true);
        this.dbManager = dbManager;
        this.projectNames = (projectNames == null) ? new ArrayList<>() : new ArrayList<>(projectNames);

        setSize(940, 660);
        setMinimumSize(new Dimension(760, 520));
        setLocationRelativeTo(owner);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                closeDialog();
            }
        });

        JPanel root = UiFactory.pane(new BorderLayout(UiFactory.GAP, UiFactory.GAP));

        pages.add(createTemplatesPage(), PAGE_TEMPLATES);
        pages.add(createUrlsPage(), PAGE_URLS);
        pages.add(createApplicationsPage(), PAGE_APPLICATIONS);
        pages.add(createDevelopersPage(), PAGE_DEVELOPERS);
        pages.add(createAppearancePage(), PAGE_APPEARANCE);

        root.add(createCategoryPane(), BorderLayout.WEST);
        root.add(pages, BorderLayout.CENTER);
        root.add(createFooter(), BorderLayout.SOUTH);

        setContentPane(root);

        loadTemplates();
        loadUrls();
        loadApplications();
        showPage(initialPage);

        getRootPane().registerKeyboardAction(e -> closeDialog(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    /** Selects a category page, keeping the left-hand list in sync. */
    public void showPage(String page) {
        String target = (page == null) ? PAGE_TEMPLATES : page;
        resetEntryFields();
        if (categoryList != null) categoryList.setSelectedValue(target, true);
        pageLayout.show(pages, target);
    }

    /** Clears the "new item" inputs so they never carry over between pages. */
    private void resetEntryFields() {
        if (newTemplateField != null) newTemplateField.setText("");
        if (newUrlCategoryField != null) newUrlCategoryField.setText("");
        if (newUrlNameField != null) newUrlNameField.setText("");
        if (newApplicationField != null) newApplicationField.setText("");
    }

    /** True when the application list changed and dependent views must reload. */
    public boolean isApplicationsChanged() {
        return applicationsChanged;
    }

    /** True when the template list changed and the project tree must be rebuilt. */
    public boolean isTemplatesChanged() {
        return templatesChanged;
    }

    /** True when the URL list changed and the URLs menu must be rebuilt. */
    public boolean isUrlsChanged() {
        return urlsChanged;
    }

    // ------------------------------------------------------------------ chrome

    private JPanel createCategoryPane() {
        JPanel panel = UiFactory.transparent(new BorderLayout(0, 8));
        panel.setPreferredSize(new Dimension(215, 0));
        panel.add(UiFactory.sectionTitle("Categories", AppIcons.sliders(16, "App.accent")),
                BorderLayout.NORTH);

        DefaultListModel<String> model = new DefaultListModel<>();
        model.addElement(PAGE_TEMPLATES);
        model.addElement(PAGE_URLS);
        model.addElement(PAGE_APPLICATIONS);
        model.addElement(PAGE_DEVELOPERS);
        model.addElement(PAGE_APPEARANCE);

        categoryList = new JList<>(model);
        categoryList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        categoryList.setOpaque(false);
        categoryList.setFixedCellHeight(28);
        categoryList.setBorder(new EmptyBorder(4, 4, 4, 4));
        categoryList.setSelectedIndex(0);
        categoryList.addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) return;
            String selected = categoryList.getSelectedValue();
            if (selected == null) return;
            // Entry boxes are per-page scratch space, never carried across pages
            resetEntryFields();
            pageLayout.show(pages, selected);
        });

        JPanel card = UiFactory.card(new BorderLayout(), 6);
        card.add(UiFactory.bareScroll(categoryList), BorderLayout.CENTER);
        panel.add(card, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createFooter() {
        JPanel footer = UiFactory.transparent(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        JButton closeBtn = UiFactory.secondary("Close", null);
        closeBtn.addActionListener(e -> closeDialog());
        footer.add(closeBtn);
        return footer;
    }

    private void closeDialog() {
        if (urlsTable != null && urlsTable.isEditing()) urlsTable.getCellEditor().stopCellEditing();
        // Persist edits so nothing typed into a table is lost on close
        saveUrls(false);
        if (developersPanel != null) developersPanel.saveDevelopers(false);
        dispose();
    }

    /** Title + italic description shown at the top of every page. */
    private JPanel pageHeader(String title, String description) {
        JPanel header = UiFactory.transparent(new BorderLayout(0, 6));
        JLabel titleLabel = UiFactory.sectionTitle(title, null);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 19f));
        JLabel subtitle = UiFactory.subtitle(description);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.ITALIC, 12f));
        header.add(titleLabel, BorderLayout.NORTH);
        header.add(subtitle, BorderLayout.CENTER);
        header.setBorder(new EmptyBorder(0, 2, UiFactory.GAP, 2));
        return header;
    }

    // --------------------------------------------------------------- templates

    private JPanel createTemplatesPage() {
        JPanel page = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        page.add(pageHeader("Project Template Items",
                "These nodes will be added to every new project."), BorderLayout.NORTH);

        templatesModel = new DefaultTableModel(new String[]{"Template Node Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        templatesTable = new JTable(templatesModel);
        UiFactory.styleTable(templatesTable);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(templatesTable), BorderLayout.CENTER);

        newTemplateField = UiFactory.textField("New Template Name");
        newTemplateField.addActionListener(e -> addTemplate());

        JButton addBtn = UiFactory.compactPrimaryIconButton(
                AppIcons.plus(14, "App.onAccent"), "Add template item");
        addBtn.addActionListener(e -> addTemplate());

        JButton removeBtn = UiFactory.compactIconButton(
                AppIcons.minus(14, "App.subtleForeground"), "Remove selected template item");
        removeBtn.addActionListener(e -> removeTemplate());

        JPanel controls = UiFactory.transparent(new BorderLayout(8, 0));
        controls.add(newTemplateField, BorderLayout.CENTER);
        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        controls.add(buttons, BorderLayout.EAST);

        page.add(tableCard, BorderLayout.CENTER);
        page.add(controls, BorderLayout.SOUTH);
        return page;
    }

    private void loadTemplates() {
        templatesModel.setRowCount(0);
        for (String name : dbManager.getTemplateItems()) {
            templatesModel.addRow(new Object[]{name});
        }
    }

    private void addTemplate() {
        String name = newTemplateField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a template name first.",
                    "Template name required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!dbManager.addTemplateItem(name)) {
            JOptionPane.showMessageDialog(this, "\"" + name + "\" already exists.",
                    "Duplicate template", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Materialise the new node for every existing project
        for (String project : projectNames) {
            dbManager.saveNodeState(project, name, false);
        }

        newTemplateField.setText("");
        templatesChanged = true;
        loadTemplates();
        int last = templatesModel.getRowCount() - 1;
        if (last >= 0) {
            templatesTable.setRowSelectionInterval(last, last);
            templatesTable.scrollRectToVisible(templatesTable.getCellRect(last, 0, true));
        }
    }

    private void removeTemplate() {
        int row = templatesTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a template item to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = String.valueOf(templatesModel.getValueAt(
                templatesTable.convertRowIndexToModel(row), 0));

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove \"" + name + "\" from every project?\n"
                        + "Completion dates and comments stored for this node will be deleted.",
                "Remove Template Item", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        dbManager.removeTemplateItem(name);
        templatesChanged = true;
        loadTemplates();
    }

    // -------------------------------------------------------------------- urls

    private JPanel createUrlsPage() {
        JPanel page = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        page.add(pageHeader("Default Project URLs",
                "Manage URL categories and set default values."), BorderLayout.NORTH);

        urlsModel = new DefaultTableModel(new String[]{"Category", "URL Name", "Default URL Value"}, 0);
        urlsTable = new JTable(urlsModel);
        UiFactory.styleTable(urlsTable);
        urlsTable.getColumnModel().getColumn(0).setPreferredWidth(120);
        urlsTable.getColumnModel().getColumn(1).setPreferredWidth(140);
        urlsTable.getColumnModel().getColumn(2).setPreferredWidth(320);
        urlsModel.addTableModelListener(e -> urlsChanged = true);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(urlsTable), BorderLayout.CENTER);

        newUrlCategoryField = UiFactory.textField("Category");
        newUrlCategoryField.setColumns(10);
        newUrlNameField = UiFactory.textField("New URL Name");
        newUrlNameField.addActionListener(e -> addUrlRow());

        JButton addBtn = UiFactory.compactPrimaryIconButton(
                AppIcons.plus(14, "App.onAccent"), "Add URL");
        addBtn.addActionListener(e -> addUrlRow());

        JButton removeBtn = UiFactory.compactIconButton(
                AppIcons.minus(14, "App.subtleForeground"), "Remove selected URL");
        removeBtn.addActionListener(e -> removeUrlRow());

        JPanel controls = UiFactory.transparent(new BorderLayout(8, 0));
        controls.add(newUrlCategoryField, BorderLayout.WEST);
        controls.add(newUrlNameField, BorderLayout.CENTER);
        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        controls.add(buttons, BorderLayout.EAST);

        JButton saveBtn = UiFactory.primary("Save All Default URLs", AppIcons.save(15, "App.onAccent"));
        saveBtn.addActionListener(e -> saveUrls(true));

        JPanel south = UiFactory.transparent(new BorderLayout(0, 10));
        south.add(controls, BorderLayout.NORTH);
        south.add(saveBtn, BorderLayout.CENTER);

        page.add(tableCard, BorderLayout.CENTER);
        page.add(south, BorderLayout.SOUTH);
        return page;
    }

    private void loadUrls() {
        urlsModel.setRowCount(0);
        for (String[] item : dbManager.getUrlItems()) {
            urlsModel.addRow(new Object[]{item[0], item[1], item[2]});
        }
        urlsChanged = false;
    }

    private void addUrlRow() {
        String name = newUrlNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter a URL name first.",
                    "URL name required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String category = newUrlCategoryField.getText().trim();
        urlsModel.addRow(new Object[]{category, name, ""});
        newUrlNameField.setText("");

        int last = urlsModel.getRowCount() - 1;
        urlsTable.setRowSelectionInterval(last, last);
        urlsTable.scrollRectToVisible(urlsTable.getCellRect(last, 2, true));
        urlsTable.editCellAt(last, 2);
        Component editor = urlsTable.getEditorComponent();
        if (editor != null) editor.requestFocusInWindow();
    }

    private void removeUrlRow() {
        int row = urlsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a URL to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        if (urlsTable.isEditing()) urlsTable.getCellEditor().cancelCellEditing();
        urlsModel.removeRow(urlsTable.convertRowIndexToModel(row));
    }

    private void saveUrls(boolean notifyUser) {
        if (urlsTable == null || urlsModel == null) return;
        if (urlsTable.isEditing()) urlsTable.getCellEditor().stopCellEditing();

        List<String[]> items = new ArrayList<>();
        for (int row = 0; row < urlsModel.getRowCount(); row++) {
            String category = text(urlsModel.getValueAt(row, 0));
            String name = text(urlsModel.getValueAt(row, 1));
            String url = text(urlsModel.getValueAt(row, 2));
            if (name.isEmpty()) continue;
            items.add(new String[]{category, name, url});
        }
        dbManager.replaceUrlItems(items);
        urlsChanged = true;

        if (notifyUser) {
            JOptionPane.showMessageDialog(this,
                    "Saved " + items.size() + " URL" + (items.size() == 1 ? "" : "s") + ".",
                    "Saved", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    // ------------------------------------------------------------ applications

    private JPanel createApplicationsPage() {
        JPanel page = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        page.add(pageHeader("Applications",
                "The unique applications your RPAs use. Tick the ones each project needs "
                        + "on its Details View."), BorderLayout.NORTH);

        applicationsModel = new DefaultTableModel(new String[]{"Application Name"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        applicationsTable = new JTable(applicationsModel);
        UiFactory.styleTable(applicationsTable);

        JPanel tableCard = UiFactory.card(new BorderLayout(), 5);
        tableCard.add(UiFactory.bareScroll(applicationsTable), BorderLayout.CENTER);

        newApplicationField = UiFactory.textField("New Application Name");
        newApplicationField.addActionListener(e -> addApplication());

        JButton addBtn = UiFactory.compactPrimaryIconButton(
                AppIcons.plus(14, "App.onAccent"), "Add application");
        addBtn.addActionListener(e -> addApplication());

        JButton removeBtn = UiFactory.compactIconButton(
                AppIcons.minus(14, "App.subtleForeground"), "Remove selected application");
        removeBtn.addActionListener(e -> removeApplication());

        JPanel controls = UiFactory.transparent(new BorderLayout(8, 0));
        controls.add(newApplicationField, BorderLayout.CENTER);
        JPanel buttons = UiFactory.transparent(new FlowLayout(FlowLayout.LEFT, 6, 0));
        buttons.add(addBtn);
        buttons.add(removeBtn);
        controls.add(buttons, BorderLayout.EAST);

        page.add(tableCard, BorderLayout.CENTER);
        page.add(controls, BorderLayout.SOUTH);
        return page;
    }

    private void loadApplications() {
        applicationsModel.setRowCount(0);
        for (String name : dbManager.getApplications()) {
            applicationsModel.addRow(new Object[]{name});
        }
    }

    private void addApplication() {
        String name = newApplicationField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Enter an application name first.",
                    "Application name required", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!dbManager.addApplication(name)) {
            JOptionPane.showMessageDialog(this, "\"" + name + "\" already exists.",
                    "Duplicate application", JOptionPane.WARNING_MESSAGE);
            return;
        }

        newApplicationField.setText("");
        applicationsChanged = true;
        loadApplications();

        int last = applicationsModel.getRowCount() - 1;
        if (last >= 0) {
            applicationsTable.setRowSelectionInterval(last, last);
            applicationsTable.scrollRectToVisible(applicationsTable.getCellRect(last, 0, true));
        }
    }

    private void removeApplication() {
        int row = applicationsTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select an application to remove.",
                    "Nothing selected", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String name = String.valueOf(applicationsModel.getValueAt(
                applicationsTable.convertRowIndexToModel(row), 0));

        int choice = JOptionPane.showConfirmDialog(this,
                "Remove \"" + name + "\"?\n"
                        + "It will also be unlinked from every project that uses it.",
                "Remove Application", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) return;

        dbManager.removeApplication(name);
        applicationsChanged = true;
        loadApplications();
    }

    // -------------------------------------------------------------- developers

    private JPanel createDevelopersPage() {
        JPanel page = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        page.add(pageHeader("Developers",
                "Add developers, then assign them projects, change requests and adhoc work. "
                        + "Each item has a single owner."), BorderLayout.NORTH);

        developersPanel = new DevelopersPanel(dbManager);
        page.add(developersPanel, BorderLayout.CENTER);
        return page;
    }

    // -------------------------------------------------------------- appearance

    private JPanel createAppearancePage() {
        JPanel page = UiFactory.transparent(new BorderLayout(0, UiFactory.GAP));
        page.add(pageHeader("Appearance", "Pick the theme used across the application."),
                BorderLayout.NORTH);

        JPanel card = UiFactory.card(new GridBagLayout(), 18);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.insets = new Insets(4, 0, 4, 0);

        gbc.gridy = 0;
        card.add(UiFactory.fieldLabel("Theme"), gbc);

        ButtonGroup group = new ButtonGroup();
        int row = 1;
        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            JRadioButton option = new JRadioButton(theme.displayName);
            option.setOpaque(false);
            option.setSelected(theme == ThemeManager.current());
            option.addActionListener(e -> ThemeManager.setTheme(theme));
            group.add(option);
            gbc.gridy = row++;
            card.add(option, gbc);
        }

        JPanel wrapper = UiFactory.transparent(new BorderLayout());
        wrapper.add(card, BorderLayout.NORTH);
        page.add(wrapper, BorderLayout.CENTER);
        return page;
    }
}
