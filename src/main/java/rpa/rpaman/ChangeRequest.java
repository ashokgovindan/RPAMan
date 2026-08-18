package rpa.rpaman;

/** A change request received against one RPA, tracked through to delivery. */
public class ChangeRequest {

    public static final String[] STATUSES = {
            "New", "Analysis", "In Development", "Testing", "UAT",
            "On Hold", "Deployed", "Rejected", "Cancelled", "Withdrawn"
    };

    public static final String[] PRIORITIES = {"Low", "Medium", "High", "Critical"};

    /** Terminal statuses: the request needs no further delivery work. */
    private static final String[] CLOSED = {"Deployed", "Rejected", "Cancelled", "Withdrawn"};

    /** True when the request has finished its life cycle, however it ended. */
    public static boolean isClosed(String status) {
        if (status == null) return false;
        for (String closed : CLOSED) {
            if (closed.equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }

    /** Database id; 0 means the row has not been saved yet. */
    public int id;

    public String projectName = "";
    public String crNumber = "";
    public String title = "";
    public String requestedBy = "";
    public String receivedDate = "";
    public String priority = "Medium";
    public String status = "New";
    public String targetDate = "";
    public String deliveredDate = "";
    public String notes = "";

    /** Single owning developer; empty when unassigned. */
    public String assignedTo = "";

    /** Deployment that shipped this CR; 0 when not linked yet. */
    public int deploymentId;
}
