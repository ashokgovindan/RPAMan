package rpa.rpaman;

/**
 * A non-checkable child node under a project, used for the Change Requests and
 * Deployments sections of the tree.
 */
public class SectionItem {

    public enum Kind {
        CHANGE_REQUESTS("Change Requests"),
        DEPLOYMENTS("Deployments"),
        SERVICE_ACCOUNTS("Service Accounts");

        public final String label;

        Kind(String label) {
            this.label = label;
        }
    }

    private final Kind kind;

    public SectionItem(Kind kind) {
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    public String getText() {
        return kind.label;
    }

    @Override
    public String toString() {
        return kind.label;
    }
}
