package rpa.rpaman;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.FontUIResource;
import javax.swing.plaf.nimbus.NimbusLookAndFeel;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Central place for the application's visual identity.
 * <p>
 * Built entirely on the JDK: Nimbus provides the base widgets and is recoloured
 * per theme through its {@code nimbus*} seed colours, which it reads when the
 * look and feel is installed. On top of that a small palette of {@code App.*}
 * colours is published into {@link UIManager} for the custom-painted parts
 * (cards, pills, icons) to read at paint time.
 */
public final class ThemeManager {

    public static final String SETTING_KEY = "ui.theme";

    /** The three curated themes exposed in the View &gt; Themes menu. */
    public enum Theme {

        LIGHT("Light", false,
                "#0F6CBD",   // accent
                "#F4F6F9",   // window / panel background
                "#FFFFFF",   // card
                "#EEF3FA",   // canvas (details area)
                "#D7DFEA",   // border
                "#F5F9FE",   // stripe
                "#DCE8F7",   // table header background
                "#14508F",   // table header foreground
                "#5E6C82"),  // subtle foreground

        BLUE("Blue", false,
                "#1565C0",
                "#EAF1FB",
                "#FFFFFF",
                "#DCE9F9",
                "#BFD4EC",
                "#F2F7FE",
                "#C9DDF5",
                "#0D4A8F",
                "#4A5F7A"),

        DARK("Dark", true,
                "#4C9AFF",
                "#2B2F36",
                "#333841",
                "#24282E",
                "#414753",
                "#2F343C",
                "#363C46",
                "#9EC5FF",
                "#98A3B3");

        public final String displayName;
        public final boolean dark;
        final String accent, background, card, canvas, border, stripe, headerBg, headerFg, subtle;

        Theme(String displayName, boolean dark, String accent, String background, String card,
              String canvas, String border, String stripe, String headerBg, String headerFg, String subtle) {
            this.displayName = displayName;
            this.dark = dark;
            this.accent = accent;
            this.background = background;
            this.card = card;
            this.canvas = canvas;
            this.border = border;
            this.stripe = stripe;
            this.headerBg = headerBg;
            this.headerFg = headerFg;
            this.subtle = subtle;
        }
    }

    private static final List<Runnable> REFRESHERS = new ArrayList<>();
    private static final List<StyleHook<?>> STYLE_HOOKS = new ArrayList<>();
    private static Theme current = Theme.LIGHT;
    private static DatabaseManager db;
    private static Set<String> availableFonts;
    private static Font appFont;

    private ThemeManager() {
    }

    // ---------------------------------------------------------------- lifecycle

    /** Installs the last theme the user chose (or Light on first run). */
    public static void init(DatabaseManager databaseManager) {
        db = databaseManager;
        Theme startup = Theme.LIGHT;
        if (db != null) {
            String saved = db.getSetting(SETTING_KEY, Theme.LIGHT.name());
            for (Theme t : Theme.values()) {
                if (t.name().equalsIgnoreCase(saved)) {
                    startup = t;
                    break;
                }
            }
        }
        current = startup;
        install(startup);
    }

    /** Switches theme at runtime and repaints every open window. */
    public static void setTheme(Theme theme) {
        if (theme == null) return;
        current = theme;
        install(theme);
        if (db != null) db.setSetting(SETTING_KEY, theme.name());

        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
        }
        for (Runnable r : new ArrayList<>(REFRESHERS)) {
            try {
                r.run();
            } catch (RuntimeException ignored) {
                // a single misbehaving component must not break the switch
            }
        }
        // Re-style tracked components, dropping any that have been collected
        Iterator<StyleHook<?>> hooks = STYLE_HOOKS.iterator();
        while (hooks.hasNext()) {
            StyleHook<?> hook = hooks.next();
            try {
                if (!hook.apply()) hooks.remove();
            } catch (RuntimeException ignored) {
                hooks.remove();
            }
        }
        for (Window window : Window.getWindows()) {
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }

    public static Theme current() {
        return current;
    }

    public static boolean isDark() {
        return current.dark;
    }

    /**
     * Registers a callback invoked after every theme change. Use it for
     * components whose colours cannot be derived lazily at paint time.
     */
    public static void onThemeChanged(Runnable r) {
        if (r != null) REFRESHERS.add(r);
    }

