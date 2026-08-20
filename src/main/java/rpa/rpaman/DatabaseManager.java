package rpa.rpaman;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:rpa_manager.db";

    public DatabaseManager() {
        initializeDatabase();
    }

    private void initializeDatabase() {
        // SQL statements to create tables
        String createTasksTable = "CREATE TABLE IF NOT EXISTS Tasks (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "description TEXT NOT NULL, " +
                "due_date TEXT, " +
                "status TEXT)";

        String createProjectsTable = "CREATE TABLE IF NOT EXISTS Projects (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL)";

        String createTemplateNodesTable = "CREATE TABLE IF NOT EXISTS TemplateNodes (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "project_name TEXT, " +
                "node_name TEXT, " +
                "is_selected INTEGER DEFAULT 0, " +
                "completion_date TEXT, " +
                "comments TEXT, " +
                "UNIQUE(project_name, node_name))";

        String createSettingsTable = "CREATE TABLE IF NOT EXISTS Settings (" +
                "key TEXT PRIMARY KEY, " +
                "value TEXT)";

        String createProjectConfigTable = "CREATE TABLE IF NOT EXISTS ProjectConfig (" +
                "project_name TEXT PRIMARY KEY, " +
                "db_path TEXT, " +
                "received_query TEXT, " +
                "pending_query TEXT, " +
                "processed_query TEXT)";

        String createProjectMachinesTable = "CREATE TABLE IF NOT EXISTS ProjectMachines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "project_name TEXT NOT NULL, " +
                "machine_name TEXT, " +
                "user_name TEXT)";

        String createTemplateItemsTable = "CREATE TABLE IF NOT EXISTS TemplateItems (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "sort_order INTEGER DEFAULT 0)";

        String createUrlItemsTable = "CREATE TABLE IF NOT EXISTS UrlItems (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "category TEXT, " +
                "name TEXT NOT NULL, " +
                "url TEXT, " +
                "sort_order INTEGER DEFAULT 0)";

        String createApplicationsTable = "CREATE TABLE IF NOT EXISTS Applications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "sort_order INTEGER DEFAULT 0)";

        String createProjectApplicationsTable = "CREATE TABLE IF NOT EXISTS ProjectApplications (" +
                "project_name TEXT NOT NULL, " +
                "application_name TEXT NOT NULL, " +
                "UNIQUE(project_name, application_name))";

        String createDeploymentsTable = "CREATE TABLE IF NOT EXISTS Deployments (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "project_name TEXT NOT NULL, " +
                "name TEXT, " +
                "ritm_number TEXT, " +
                "environment TEXT, " +
                "requested_date TEXT, " +
                "deployed_date TEXT, " +
                "status TEXT, " +
                "requested_by TEXT, " +
                "change_description TEXT, " +
                "tasks_to_deploy TEXT, " +
                "test_log_path TEXT, " +
                "code_analysis_path TEXT, " +
                "code_moved_to_test TEXT, " +
                "notes TEXT)";

        String createChangeRequestsTable = "CREATE TABLE IF NOT EXISTS ChangeRequests (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "project_name TEXT NOT NULL, " +
                "cr_number TEXT, " +
                "title TEXT, " +
                "requested_by TEXT, " +
                "received_date TEXT, " +
                "priority TEXT, " +
                "status TEXT, " +
                "target_date TEXT, " +
                "delivered_date TEXT, " +
                "deployment_id INTEGER DEFAULT 0, " +
                "notes TEXT)";

        String createServiceAccountsTable = "CREATE TABLE IF NOT EXISTS ServiceAccounts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "project_name TEXT NOT NULL, " +
                "environment TEXT, " +
                "account_id TEXT, " +
                "alias TEXT, " +
                "app_name TEXT, " +
                "email TEXT, " +
                "description TEXT)";

        String createDevelopersTable = "CREATE TABLE IF NOT EXISTS Developers (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT UNIQUE NOT NULL, " +
                "emp_id TEXT, " +
                "email TEXT, " +
                "active INTEGER DEFAULT 1, " +
                "sort_order INTEGER DEFAULT 0)";

        String createAdhocItemsTable = "CREATE TABLE IF NOT EXISTS AdhocItems (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "developer_name TEXT, " +
                "title TEXT, " +
                "description TEXT, " +
                "status TEXT, " +
                "priority TEXT, " +
                "start_date TEXT, " +
                "due_date TEXT, " +
                "completed_date TEXT)";

        String createAaActivityTable = "CREATE TABLE IF NOT EXISTS AaActivity (" +
                "id TEXT PRIMARY KEY, " +
                "activity_name TEXT, " +
                "automation_name TEXT, " +
                "automation_type TEXT, " +
                "device_name TEXT, " +
                "run_as_user TEXT, " +
                "status TEXT, " +
                "running_time TEXT, " +
                "activity_type TEXT, " +
                "started_on TEXT, " +
                "started_display TEXT, " +
                "ended_on TEXT, " +
                "fetched_at TEXT)";

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {

            stmt.execute(createTasksTable);
            stmt.execute(createProjectsTable);
            stmt.execute(createTemplateNodesTable);
            stmt.execute(createSettingsTable);
            stmt.execute(createProjectConfigTable);
            stmt.execute(createProjectMachinesTable);
            stmt.execute(createTemplateItemsTable);
            stmt.execute(createUrlItemsTable);
            stmt.execute(createApplicationsTable);
            stmt.execute(createProjectApplicationsTable);
            stmt.execute(createDeploymentsTable);
            stmt.execute(createChangeRequestsTable);
            stmt.execute(createServiceAccountsTable);
            stmt.execute(createDevelopersTable);
            stmt.execute(createAdhocItemsTable);
            stmt.execute(createAaActivityTable);

            migrateSchema(conn);

        } catch (SQLException e) {
            System.err.println("Database initialization error: " + e.getMessage());
        }

        seedDefaultTemplateItems();
        seedDefaultProjects();
    }

    // ================= PROJECTS =================

    /** Keeps the projects that shipped with the app available on first run. */
    private void seedDefaultProjects() {
        if (!getProjectNames().isEmpty()) return;
        for (String name : new String[]{"NAR Umbrella", "Another Test", "Test33"}) {
            addProject(name);
        }
    }

    /** All project names, in creation order. */
    public List<String> getProjectNames() {
        List<String> names = new ArrayList<>();
        String sql = "SELECT name FROM Projects ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(safe(rs.getString("name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return names;
    }

    /** Adds a project. Returns false when the name is blank or already taken. */
    public boolean addProject(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String sql = "INSERT OR IGNORE INTO Projects (name) VALUES (?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** True when a project with this name already exists (case-insensitive). */
    public boolean projectExists(String name) {
        if (name == null) return false;
        String sql = "SELECT 1 FROM Projects WHERE name = ? COLLATE NOCASE";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name.trim());
            return pstmt.executeQuery().next();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ================= SERVICE ACCOUNTS =================

    public List<ServiceAccount> getServiceAccounts(String projectName) {
        return readServiceAccounts(
                "SELECT * FROM ServiceAccounts WHERE project_name = ? ORDER BY id", projectName);
    }

    /** Every service account, for expiry reporting across all RPAs. */
    public List<ServiceAccount> getAllServiceAccounts() {
        return readServiceAccounts(
                "SELECT * FROM ServiceAccounts ORDER BY project_name COLLATE NOCASE, id", null);
    }

    private List<ServiceAccount> readServiceAccounts(String sql, String projectName) {
        List<ServiceAccount> accounts = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (projectName != null) pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                ServiceAccount account = new ServiceAccount();
                account.id = rs.getInt("id");
                account.projectName = safe(rs.getString("project_name"));
                account.environment = safe(rs.getString("environment"));
                account.accountId = safe(rs.getString("account_id"));
                account.alias = safe(rs.getString("alias"));
                account.appName = safe(rs.getString("app_name"));
                account.email = safe(rs.getString("email"));
                account.description = safe(rs.getString("description"));
                accounts.add(account);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return accounts;
    }

    /** Inserts one account and writes the generated id back. */
    public boolean addServiceAccount(ServiceAccount account) {
        String sql = "INSERT INTO ServiceAccounts (project_name, environment, account_id, " +
                "alias, app_name, email, description) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, safe(account.projectName));
            pstmt.setString(2, safe(account.environment));
            pstmt.setString(3, safe(account.accountId));
            pstmt.setString(4, safe(account.alias));
            pstmt.setString(5, safe(account.appName));
            pstmt.setString(6, safe(account.email));
            pstmt.setString(7, safe(account.description));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) account.id = keys.getInt(1);
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public boolean updateServiceAccount(ServiceAccount account) {
        String sql = "UPDATE ServiceAccounts SET environment = ?, account_id = ?, alias = ?, " +
                "app_name = ?, email = ?, description = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safe(account.environment));
            pstmt.setString(2, safe(account.accountId));
            pstmt.setString(3, safe(account.alias));
            pstmt.setString(4, safe(account.appName));
            pstmt.setString(5, safe(account.email));
            pstmt.setString(6, safe(account.description));
            pstmt.setInt(7, account.id);
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void deleteServiceAccount(int id) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM ServiceAccounts WHERE id = ?")) {
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= SUMMARY =================

    /** Row counts per table, in display order, for the About page. */
    public java.util.Map<String, Integer> getRecordCounts() {
        java.util.Map<String, Integer> counts = new java.util.LinkedHashMap<>();
        String[][] tables = {
                {"Projects", "RPA projects"},
                {"ChangeRequests", "Change requests"},
                {"Deployments", "Deployments"},
                {"Developers", "Developers"},
                {"AdhocItems", "Adhoc items"},
                {"Applications", "Applications"},
                {"Tasks", "Tasks"},
                {"AaActivity", "AA activity rows"}
        };

        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            for (String[] table : tables) {
                try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS total FROM " + table[0])) {
                    counts.put(table[1], rs.next() ? rs.getInt("total") : 0);
                } catch (SQLException e) {
                    // A table missing from an older database simply reads as zero
                    counts.put(table[1], 0);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return counts;
    }

    /** The SQLite file this manager is talking to. */
    public java.io.File getDatabaseFile() {
        return new java.io.File(DB_URL.substring("jdbc:sqlite:".length()));
    }

    // ================= DEVELOPERS =================

    public List<Developer> getDevelopers() {
        List<Developer> developers = new ArrayList<>();
        String sql = "SELECT * FROM Developers ORDER BY sort_order, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                Developer d = new Developer();
                d.id = rs.getInt("id");
                d.name = safe(rs.getString("name"));
                d.empId = safe(rs.getString("emp_id"));
                d.email = safe(rs.getString("email"));
                d.active = rs.getInt("active") == 1;
                developers.add(d);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return developers;
    }

    /** Names only, for the "Assigned To" pickers. */
    public List<String> getDeveloperNames() {
        List<String> names = new ArrayList<>();
        for (Developer d : getDevelopers()) {
            names.add(d.name);
        }
        return names;
    }

    /** Adds a developer. Returns false when blank or already present. */
    public boolean addDeveloper(String name, String empId, String email) {
        if (name == null || name.trim().isEmpty()) return false;
        String sql = "INSERT OR IGNORE INTO Developers (name, emp_id, email, active, sort_order) " +
                "VALUES (?, ?, ?, 1, (SELECT IFNULL(MAX(sort_order), 0) + 1 FROM Developers))";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name.trim());
            pstmt.setString(2, safe(empId));
            pstmt.setString(3, safe(email));
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateDeveloper(int id, String name, String empId, String email, boolean active) {
        String sql = "UPDATE Developers SET name = ?, emp_id = ?, email = ?, active = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safe(name));
            pstmt.setString(2, safe(empId));
            pstmt.setString(3, safe(email));
            pstmt.setInt(4, active ? 1 : 0);
            pstmt.setInt(5, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Removes a developer and releases everything they owned. */
    public void removeDeveloper(String name) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM Developers WHERE name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM AdhocItems WHERE developer_name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE Projects SET assigned_developer = '' WHERE assigned_developer = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "UPDATE ChangeRequests SET assigned_to = '' WHERE assigned_to = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= OWNERSHIP =================

    /** Project name to owning developer; projects with no owner are omitted. */
    public java.util.Map<String, String> getProjectOwners() {
        java.util.Map<String, String> owners = new java.util.LinkedHashMap<>();
        String sql = "SELECT name, IFNULL(assigned_developer, '') AS owner FROM Projects ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String owner = safe(rs.getString("owner"));
                if (!owner.isEmpty()) owners.put(safe(rs.getString("name")), owner);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return owners;
    }

    /** Sets or clears the single owner of a project. */
    public void setProjectOwner(String projectName, String developerName) {
        String sql = "UPDATE Projects SET assigned_developer = ? WHERE name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safe(developerName));
            pstmt.setString(2, projectName);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Sets or clears the single owner of a change request. */
    public void setChangeRequestOwner(int changeRequestId, String developerName) {
        String sql = "UPDATE ChangeRequests SET assigned_to = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, safe(developerName));
            pstmt.setInt(2, changeRequestId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= ADHOC ITEMS =================

    /** Adhoc items for one developer, or all of them when the name is null. */
    public List<AdhocItem> getAdhocItems(String developerName) {
        List<AdhocItem> items = new ArrayList<>();
        boolean all = (developerName == null);
        String sql = all
                ? "SELECT * FROM AdhocItems ORDER BY developer_name COLLATE NOCASE, id"
                : "SELECT * FROM AdhocItems WHERE developer_name = ? ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            if (!all) pstmt.setString(1, developerName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AdhocItem item = new AdhocItem();
                item.id = rs.getInt("id");
                item.developerName = safe(rs.getString("developer_name"));
                item.title = safe(rs.getString("title"));
                item.description = safe(rs.getString("description"));
                item.status = safe(rs.getString("status"));
                item.priority = safe(rs.getString("priority"));
                item.startDate = safe(rs.getString("start_date"));
                item.dueDate = safe(rs.getString("due_date"));
                item.completedDate = safe(rs.getString("completed_date"));
                items.add(item);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    /** Nothing references adhoc ids, so one developer's list is replaced wholesale. */
    public void saveAdhocItems(String developerName, List<AdhocItem> items) {
        if (developerName == null || developerName.trim().isEmpty()) return;

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM AdhocItems WHERE developer_name = ?")) {
                    delete.setString(1, developerName);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO AdhocItems (developer_name, title, description, status, " +
                                "priority, start_date, due_date, completed_date) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (AdhocItem item : items) {
                        if (safe(item.title).isEmpty()) continue;
                        insert.setString(1, developerName);
                        insert.setString(2, safe(item.title));
                        insert.setString(3, safe(item.description));
                        insert.setString(4, safe(item.status));
                        insert.setString(5, safe(item.priority));
                        insert.setString(6, safe(item.startDate));
                        insert.setString(7, safe(item.dueDate));
                        insert.setString(8, safe(item.completedDate));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= GLOBAL TEMPLATE ITEMS =================

    /** Populates the template list on first run so the app is usable immediately. */
    private void seedDefaultTemplateItems() {
        if (!getTemplateItems().isEmpty()) return;
        String[] defaults = {
                "TPM Name Requested", "TPM Entry", "Qualification", "Define",
                "BCA", "Development", "Deployment", "Prod Validation"
        };
        for (String name : defaults) {
            addTemplateItem(name);
        }
    }

    /** The template nodes every project gets, in display order. */
    public List<String> getTemplateItems() {
        List<String> items = new ArrayList<>();
        String sql = "SELECT name FROM TemplateItems ORDER BY sort_order, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(safe(rs.getString("name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    /** Appends a template item. Returns false when the name already exists. */
    public boolean addTemplateItem(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String sql = "INSERT OR IGNORE INTO TemplateItems (name, sort_order) VALUES (?, " +
                "(SELECT IFNULL(MAX(sort_order), 0) + 1 FROM TemplateItems))";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Removes a template item and every per-project row that referenced it. */
    public void removeTemplateItem(String name) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM TemplateItems WHERE name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM TemplateNodes WHERE node_name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= DEFAULT PROJECT URLS =================

    /** Returns URL rows as {@code {category, name, url}}, grouped by category. */
    public List<String[]> getUrlItems() {
        List<String[]> items = new ArrayList<>();
        String sql = "SELECT category, name, url FROM UrlItems ORDER BY category, sort_order, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                items.add(new String[]{
                        safe(rs.getString("category")),
                        safe(rs.getString("name")),
                        safe(rs.getString("url"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return items;
    }

    /** Replaces the whole URL list in one transaction. */
    public void replaceUrlItems(List<String[]> items) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("DELETE FROM UrlItems");
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO UrlItems (category, name, url, sort_order) VALUES (?, ?, ?, ?)")) {
                    int order = 0;
                    for (String[] item : items) {
                        insert.setString(1, item.length > 0 ? safe(item[0]) : "");
                        insert.setString(2, item.length > 1 ? safe(item[1]) : "");
                        insert.setString(3, item.length > 2 ? safe(item[2]) : "");
                        insert.setInt(4, order++);
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= TASKS CRUD OPERATIONS =================

    public void addTask(String description, String dueDate, String status) {
        String sql = "INSERT INTO Tasks(description, due_date, status) VALUES(?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, description);
            pstmt.setString(2, dueDate);
            pstmt.setString(3, status);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void updateTask(int id, String description, String dueDate, String status) {
        String sql = "UPDATE Tasks SET description = ?, due_date = ?, status = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, description);
            pstmt.setString(2, dueDate);
            pstmt.setString(3, status);
            pstmt.setInt(4, id);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Object[]> getAllTasks() {
        List<Object[]> tasks = new ArrayList<>();
        String sql = "SELECT id, description, due_date, status FROM Tasks";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                tasks.add(new Object[]{
                        rs.getInt("id"),
                        rs.getString("description"),
                        rs.getString("due_date"),
                        rs.getString("status")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return tasks;
    }

    // ================= TREE NODE STATE OPERATIONS =================

    public void saveNodeState(String projectName, String nodeName, boolean isSelected) {
        String sql = "INSERT INTO TemplateNodes (project_name, node_name, is_selected) VALUES (?, ?, ?) " +
                "ON CONFLICT(project_name, node_name) DO UPDATE SET is_selected = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.setString(2, nodeName);
            pstmt.setInt(3, isSelected ? 1 : 0);
            pstmt.setInt(4, isSelected ? 1 : 0);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public boolean isNodeSelected(String projectName, String nodeName) {
        String sql = "SELECT is_selected FROM TemplateNodes WHERE project_name = ? AND node_name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.setString(2, nodeName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("is_selected") == 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    // ================= TEMPLATE NODE DETAILS =================

    /**
     * Saves the completion date and comments for one template node.
     * The checkbox state of the node is deliberately left untouched.
     */
    public void saveTemplateDetails(String projectName, String nodeName,
                                    String completionDate, String comments) {
        String sql = "INSERT INTO TemplateNodes (project_name, node_name, completion_date, comments) " +
                "VALUES (?, ?, ?, ?) " +
                "ON CONFLICT(project_name, node_name) DO UPDATE SET completion_date = ?, comments = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.setString(2, nodeName);
            pstmt.setString(3, completionDate);
            pstmt.setString(4, comments);
            pstmt.setString(5, completionDate);
            pstmt.setString(6, comments);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Returns {@code {completionDate, comments}} for a template node.
     * Missing values come back as empty strings, never {@code null}.
     */
    public String[] getTemplateDetails(String projectName, String nodeName) {
        String sql = "SELECT completion_date, comments FROM TemplateNodes " +
                "WHERE project_name = ? AND node_name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.setString(2, nodeName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new String[]{safe(rs.getString("completion_date")), safe(rs.getString("comments"))};
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new String[]{"", ""};
    }

    // ================= PROJECT DATABASE CONFIGURATION =================

    /**
     * Returns {@code {dbPath, receivedQuery, pendingQuery, processedQuery}}
     * for a project, or four empty strings when nothing is stored yet.
     */
    public String[] getProjectConfig(String projectName) {
        String sql = "SELECT db_path, received_query, pending_query, processed_query " +
                "FROM ProjectConfig WHERE project_name = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new String[]{
                        safe(rs.getString("db_path")),
                        safe(rs.getString("received_query")),
                        safe(rs.getString("pending_query")),
                        safe(rs.getString("processed_query"))
                };
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return new String[]{"", "", "", ""};
    }

    public void saveProjectConfig(String projectName, String dbPath, String receivedQuery,
                                  String pendingQuery, String processedQuery) {
        String sql = "INSERT INTO ProjectConfig " +
                "(project_name, db_path, received_query, pending_query, processed_query) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(project_name) DO UPDATE SET " +
                "db_path = ?, received_query = ?, pending_query = ?, processed_query = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            pstmt.setString(2, dbPath);
            pstmt.setString(3, receivedQuery);
            pstmt.setString(4, pendingQuery);
            pstmt.setString(5, processedQuery);
            pstmt.setString(6, dbPath);
            pstmt.setString(7, receivedQuery);
            pstmt.setString(8, pendingQuery);
            pstmt.setString(9, processedQuery);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Wipes the stored configuration and machine list for a project. */
    public void deleteProjectConfig(String projectName) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM ProjectConfig WHERE project_name = ?")) {
                pstmt.setString(1, projectName);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM ProjectMachines WHERE project_name = ?")) {
                pstmt.setString(1, projectName);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= PROJECT MACHINES / USERS =================

    /** Returns the machine/user pairs of a project as {@code {machine, user}} rows. */
    public List<String[]> getMachines(String projectName) {
        List<String[]> machines = new ArrayList<>();
        String sql = "SELECT machine_name, user_name FROM ProjectMachines " +
                "WHERE project_name = ? ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                machines.add(new String[]{safe(rs.getString("machine_name")), safe(rs.getString("user_name"))});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return machines;
    }

    /**
     * Every machine mapping in the database as {@code {project, machine, user}},
     * used to spot a machine or user that is already claimed elsewhere.
     */
    public List<String[]> getAllMachines() {
        List<String[]> machines = new ArrayList<>();
        String sql = "SELECT project_name, machine_name, user_name FROM ProjectMachines " +
                "ORDER BY project_name COLLATE NOCASE, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                machines.add(new String[]{
                        safe(rs.getString("project_name")),
                        safe(rs.getString("machine_name")),
                        safe(rs.getString("user_name"))});
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return machines;
    }

    /** Replaces the whole machine list of a project in a single transaction. */
    public void replaceMachines(String projectName, List<String[]> machines) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM ProjectMachines WHERE project_name = ?")) {
                    delete.setString(1, projectName);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO ProjectMachines (project_name, machine_name, user_name) VALUES (?, ?, ?)")) {
                    for (String[] machine : machines) {
                        insert.setString(1, projectName);
                        insert.setString(2, machine.length > 0 ? machine[0] : "");
                        insert.setString(3, machine.length > 1 ? machine[1] : "");
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= SCHEMA MIGRATION =================

    /**
     * Adds columns introduced after a database was first created.
     * {@code CREATE TABLE IF NOT EXISTS} leaves existing tables untouched, so
     * new fields have to be added explicitly for anyone upgrading.
     */
    private void migrateSchema(Connection conn) {
        addColumnIfMissing(conn, "Deployments", "ritm_number", "TEXT");
        addColumnIfMissing(conn, "Deployments", "change_description", "TEXT");
        addColumnIfMissing(conn, "Deployments", "tasks_to_deploy", "TEXT");
        addColumnIfMissing(conn, "Deployments", "test_log_path", "TEXT");
        addColumnIfMissing(conn, "Deployments", "code_analysis_path", "TEXT");
        addColumnIfMissing(conn, "Deployments", "code_moved_to_test", "TEXT");
        // Single-owner assignment, stored on the item itself
        addColumnIfMissing(conn, "Projects", "assigned_developer", "TEXT");
        addColumnIfMissing(conn, "ChangeRequests", "assigned_to", "TEXT");
        addColumnIfMissing(conn, "Developers", "emp_id", "TEXT");
        // Service accounts were reduced to a tracking list; earlier databases
        // still carry the original columns, which are simply left unused.
        addColumnIfMissing(conn, "ServiceAccounts", "account_id", "TEXT");
        addColumnIfMissing(conn, "ServiceAccounts", "alias", "TEXT");
        addColumnIfMissing(conn, "ServiceAccounts", "app_name", "TEXT");
        addColumnIfMissing(conn, "ServiceAccounts", "email", "TEXT");
        addColumnIfMissing(conn, "ServiceAccounts", "description", "TEXT");

        // The QA environment was renamed to TEST; bring existing rows across so
        // they still match an entry in the dropdown.
        renameEnvironment(conn, "Deployments", "QA", "TEST");
        renameEnvironment(conn, "ServiceAccounts", "QA", "TEST");
    }

    private void renameEnvironment(Connection conn, String table, String from, String to) {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE " + table + " SET environment = ? WHERE environment = ?")) {
            pstmt.setString(1, to);
            pstmt.setString(2, from);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Could not rename environment in " + table + ": " + e.getMessage());
        }
    }

    private void addColumnIfMissing(Connection conn, String table, String column, String type) {
        try {
            Set<String> existing = new HashSet<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + table + ")")) {
                while (rs.next()) {
                    existing.add(rs.getString("name").toLowerCase());
                }
            }
            if (existing.contains(column.toLowerCase())) return;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
            }
        } catch (SQLException e) {
            System.err.println("Could not add column " + table + "." + column + ": " + e.getMessage());
        }
    }

    // ================= DEPLOYMENTS =================

    public List<Deployment> getDeployments(String projectName) {
        List<Deployment> deployments = new ArrayList<>();
        String sql = "SELECT * FROM Deployments WHERE project_name = ? ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                deployments.add(readDeployment(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return deployments;
    }

    private Deployment readDeployment(ResultSet rs) throws SQLException {
        Deployment d = new Deployment();
        d.id = rs.getInt("id");
        d.projectName = safe(rs.getString("project_name"));
        d.name = safe(rs.getString("name"));
        d.ritmNumber = safe(rs.getString("ritm_number"));
        d.environment = safe(rs.getString("environment"));
        d.requestedDate = safe(rs.getString("requested_date"));
        d.deployedDate = safe(rs.getString("deployed_date"));
        d.status = safe(rs.getString("status"));
        d.requestedBy = safe(rs.getString("requested_by"));
        d.changeDescription = safe(rs.getString("change_description"));
        d.tasksToDeploy = safe(rs.getString("tasks_to_deploy"));
        d.testLogPath = safe(rs.getString("test_log_path"));
        d.codeAnalysisPath = safe(rs.getString("code_analysis_path"));
        d.codeMovedToTest = safe(rs.getString("code_moved_to_test"));
        d.notes = safe(rs.getString("notes"));
        return d;
    }

    /** Inserts a single deployment request, e.g. from the submission form. */
    public boolean addDeployment(Deployment d) {
        String sql = "INSERT INTO Deployments (project_name, name, ritm_number, environment, " +
                "requested_date, deployed_date, status, requested_by, change_description, " +
                "tasks_to_deploy, test_log_path, code_analysis_path, code_moved_to_test, notes) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bindDeployment(pstmt, d, d.projectName);
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) d.id = keys.getInt(1);
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    private void bindDeployment(PreparedStatement pstmt, Deployment d, String projectName)
            throws SQLException {
        pstmt.setString(1, safe(projectName));
        pstmt.setString(2, safe(d.name));
        pstmt.setString(3, safe(d.ritmNumber));
        pstmt.setString(4, safe(d.environment));
        pstmt.setString(5, safe(d.requestedDate));
        pstmt.setString(6, safe(d.deployedDate));
        pstmt.setString(7, safe(d.status));
        pstmt.setString(8, safe(d.requestedBy));
        pstmt.setString(9, safe(d.changeDescription));
        pstmt.setString(10, safe(d.tasksToDeploy));
        pstmt.setString(11, safe(d.testLogPath));
        pstmt.setString(12, safe(d.codeAnalysisPath));
        pstmt.setString(13, safe(d.codeMovedToTest));
        pstmt.setString(14, safe(d.notes));
    }

    /**
     * Saves a project's deployments. Rows are matched on id rather than being
     * replaced wholesale, because change requests reference those ids.
     * Newly inserted deployments get their generated id written back.
     */
    public void saveDeployments(String projectName, List<Deployment> deployments) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                // Delete deployments the user removed, along with their RITMs
                StringBuilder keep = new StringBuilder();
                for (Deployment d : deployments) {
                    if (d.id > 0) {
                        if (keep.length() > 0) keep.append(',');
                        keep.append(d.id);
                    }
                }
                String deleteSql = "DELETE FROM Deployments WHERE project_name = ?"
                        + (keep.length() > 0 ? " AND id NOT IN (" + keep + ")" : "");
                try (PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
                    pstmt.setString(1, projectName);
                    pstmt.executeUpdate();
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("UPDATE ChangeRequests SET deployment_id = 0 "
                            + "WHERE deployment_id NOT IN (SELECT id FROM Deployments)");
                }

                String updateSql = "UPDATE Deployments SET name = ?, ritm_number = ?, " +
                        "environment = ?, requested_date = ?, deployed_date = ?, status = ?, " +
                        "requested_by = ?, change_description = ?, tasks_to_deploy = ?, " +
                        "test_log_path = ?, code_analysis_path = ?, code_moved_to_test = ?, " +
                        "notes = ? WHERE id = ?";
                String insertSql = "INSERT INTO Deployments (project_name, name, ritm_number, " +
                        "environment, requested_date, deployed_date, status, requested_by, " +
                        "change_description, tasks_to_deploy, test_log_path, code_analysis_path, " +
                        "code_moved_to_test, notes) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement update = conn.prepareStatement(updateSql);
                     PreparedStatement insert = conn.prepareStatement(insertSql,
                             Statement.RETURN_GENERATED_KEYS)) {

                    for (Deployment d : deployments) {
                        if (d.id > 0) {
                            update.setString(1, safe(d.name));
                            update.setString(2, safe(d.ritmNumber));
                            update.setString(3, safe(d.environment));
                            update.setString(4, safe(d.requestedDate));
                            update.setString(5, safe(d.deployedDate));
                            update.setString(6, safe(d.status));
                            update.setString(7, safe(d.requestedBy));
                            update.setString(8, safe(d.changeDescription));
                            update.setString(9, safe(d.tasksToDeploy));
                            update.setString(10, safe(d.testLogPath));
                            update.setString(11, safe(d.codeAnalysisPath));
                            update.setString(12, safe(d.codeMovedToTest));
                            update.setString(13, safe(d.notes));
                            update.setInt(14, d.id);
                            update.executeUpdate();
                        } else {
                            bindDeployment(insert, d, projectName);
                            insert.executeUpdate();
                            try (ResultSet keys = insert.getGeneratedKeys()) {
                                if (keys.next()) {
                                    d.id = keys.getInt(1);
                                    d.projectName = projectName;
                                }
                            }
                        }
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= CHANGE REQUESTS =================

    public List<ChangeRequest> getChangeRequests(String projectName) {
        List<ChangeRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM ChangeRequests WHERE project_name = ? ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                requests.add(readChangeRequest(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    /** Every change request across all projects, for the cross-project report. */
    public List<ChangeRequest> getAllChangeRequests() {
        List<ChangeRequest> requests = new ArrayList<>();
        String sql = "SELECT * FROM ChangeRequests ORDER BY project_name COLLATE NOCASE, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                requests.add(readChangeRequest(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return requests;
    }

    /** Every deployment across all projects, for the cross-project report. */
    public List<Deployment> getAllDeployments() {
        List<Deployment> deployments = new ArrayList<>();
        String sql = "SELECT * FROM Deployments ORDER BY project_name COLLATE NOCASE, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                deployments.add(readDeployment(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return deployments;
    }

    private ChangeRequest readChangeRequest(ResultSet rs) throws SQLException {
        ChangeRequest cr = new ChangeRequest();
        cr.id = rs.getInt("id");
        cr.projectName = safe(rs.getString("project_name"));
        cr.crNumber = safe(rs.getString("cr_number"));
        cr.title = safe(rs.getString("title"));
        cr.requestedBy = safe(rs.getString("requested_by"));
        cr.receivedDate = safe(rs.getString("received_date"));
        cr.priority = safe(rs.getString("priority"));
        cr.status = safe(rs.getString("status"));
        cr.targetDate = safe(rs.getString("target_date"));
        cr.deliveredDate = safe(rs.getString("delivered_date"));
        cr.deploymentId = rs.getInt("deployment_id");
        cr.notes = safe(rs.getString("notes"));
        cr.assignedTo = safe(rs.getString("assigned_to"));
        return cr;
    }

    /** Inserts a single change request, e.g. from the create form. */
    public boolean addChangeRequest(ChangeRequest cr) {
        String sql = "INSERT INTO ChangeRequests (project_name, cr_number, title, requested_by, " +
                "received_date, priority, status, target_date, delivered_date, deployment_id, " +
                "notes, assigned_to) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, safe(cr.projectName));
            pstmt.setString(2, safe(cr.crNumber));
            pstmt.setString(3, safe(cr.title));
            pstmt.setString(4, safe(cr.requestedBy));
            pstmt.setString(5, safe(cr.receivedDate));
            pstmt.setString(6, safe(cr.priority));
            pstmt.setString(7, safe(cr.status));
            pstmt.setString(8, safe(cr.targetDate));
            pstmt.setString(9, safe(cr.deliveredDate));
            pstmt.setInt(10, cr.deploymentId);
            pstmt.setString(11, safe(cr.notes));
            pstmt.setString(12, safe(cr.assignedTo));
            pstmt.executeUpdate();
            try (ResultSet keys = pstmt.getGeneratedKeys()) {
                if (keys.next()) cr.id = keys.getInt(1);
            }
            return true;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Nothing references change request ids, so the list is replaced wholesale. */
    public void saveChangeRequests(String projectName, List<ChangeRequest> requests) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM ChangeRequests WHERE project_name = ?")) {
                    delete.setString(1, projectName);
                    delete.executeUpdate();
                }
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO ChangeRequests (project_name, cr_number, title, requested_by, " +
                                "received_date, priority, status, target_date, delivered_date, " +
                                "deployment_id, notes, assigned_to) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
                    for (ChangeRequest cr : requests) {
                        if (safe(cr.crNumber).isEmpty() && safe(cr.title).isEmpty()) continue;
                        insert.setString(1, projectName);
                        insert.setString(2, safe(cr.crNumber));
                        insert.setString(3, safe(cr.title));
                        insert.setString(4, safe(cr.requestedBy));
                        insert.setString(5, safe(cr.receivedDate));
                        insert.setString(6, safe(cr.priority));
                        insert.setString(7, safe(cr.status));
                        insert.setString(8, safe(cr.targetDate));
                        insert.setString(9, safe(cr.deliveredDate));
                        insert.setInt(10, cr.deploymentId);
                        insert.setString(11, safe(cr.notes));
                        insert.setString(12, safe(cr.assignedTo));
                        insert.addBatch();
                    }
                    insert.executeBatch();
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ================= APPLICATIONS =================

    /** The master list of applications, in display order. */
    public List<String> getApplications() {
        List<String> applications = new ArrayList<>();
        String sql = "SELECT name FROM Applications ORDER BY sort_order, id";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                applications.add(safe(rs.getString("name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return applications;
    }

    /** Appends an application. Returns false when the name already exists. */
    public boolean addApplication(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        String sql = "INSERT OR IGNORE INTO Applications (name, sort_order) VALUES (?, " +
                "(SELECT IFNULL(MAX(sort_order), 0) + 1 FROM Applications))";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, name.trim());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Removes an application and unlinks it from every project. */
    public void removeApplication(String name) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM Applications WHERE name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
            try (PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM ProjectApplications WHERE application_name = ?")) {
                pstmt.setString(1, name);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /** Applications linked to one project. */
    public List<String> getProjectApplications(String projectName) {
        List<String> applications = new ArrayList<>();
        String sql = "SELECT application_name FROM ProjectApplications " +
                "WHERE project_name = ? ORDER BY application_name COLLATE NOCASE";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, projectName);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                applications.add(safe(rs.getString("application_name")));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return applications;
    }

    /** Replaces a project's application links in one transaction. */
    public void replaceProjectApplications(String projectName, List<String> applications) {
        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement delete = conn.prepareStatement(
                        "DELETE FROM ProjectApplications WHERE project_name = ?")) {
                    delete.setString(1, projectName);
                    delete.executeUpdate();
                }
                if (applications != null && !applications.isEmpty()) {
                    try (PreparedStatement insert = conn.prepareStatement(
                            "INSERT OR IGNORE INTO ProjectApplications " +
                                    "(project_name, application_name) VALUES (?, ?)")) {
                        for (String application : applications) {
                            insert.setString(1, projectName);
                            insert.setString(2, application);
                            insert.addBatch();
                        }
                        insert.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Application-to-project pairs for the App Usage report, as
     * {@code {applicationName, projectName}}. Applications nobody uses come
     * back once with an empty project name so they still appear in the report.
     */
    public List<String[]> getApplicationUsage() {
        List<String[]> usage = new ArrayList<>();
        String sql = "SELECT a.name AS application_name, IFNULL(pa.project_name, '') AS project_name " +
                "FROM Applications a " +
                "LEFT JOIN ProjectApplications pa ON pa.application_name = a.name " +
                "ORDER BY a.sort_order, a.id, pa.project_name COLLATE NOCASE";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                usage.add(new String[]{
                        safe(rs.getString("application_name")),
                        safe(rs.getString("project_name"))
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return usage;
    }

    // ================= AUTOMATION ANYWHERE RUN HISTORY =================

    /**
     * Inserts or refreshes scraped activity rows. Existing rows are updated in
     * place, so repeated scrapes of an overlapping window never duplicate.
     *
     * @return the number of rows written
     */
    public int upsertActivities(List<AaActivity> activities) {
        if (activities == null || activities.isEmpty()) return 0;

        String sql = "INSERT INTO AaActivity (id, activity_name, automation_name, automation_type, " +
                "device_name, run_as_user, status, running_time, activity_type, started_on, " +
                "started_display, ended_on, fetched_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                "ON CONFLICT(id) DO UPDATE SET " +
                "activity_name = excluded.activity_name, " +
                "automation_name = excluded.automation_name, " +
                "automation_type = excluded.automation_type, " +
                "device_name = excluded.device_name, " +
                "run_as_user = excluded.run_as_user, " +
                "status = excluded.status, " +
                "running_time = excluded.running_time, " +
                "activity_type = excluded.activity_type, " +
                "started_on = excluded.started_on, " +
                "started_display = excluded.started_display, " +
                "ended_on = excluded.ended_on, " +
                "fetched_at = excluded.fetched_at";

        String fetchedAt = java.time.LocalDateTime.now().toString();
        int written = 0;

        try (Connection conn = DriverManager.getConnection(DB_URL)) {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (AaActivity a : activities) {
                    if (a.id == null || a.id.trim().isEmpty()) continue;
                    pstmt.setString(1, a.id);
                    pstmt.setString(2, safe(a.activityName));
                    pstmt.setString(3, safe(a.automationName));
                    pstmt.setString(4, safe(a.automationType));
                    pstmt.setString(5, safe(a.deviceName));
                    pstmt.setString(6, safe(a.runAsUser));
                    pstmt.setString(7, safe(a.status));
                    pstmt.setString(8, safe(a.runningTime));
                    pstmt.setString(9, safe(a.activityType));
                    pstmt.setString(10, safe(a.startedOn));
                    pstmt.setString(11, safe(a.startedDisplay));
                    pstmt.setString(12, safe(a.endedOn));
                    pstmt.setString(13, fetchedAt);
                    pstmt.addBatch();
                    written++;
                }
                pstmt.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
        return written;
    }

    /**
     * Activity rows started at or after {@code isoCutoff}, grouped-ready:
     * ordered by device then most recent first. Rows whose start time could not
     * be parsed are always included so nothing silently disappears.
     */
    public List<AaActivity> getActivitiesSince(String isoCutoff) {
        List<AaActivity> activities = new ArrayList<>();
        String sql = "SELECT * FROM AaActivity WHERE started_on >= ? OR started_on = '' " +
                "ORDER BY device_name COLLATE NOCASE, started_on DESC";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, isoCutoff == null ? "" : isoCutoff);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                AaActivity a = new AaActivity();
                a.id = safe(rs.getString("id"));
                a.activityName = safe(rs.getString("activity_name"));
                a.automationName = safe(rs.getString("automation_name"));
                a.automationType = safe(rs.getString("automation_type"));
                a.deviceName = safe(rs.getString("device_name"));
                a.runAsUser = safe(rs.getString("run_as_user"));
                a.status = safe(rs.getString("status"));
                a.runningTime = safe(rs.getString("running_time"));
                a.activityType = safe(rs.getString("activity_type"));
                a.startedOn = safe(rs.getString("started_on"));
                a.startedDisplay = safe(rs.getString("started_display"));
                a.endedOn = safe(rs.getString("ended_on"));
                activities.add(a);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return activities;
    }

    // ================= EXTERNAL BOT DATABASES =================

    /**
     * Turns a configured DB Path into a JDBC URL.
     * <p>
     * A value that already looks like a JDBC URL is passed through untouched,
     * {@code .accdb} and {@code .mdb} files go through UCanAccess, and anything
     * else is treated as a SQLite file.
     */
    public static String toJdbcUrl(String dbPath) {
        String path = dbPath == null ? "" : dbPath.trim();
        if (path.toLowerCase().startsWith("jdbc:")) return path;

        String lower = path.toLowerCase();
        if (lower.endsWith(".accdb") || lower.endsWith(".mdb")) {
            return "jdbc:ucanaccess://" + path;
        }
        return "jdbc:sqlite:" + path;
    }

    /**
     * Runs a query against a project's own database and returns the first
     * column of the first row. Used by the Bot Run Status report, so the query
     * is expected to produce a single value.
     */
    public String queryExternalScalar(String dbPath, String sql) throws SQLException {
        try (Connection conn = DriverManager.getConnection(toJdbcUrl(dbPath));
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                Object value = rs.getObject(1);
                return value == null ? "" : value.toString();
            }
        }
        return "";
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    // ================= APPLICATION SETTINGS =================

    /** Reads a persisted preference, falling back to {@code defaultValue}. */
    public String getSetting(String key, String defaultValue) {
        String sql = "SELECT value FROM Settings WHERE key = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String value = rs.getString("value");
                if (value != null) return value;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return defaultValue;
    }

    /** Stores a preference, overwriting any previous value. */
    public void setSetting(String key, String value) {
        String sql = "INSERT INTO Settings (key, value) VALUES (?, ?) " +
                "ON CONFLICT(key) DO UPDATE SET value = ?";
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, key);
            pstmt.setString(2, value);
            pstmt.setString(3, value);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}