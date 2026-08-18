package rpa.rpaman;

import java.awt.Frame;
import java.util.List;

/** Every deployment request across every RPA, split by whether it is still live. */
public class DeploymentsReportDialog extends StatusSplitDialog {

    private static final String[] COLUMNS = {
            "RPA Name", "Deployment", "RITM #", "Environment", "Status",
            "Requested By", "Requested", "Deployed", "Code Moved to Test", "Change Description"
    };
    private static final int[] WIDTHS = {160, 180, 120, 110, 130, 130, 100, 100, 140, 280};
    private static final int STATUS_COLUMN = 4;

    public DeploymentsReportDialog(Frame owner, DatabaseManager dbManager) {
        super(owner, dbManager,
                "Deployments - All Projects",
                "Deployments across all RPAs",
                AppIcons.upload(17, "App.accent"),
                "Open  -  requested, approved or scheduled",
                "Closed  -  deployed, failed, rolled back or cancelled",
                COLUMNS, WIDTHS, STATUS_COLUMN);
        refresh();
    }

    @Override
    protected void loadRows(List<Object[]> active, List<Object[]> closed) {
        for (Deployment deployment : dbManager.getAllDeployments()) {
            Object[] row = {
                    deployment.projectName,
                    deployment.name,
                    deployment.ritmNumber,
                    deployment.environment,
                    deployment.status,
                    deployment.requestedBy,
                    deployment.requestedDate,
                    deployment.deployedDate,
                    deployment.codeMovedToTest,
                    deployment.changeDescription
            };
            if (Deployment.isClosed(deployment.status)) {
                closed.add(row);
            } else {
                active.add(row);
            }
        }
    }
}
