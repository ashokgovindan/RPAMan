package rpa.rpaman;

/**
 * A deployment request raised for one RPA. A deployment can carry several
 * change requests and records the RITM opened for it.
 */
public class Deployment {

    public static final String[] STATUSES = {
            "Requested", "Approved", "Scheduled",
            "Deployed", "Failed", "Rolled Back", "Cancelled"
    };

    public static final String[] ENVIRONMENTS = {"DEV", "TEST", "UAT", "PROD"};

    /** Terminal statuses: the request has run its course, successfully or not. */
    private static final String[] CLOSED = {"Deployed", "Failed", "Rolled Back", "Cancelled"};

    /** True when the deployment has finished its life cycle. */
    public static boolean isClosed(String status) {
        if (status == null) return false;
        for (String closed : CLOSED) {
            if (closed.equalsIgnoreCase(status.trim())) return true;
        }
        return false;
    }

    public static final String[] YES_NO = {"Yes", "No"};

    /** Database id; 0 means the row has not been saved yet. */
    public int id;

    public String projectName = "";
    public String name = "";
    public String ritmNumber = "";
    public String environment = "PROD";
    public String requestedDate = "";
    public String deployedDate = "";
    public String status = "Requested";
    public String requestedBy = "";
    public String changeDescription = "";
    public String tasksToDeploy = "";
    public String testLogPath = "";
    public String codeAnalysisPath = "";
    public String codeMovedToTest = "No";
    public String notes = "";

    /** Label used in the change request's deployment picker. */
    public String label() {
        String base = name == null || name.trim().isEmpty() ? "Deployment #" + id : name.trim();
        String ritm = ritmNumber == null ? "" : ritmNumber.trim();
        if (!ritm.isEmpty()) base = base + " / " + ritm;

        String env = environment == null ? "" : environment.trim();
        return env.isEmpty() ? base : base + " (" + env + ")";
    }

    @Override
    public String toString() {
        return label();
    }
}
