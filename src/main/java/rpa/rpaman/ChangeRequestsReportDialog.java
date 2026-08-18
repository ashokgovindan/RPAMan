package rpa.rpaman;

import java.awt.Frame;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Every change request across every RPA, split by whether it is still live. */
public class ChangeRequestsReportDialog extends StatusSplitDialog {

    private static final String[] COLUMNS = {
            "RPA Name", "CR Number", "Title", "Assigned To", "Priority", "Status",
            "Requested By", "Received", "Target", "Delivered", "Deployment"
    };
    private static final int[] WIDTHS = {160, 110, 260, 150, 90, 130, 130, 100, 100, 100, 180};
    private static final int STATUS_COLUMN = 5;

    public ChangeRequestsReportDialog(Frame owner, DatabaseManager dbManager) {
        super(owner, dbManager,
                "Change Requests - All Projects",
                "Change Requests across all RPAs",
                AppIcons.inbox(17, "App.accent"),
                "Open  -  not started or in progress",
                "Closed  -  deployed, rejected, cancelled or withdrawn",
                COLUMNS, WIDTHS, STATUS_COLUMN);
        refresh();
    }

    @Override
    protected void loadRows(List<Object[]> active, List<Object[]> closed) {
        // Resolve deployment ids to readable labels in one pass
        Map<Integer, String> deploymentLabels = new HashMap<>();
        for (Deployment deployment : dbManager.getAllDeployments()) {
            deploymentLabels.put(deployment.id, deployment.label());
        }

        for (ChangeRequest cr : dbManager.getAllChangeRequests()) {
            Object[] row = {
                    cr.projectName,
                    cr.crNumber,
                    cr.title,
                    cr.assignedTo,
                    cr.priority,
                    cr.status,
                    cr.requestedBy,
                    cr.receivedDate,
                    cr.targetDate,
                    cr.deliveredDate,
                    deploymentLabels.getOrDefault(cr.deploymentId, "")
            };
            if (ChangeRequest.isClosed(cr.status)) {
                closed.add(row);
            } else {
                active.add(row);
            }
        }
    }
}
