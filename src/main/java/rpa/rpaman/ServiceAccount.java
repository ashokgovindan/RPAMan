package rpa.rpaman;

/**
 * A service account an RPA runs under, tracked for reference only.
 * <p>
 * No credential is held here — just enough to identify the account and who to
 * contact about it.
 */
public class ServiceAccount {

    public static final String[] ENVIRONMENTS = {"DEV", "TEST", "UAT", "PROD"};

    /** Database id; 0 means the row has not been saved yet. */
    public int id;

    public String projectName = "";
    public String environment = "PROD";
    public String accountId = "";
    public String alias = "";
    public String appName = "";
    public String email = "";
    public String description = "";

    /**
     * Labelled block for pasting into a ticket, chat or email. Deliberately a
     * short subset — the identifying fields, not the whole record.
     */
    public String detailsBlock() {
        return "RPA Name: " + projectName + "\n"
                + "Account ID: " + accountId + "\n"
                + "Alias: " + alias + "\n"
                + "App Name: " + appName;
    }
}