    /**
     * Re-applies {@code styler} to {@code component} on every theme change.
     * <p>
     * The component is held weakly and the styler must not capture it, so
     * short-lived windows such as the settings dialog can be garbage collected.
     */
    public static <T extends Component> void styleOnThemeChange(T component, Consumer<T> styler) {
        if (component == null || styler == null) return;
        styler.accept(component);
        STYLE_HOOKS.add(new StyleHook<>(component, styler));
    }

    /** A weakly-held component plus the code that re-styles it. */
    private static final class StyleHook<T extends Component> {
        private final WeakReference<T> ref;
        private final Consumer<T> styler;

        StyleHook(T component, Consumer<T> styler) {
            this.ref = new WeakReference<>(component);
            this.styler = styler;
        }

        /** Returns false once the component has been collected. */
        boolean apply() {
            T component = ref.get();
            if (component == null) return false;
            styler.accept(component);
            return true;
        }
    }

    // ---------------------------------------------------------------- install

    private static void install(Theme t) {
        // Nimbus derives its whole palette from these seeds when it is
        // installed, so they have to be in place beforehand.
        seedNimbusColors(t);

        try {
            UIManager.setLookAndFeel(new NimbusLookAndFeel());
        } catch (UnsupportedLookAndFeelException e) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // fall back to whatever is already installed
            }
        }

        applyShape();
        applyPalette(t);
        installFont();
    }

    /** Recolours Nimbus itself using its documented seed colours. */
    private static void seedNimbusColors(Theme t) {
        Color accent = hex(t.accent);
        Color background = hex(t.background);
        Color card = hex(t.card);
        Color border = hex(t.border);
        Color subtle = hex(t.subtle);
        Color text = readableOn(card);

        put("control", background);
        put("controlText", text);
        put("text", text);
        put("menu", background);
        put("menuText", text);
        put("info", card);
        put("scrollbar", blend(border, background, 0.5f));

        put("nimbusBase", accent);
        put("nimbusBlueGrey", blend(border, background, 0.75f));
        put("nimbusLightBackground", card);
        put("nimbusBorder", border);
        put("nimbusFocus", accent);
        put("nimbusDisabledText", subtle);
        put("nimbusSelectionBackground", blend(accent, card, 0.75f));
        put("nimbusSelectedText", readableOn(blend(accent, card, 0.75f)));

        put("textForeground", text);
        put("textBackground", card);
        put("textHighlight", blend(accent, card, 0.35f));
        put("textHighlightText", text);
        put("textInactiveText", subtle);
    }

    /** Row heights and grid behaviour that plain Swing understands. */
    private static void applyShape() {
        UIManager.put("Tree.rowHeight", 26);
        UIManager.put("Tree.paintLines", Boolean.FALSE);
        UIManager.put("Table.rowHeight", 26);
        UIManager.put("Table.showHorizontalLines", Boolean.TRUE);
        UIManager.put("Table.showVerticalLines", Boolean.TRUE);
        // 1px spacing so the grid lines are actually drawn between cells
        UIManager.put("Table.intercellSpacing", new Dimension(1, 1));
        UIManager.put("ToolTip.paintBackground", Boolean.TRUE);
        UIManager.put("SplitPane.dividerSize", 6);
        UIManager.put("SplitPane.oneTouchButtonSize", 0);
    }

    /** Publishes the {@code App.*} palette consumed across the app. */
    private static void applyPalette(Theme t) {
        put("App.accent", hex(t.accent));
        put("App.card", hex(t.card));
        put("App.canvas", hex(t.canvas));
        put("App.border", hex(t.border));
        put("App.stripe", hex(t.stripe));
        put("App.tableHeaderBackground", hex(t.headerBg));
        put("App.tableHeaderForeground", hex(t.headerFg));
        put("App.subtleForeground", hex(t.subtle));

        Color accent = hex(t.accent);
        Color card = hex(t.card);
        Color text = readableOn(card);

        put("App.accentSoft", blend(accent, card, 0.16f));
        put("App.accentHover", blend(accent, card, 0.30f));
        put("App.icon", hex(t.subtle));
        put("App.foreground", text);
        put("App.onAccent", readableOn(accent));

        put("Table.alternateRowColor", hex(t.stripe));
        put("Table.gridColor", blend(hex(t.border), card, 0.55f));
        put("Table.background", card);
        put("Table.foreground", text);
        put("Table.selectionBackground", blend(accent, card, 0.22f));
        put("Table.selectionForeground", text);

        put("Tree.background", card);
        put("Tree.foreground", text);
        put("Tree.textForeground", text);
        put("Tree.selectionBackground", blend(accent, card, 0.18f));
        put("Tree.selectionForeground", text);

        put("List.background", card);
        put("List.foreground", text);
        put("List.selectionBackground", blend(accent, card, 0.22f));
        put("List.selectionForeground", text);

        put("Label.foreground", text);
        put("Panel.background", hex(t.background));
        put("TextField.caretForeground", text);
        put("TextArea.caretForeground", text);

        // Status badge colours used by the tables
        if (t.dark) {
            put("App.badgeTodoBg", hex("#3A414D"));
            put("App.badgeTodoFg", hex("#C6CFDC"));
            put("App.badgeProgressBg", hex("#4A3B1E"));
            put("App.badgeProgressFg", hex("#F2C46B"));
            put("App.badgeDoneBg", hex("#1F4034"));
            put("App.badgeDoneFg", hex("#79D3A5"));
            put("App.badgeErrorBg", hex("#4A2A2A"));
            put("App.badgeErrorFg", hex("#F09393"));
        } else {
            put("App.badgeTodoBg", hex("#E6EBF2"));
            put("App.badgeTodoFg", hex("#4A5768"));
            put("App.badgeProgressBg", hex("#FDF0D5"));
            put("App.badgeProgressFg", hex("#96631A"));
            put("App.badgeDoneBg", hex("#DCF3E6"));
            put("App.badgeDoneFg", hex("#1E7A4C"));
            put("App.badgeErrorBg", hex("#FBE1E1"));
            put("App.badgeErrorFg", hex("#B3261E"));
        }
    }

    /**
     * Nimbus has no single "default font" key, so every font entry in the
     * look and feel defaults is replaced individually.
     */
    private static void installFont() {
        if (availableFonts == null) {
            availableFonts = new HashSet<>(Arrays.asList(GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getAvailableFontFamilyNames()));
        }
        if (appFont == null) {
            String[] preferred = {"Segoe UI Variable Text", "Segoe UI", "Inter",
                    "SF Pro Text", "Roboto", "Noto Sans", "DejaVu Sans"};
            String family = Font.SANS_SERIF;
            for (String candidate : preferred) {
                if (availableFonts.contains(candidate)) {
                    family = candidate;
                    break;
                }
            }
            appFont = new Font(family, Font.PLAIN, 13);
        }

        FontUIResource resource = new FontUIResource(appFont);
        try {
            UIDefaults defaults = UIManager.getLookAndFeelDefaults();
            for (Object key : new ArrayList<>(defaults.keySet())) {
                if (defaults.get(key) instanceof Font) {
                    UIManager.put(key, resource);
                }
            }
        } catch (RuntimeException ignored) {
            // Nimbus builds some defaults lazily; a partial pass is fine
        }
    }

    /** The font every screen uses, for components that derive their own. */
    public static Font appFont() {
        return appFont != null ? appFont : new Font(Font.SANS_SERIF, Font.PLAIN, 13);
    }

    // ---------------------------------------------------------------- colours

    private static void put(String key, Color color) {
        UIManager.put(key, new ColorUIResource(color));
    }

    public static Color hex(String value) {
        return Color.decode(value);
    }

    /** Picks white or near-black text so it stays legible on {@code background}. */
    public static Color readableOn(Color background) {
        double luminance = (0.299 * background.getRed()
                + 0.587 * background.getGreen()
                + 0.114 * background.getBlue()) / 255.0;
        return luminance > 0.62 ? new Color(0x10, 0x18, 0x24) : Color.WHITE;
    }

    /** Mixes {@code ratio} of {@code fg} into {@code bg}. */
    public static Color blend(Color fg, Color bg, float ratio) {
        float r = Math.max(0f, Math.min(1f, ratio));
        return new Color(
                Math.round(fg.getRed() * r + bg.getRed() * (1 - r)),
                Math.round(fg.getGreen() * r + bg.getGreen() * (1 - r)),
                Math.round(fg.getBlue() * r + bg.getBlue() * (1 - r)));
    }

    /** {@link UIManager} colour lookup with a fallback. */
    public static Color color(String key, Color fallback) {
        Color c = UIManager.getColor(key);
        return c != null ? new Color(c.getRGB(), true) : fallback;
    }

    public static Color accent() {
        return color("App.accent", new Color(0x0F6CBD));
    }

    public static Color card() {
        return color("App.card", Color.WHITE);
    }

    public static Color border() {
        return color("App.border", new Color(0xD7DFEA));
    }

    public static Color subtle() {
        return color("App.subtleForeground", Color.GRAY);
    }
}
