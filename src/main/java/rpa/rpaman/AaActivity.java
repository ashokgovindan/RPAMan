package rpa.rpaman;

/**
 * One row of the Automation Anywhere "Historical activity" table.
 * <p>
 * A plain data holder shared by the scraper, the database layer and the
 * run-history dialog.
 */
public class AaActivity {

    /** Stable key: automation + activity + start time, so re-scrapes update rather than duplicate. */
    public String id = "";

    public String activityName = "";
    public String automationName = "";
    public String automationType = "";
    public String deviceName = "";
    public String runAsUser = "";
    public String status = "";
    public String runningTime = "";
    public String activityType = "";

    /** Sortable ISO form, e.g. {@code 2026-07-30T05:53:09}. Empty when unparseable. */
    public String startedOn = "";

    /** Exactly what the Control Room showed, kept for display. */
    public String startedDisplay = "";

    public String endedOn = "";

    /** Device label used for grouping; falls back to a placeholder when blank. */
    public String deviceLabel() {
        String device = deviceName == null ? "" : deviceName.trim();
        if (device.isEmpty() || "--".equals(device) || "-".equals(device)) {
            return "(no device)";
        }
        return device;
    }

    /** Builds the primary key from the identifying fields. */
    public void buildId() {
        id = (automationName + "|" + activityName + "|" + startedDisplay).trim();
    }
}
