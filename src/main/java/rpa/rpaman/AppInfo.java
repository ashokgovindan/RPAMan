package rpa.rpaman;

/** Product identity in one place, so the title bar and About page agree. */
public final class AppInfo {

    public static final String NAME = "AshApp";
    public static final String TITLE = "RPA Project Manager";
    public static final String VERSION = "1.0";
    public static final String TAGLINE =
            "Track RPA projects, change requests and deployments in one place.";

    /** Text for the main window's title bar. */
    public static String windowTitle() {
        return NAME + " - " + TITLE;
    }

    private AppInfo() {
    }
}
