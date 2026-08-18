package rpa.rpaman;

/** Side work assigned to a developer that is neither a change request nor a deployment. */
public class AdhocItem {

    public static final String[] STATUSES = {
            "New", "In Progress", "Blocked", "Done", "Cancelled"
    };

    public static final String[] PRIORITIES = {"Low", "Medium", "High", "Critical"};

    private static final String[] CLOSED = {"Done", "Cancelled"};

    /** True once the item needs no further work. */
    public static boolean isClosed(String status) {
        if (status == null) return false;
        for (String closed : CLOSED) {
            if (closed.equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }

    /** Database id; 0 means the row has not been saved yet. */
    public int id;

    public String developerName = "";
    public String title = "";
    public String description = "";
    public String status = "New";
    public String priority = "Medium";
    public String startDate = "";
    public String dueDate = "";
    public String completedDate = "";
}
