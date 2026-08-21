package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.lang.ref.WeakReference;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Small builders that give every screen the same look: rounded cards,
 * accent-coloured headers, pill buttons and theme-aware date pickers.
 */
public final class UiFactory {

    public static final int GAP = 12;

    /** Vertical space between a pane's title and the row under it. */
    public static final int HEADER_GAP = 8;

    private static int cachedHeaderHeight = -1;

    // Weak so short-lived windows (the settings dialog) can be collected
    private static final List<WeakReference<DatePicker>> DATE_PICKERS = new ArrayList<>();
    private static final List<WeakReference<JButton>> PRIMARY_BUTTONS = new ArrayList<>();

    static {
        ThemeManager.onThemeChanged(UiFactory::refreshRegisteredComponents);
    }

    private UiFactory() {
    }

    // ------------------------------------------------------------- headings

    /** Bold, accent-coloured section heading with a leading icon. */
    public static JLabel sectionTitle(String text, Icon icon) {
        JLabel label = new JLabel(text, icon, SwingConstants.LEADING);
        label.setIconTextGap(8);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 15f));
        ThemeManager.styleOnThemeChange(label, l -> l.setForeground(ThemeManager.accent()));
        return label;
    }

    /** Muted secondary line used under section titles. */
    public static JLabel subtitle(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        ThemeManager.styleOnThemeChange(label, l -> l.setForeground(ThemeManager.subtle()));
        return label;
    }

    /** Small bold label used for form field captions. */
    public static JLabel fieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 12f));
        ThemeManager.styleOnThemeChange(label, l -> l.setForeground(ThemeManager.accent()));
        return label;
    }

    /**
     * Fixed-height heading block: a title with an optional second row beneath.
     * <p>
     * Every pane uses one of these so the tree, the details card and the tasks
     * table all start on the same line, no matter what the second row holds.
     */
    public static JPanel headerBlock(JLabel title, JComponent secondRow) {
        return headerBlock((JComponent) title, secondRow);
    }

    public static JPanel headerBlock(JComponent title, JComponent secondRow) {
        JPanel panel = transparent(new BorderLayout(0, HEADER_GAP));
        panel.add(title, BorderLayout.NORTH);

        if (secondRow != null) {
            // NORTH inside the holder keeps the row at its natural height and
            // pushes any leftover slack below it, instead of stretching it.
            JPanel holder = transparent(new BorderLayout());
            holder.add(secondRow, BorderLayout.NORTH);
            panel.add(holder, BorderLayout.CENTER);
        }

        int height = headerHeight();
        panel.setPreferredSize(new Dimension(0, height));
        panel.setMinimumSize(new Dimension(0, height));
        return panel;
    }

    /**
     * Height of a heading block, measured from real components so it follows
     * the current font and display scaling rather than a hard-coded value.
     */
    public static int headerHeight() {
        if (cachedHeaderHeight < 0) {
            JLabel probeTitle = new JLabel("Ag", AppIcons.info(18, "App.accent"), SwingConstants.LEADING);
            probeTitle.setIconTextGap(8);
            probeTitle.setFont(probeTitle.getFont().deriveFont(Font.BOLD, 15f));

            JTextField probeField = textField("Ag");

            cachedHeaderHeight = probeTitle.getPreferredSize().height
                    + HEADER_GAP
                    + probeField.getPreferredSize().height;
        }
        return cachedHeaderHeight;
    }

    /** Plain label used inline next to a field, e.g. "Machine:". */
    public static JLabel inlineLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 12f));
        return label;
    }

    // ---------------------------------------------------------------- panels

    /** Transparent panel — lets the parent's background show through. */
    public static JPanel transparent(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    /** A rounded, bordered surface painted in the theme's card colour. */
    public static JPanel card(LayoutManager layout, int padding) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        panel.setBorder(new CardBorder(padding));
        return panel;
    }

    public static JPanel card(LayoutManager layout) {
        return card(layout, 14);
    }

    /** Root container for a pane: canvas background plus breathing room. */
    public static JPanel pane(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(true);
        panel.setBorder(new EmptyBorder(GAP, GAP, GAP, GAP));
        ThemeManager.styleOnThemeChange(panel,
                p -> p.setBackground(ThemeManager.color("App.canvas", p.getBackground())));
        return panel;
    }

    // ---------------------------------------------------------------- inputs

    public static JTextField textField(String placeholder) {
        HintTextField field = new HintTextField(placeholder, null);
        field.setOpaque(false);
        field.setBorder(new RoundedFieldBorder(6, 10, 6, 10, false));
        ThemeManager.styleOnThemeChange(field, f -> {
            f.setForeground(ThemeManager.color("App.foreground", Color.DARK_GRAY));
            f.setCaretColor(ThemeManager.color("App.foreground", Color.DARK_GRAY));
        });
        return field;
    }

    /** Text field with a magnifier drawn inside the left edge. */
    public static JTextField searchField(String placeholder) {
        HintTextField field = new HintTextField(placeholder,
                AppIcons.search(15, "App.subtleForeground"));
        field.setOpaque(false);
        // Extra left inset leaves room for the icon
        field.setBorder(new RoundedFieldBorder(6, 32, 6, 10, false));
        ThemeManager.styleOnThemeChange(field, f -> {
            f.setForeground(ThemeManager.color("App.foreground", Color.DARK_GRAY));
            f.setCaretColor(ThemeManager.color("App.foreground", Color.DARK_GRAY));
        });
        return field;
    }

    public static JTextArea textArea(int rows, int columns) {
        JTextArea area = new JTextArea(rows, columns);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setMargin(new Insets(6, 8, 6, 8));
        // The rounded scroll pane border paints the background instead
        area.setOpaque(false);
        ThemeManager.styleOnThemeChange(area, a -> {
            a.setForeground(ThemeManager.color("App.foreground", Color.DARK_GRAY));
            a.setCaretColor(ThemeManager.color("App.foreground", Color.DARK_GRAY));
        });
        return area;
    }

    /** Wraps a component in a rounded scroll pane that paints the field surface. */
    public static JScrollPane scroll(Component view) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(new RoundedFieldBorder(3, 3, 3, 3));
        return scrollPane;
    }

    /**
     * Vertical-only scroll pane for a form.
     * <p>
     * The view is forced to the viewport's width, so a form whose preferred
     * width grows with the platform font compresses instead of producing a
     * horizontal scrollbar. Use this for stacked cards; tables still need
     * {@link #bareScroll} so wide grids can scroll sideways.
     */
    public static JScrollPane formScroll(Component content) {
        WidthTrackingPanel holder = new WidthTrackingPanel();
        holder.add(content, BorderLayout.NORTH);

        JScrollPane scrollPane = new JScrollPane(holder);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(null);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        return scrollPane;
    }

    /** Panel that always matches the viewport width, never exceeding it. */
    private static final class WidthTrackingPanel extends JPanel implements Scrollable {

        WidthTrackingPanel() {
            super(new BorderLayout());
            setOpaque(false);
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return orientation == SwingConstants.VERTICAL
                    ? Math.max(16, visibleRect.height - 24)
                    : Math.max(16, visibleRect.width - 24);
        }

        /** The point of this class: never wider than the viewport. */
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }

    /**
     * Scroll pane pinned to a fixed height.
     * <p>
     * A JScrollPane's minimum height is only its insets, so a GridBagLayout
     * short of vertical space will happily squash one down to a single line.
     * Pinning preferred and minimum together keeps multi-line inputs readable
     * whatever the container does. Width is left free for the layout to fill.
     */
    public static JScrollPane fixedHeightScroll(Component view, int height) {
        JScrollPane scrollPane = scroll(view);
        scrollPane.setPreferredSize(new Dimension(10, height));
        scrollPane.setMinimumSize(new Dimension(10, height));
        return scrollPane;
    }

    /** Scroll pane with no border at all — for content already inside a card. */
    public static JScrollPane bareScroll(Component view) {
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setOpaque(false);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setViewportBorder(null);
        return scrollPane;
    }

    public static <T> JComboBox<T> comboBox(T[] items) {
        return new JComboBox<>(items);
    }

    // --------------------------------------------------------------- buttons

    /** Filled accent button for the primary action on a screen. */
    public static JButton primary(String text, Icon icon) {
        RoundedButton button = new RoundedButton(text, icon, true, false);
        PRIMARY_BUTTONS.add(new WeakReference<JButton>(button));
        return button;
    }

    /** Outlined button for secondary actions. */
    public static JButton secondary(String text, Icon icon) {
        return new RoundedButton(text, icon, false, false);
    }

    /** Small bordered icon button — used for the machine/user "x" remove action. */
    public static JButton compactIconButton(Icon icon, String tooltip) {
        RoundedButton button = new RoundedButton(null, icon, false, true);
        button.setToolTipText(tooltip);
        return button;
    }

    /** Small accent-filled icon button — the "+" in the settings tables. */
    public static JButton compactPrimaryIconButton(Icon icon, String tooltip) {
        RoundedButton button = new RoundedButton(null, icon, true, true);
        button.setToolTipText(tooltip);
        PRIMARY_BUTTONS.add(new WeakReference<JButton>(button));
        return button;
    }

    /** Borderless icon-only button. */
    public static JButton iconButton(Icon icon, String tooltip) {
        RoundedButton button = new RoundedButton(null, icon, false, true);
        button.setBorderless(true);
        button.setToolTipText(tooltip);
        return button;
    }

    private static void applyPrimaryColors(JButton button) {
        if (button instanceof RoundedButton) {
            ((RoundedButton) button).refreshColors();
        } else {
            button.setBackground(ThemeManager.accent());
            button.setForeground(idealTextOn(ThemeManager.accent()));
        }
    }

    private static Color idealTextOn(Color background) {
        return ThemeManager.color("App.onAccent", ThemeManager.readableOn(background));
    }

    /**
     * Button that paints its own rounded surface.
     * <p>
     * Nimbus draws buttons through Painters that ignore {@code setBackground},
     * so the accent fill and rounded corners are painted here instead of being
     * configured on the look and feel.
     */
    public static final class RoundedButton extends JButton {

        private static final int ARC = 12;

        private final boolean filled;
        private boolean borderless;

        RoundedButton(String text, Icon icon, boolean filled, boolean compact) {
            super(text, icon);
            this.filled = filled;

            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setIconTextGap(8);
            setBorder(compact
                    ? new EmptyBorder(5, 8, 5, 8)
                    : new EmptyBorder(7, 14, 7, 14));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            refreshColors();
            ThemeManager.styleOnThemeChange(this, RoundedButton::refreshColors);
        }

        void setBorderless(boolean borderless) {
            this.borderless = borderless;
            repaint();
        }

        void refreshColors() {
            setForeground(filled
                    ? ThemeManager.color("App.onAccent", Color.WHITE)
                    : ThemeManager.color("App.foreground", Color.DARK_GRAY));
            setFont(ThemeManager.appFont().deriveFont(filled ? Font.BOLD : Font.PLAIN, 12.5f));
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                ButtonModel model = getModel();
                RoundRectangle2D shape = new RoundRectangle2D.Double(
                        0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, ARC, ARC);

                if (filled) {
                    Color base = ThemeManager.accent();
                    if (!model.isEnabled()) {
                        base = ThemeManager.blend(base, ThemeManager.card(), 0.45f);
                    } else if (model.isPressed()) {
                        base = base.darker();
                    } else if (model.isRollover()) {
                        base = ThemeManager.blend(base, Color.WHITE, 0.85f);
                    }
                    g.setColor(base);
                    g.fill(shape);
                } else if (!borderless) {
                    Color surface = ThemeManager.card();
                    if (model.isPressed()) {
                        surface = ThemeManager.blend(ThemeManager.accent(), surface, 0.18f);
                    } else if (model.isRollover()) {
                        surface = ThemeManager.blend(ThemeManager.accent(), surface, 0.09f);
                    }
                    g.setColor(surface);
                    g.fill(shape);
                    g.setColor(ThemeManager.border());
                    g.setStroke(new BasicStroke(1f));
                    g.draw(shape);
                } else if (model.isRollover() || model.isPressed()) {
                    g.setColor(ThemeManager.color("App.accentSoft", ThemeManager.card()));
                    g.fill(shape);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }

    /**
     * Text field that draws a placeholder while empty, plus an optional icon
     * inside its left edge.
     */
    public static final class HintTextField extends JTextField {

        private final String placeholder;
        private final Icon leadingIcon;

        HintTextField(String placeholder, Icon leadingIcon) {
            this.placeholder = placeholder;
            this.leadingIcon = leadingIcon;
            // The hint shows only while unfocused, so repaint on focus changes
            addFocusListener(new java.awt.event.FocusAdapter() {
                @Override
                public void focusGained(java.awt.event.FocusEvent e) {
                    repaint();
                }

                @Override
                public void focusLost(java.awt.event.FocusEvent e) {
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            // Painted here rather than in the border: the text is drawn during
            // paintComponent, and the border runs afterwards, so a filling
            // border would cover it.
            Graphics2D fillG = (Graphics2D) graphics.create();
            try {
                fillG.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                        RenderingHints.VALUE_ANTIALIAS_ON);
                fillG.setColor(ThemeManager.card());
                fillG.fill(new RoundRectangle2D.Double(
                        0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 10, 10));
            } finally {
                fillG.dispose();
            }

            super.paintComponent(graphics);

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                if (leadingIcon != null) {
                    int y = (getHeight() - leadingIcon.getIconHeight()) / 2;
                    leadingIcon.paintIcon(this, g, 10, y);
                }

                if (placeholder != null && !placeholder.isEmpty()
                        && getText().isEmpty() && !isFocusOwner()) {
                    g.setColor(ThemeManager.subtle());
                    g.setFont(getFont());
                    FontMetrics metrics = g.getFontMetrics();
                    Insets insets = getInsets();
                    int baseline = (getHeight() - metrics.getHeight()) / 2 + metrics.getAscent();
                    g.drawString(placeholder, insets.left, baseline);
                }
            } finally {
                g.dispose();
            }
        }
    }

    // ----------------------------------------------------------- date picker

    /** The project's own date field, styled to match the other inputs. */
    public static DatePicker datePicker() {
        DatePicker picker = new DatePicker();
        picker.setDateFormat(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        DATE_PICKERS.add(new WeakReference<>(picker));
        stylePicker(picker);
        return picker;
    }

    private static void stylePicker(DatePicker picker) {
        Color foreground = ThemeManager.color("App.foreground", Color.DARK_GRAY);

        // The picker is a container, so the border can supply the rounded fill
        picker.setOpaque(false);
        picker.setBorder(new RoundedFieldBorder());

        JTextField editor = picker.getEditor();
        editor.setOpaque(false);
        editor.setBorder(new EmptyBorder(0, 2, 0, 2));
        editor.setForeground(foreground);
        editor.setCaretColor(foreground);

        JButton toggle = picker.getToggleButton();
        toggle.setText("");
        toggle.setIcon(AppIcons.calendar(15, "App.subtleForeground"));
        toggle.setToolTipText("Pick a date");
    }

    // ---------------------------------------------------------------- tables

    /** Applies the shared table look: pill statuses, striped rows, soft grid. */
    public static void styleTable(JTable table) {
        table.setRowHeight(28);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new HeaderRenderer());

        DefaultTableCellRenderer padded = new PaddedCellRenderer();
        table.setDefaultRenderer(Object.class, padded);
    }

    /** Renders a status column as a coloured pill. */
    public static TableCellRenderer statusRenderer() {
        return new StatusRenderer();
    }

    /**
     * Status pill whose colour is chosen from keywords rather than an exact
     * list, so it suits change requests, deployments and run statuses alike.
     */
    public static TableCellRenderer badgeRenderer() {
        return new BadgeRenderer();
    }

    private static final class BadgeRenderer extends DefaultTableCellRenderer {
        private String text = "";

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            text = value == null ? "" : value.toString();
            setText("");
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (text.isEmpty()) return;

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                g.setFont(getFont().deriveFont(Font.BOLD, 11f));
                FontMetrics metrics = g.getFontMetrics();

                int pillWidth = metrics.stringWidth(text) + 20;
                int pillHeight = Math.min(getHeight() - 8, 20);
                int x = 10;
                int y = (getHeight() - pillHeight) / 2;

                g.setColor(background(text));
                g.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, pillHeight, pillHeight));
                g.setColor(foreground(text));
                int baseline = y + (pillHeight - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(text, x + 10, baseline);
            } finally {
                g.dispose();
            }
        }

        private String bucket(String status) {
            String key = status.toLowerCase();
            if (key.contains("fail") || key.contains("reject") || key.contains("cancel")
                    || key.contains("withdraw") || key.contains("rolled") || key.contains("error")) {
                return "error";
            }
            if (key.contains("deploy") || key.contains("complete") || key.contains("done")
                    || key.contains("success") || key.contains("closed complete")) {
                return "done";
            }
            if (key.contains("progress") || key.contains("test") || key.contains("uat")
                    || key.contains("analysis") || key.contains("approv") || key.contains("schedul")
                    || key.contains("hold") || key.contains("run")) {
                return "progress";
            }
            return "todo";
        }

        private Color background(String status) {
            switch (bucket(status)) {
                case "error":
                    return ThemeManager.color("App.badgeErrorBg", Color.PINK);
                case "done":
                    return ThemeManager.color("App.badgeDoneBg", Color.LIGHT_GRAY);
                case "progress":
                    return ThemeManager.color("App.badgeProgressBg", Color.LIGHT_GRAY);
                default:
                    return ThemeManager.color("App.badgeTodoBg", Color.LIGHT_GRAY);
            }
        }

        private Color foreground(String status) {
            switch (bucket(status)) {
                case "error":
                    return ThemeManager.color("App.badgeErrorFg", Color.RED);
                case "done":
                    return ThemeManager.color("App.badgeDoneFg", Color.DARK_GRAY);
                case "progress":
                    return ThemeManager.color("App.badgeProgressFg", Color.DARK_GRAY);
                default:
                    return ThemeManager.color("App.badgeTodoFg", Color.DARK_GRAY);
            }
        }
    }

    // -------------------------------------------------------------- refresh

    private static void refreshRegisteredComponents() {
        Iterator<WeakReference<DatePicker>> pickers = DATE_PICKERS.iterator();
        while (pickers.hasNext()) {
            DatePicker picker = pickers.next().get();
            if (picker == null) {
                pickers.remove();
                continue;
            }
            stylePicker(picker);
            picker.revalidate();
            picker.repaint();
        }

        Iterator<WeakReference<JButton>> buttons = PRIMARY_BUTTONS.iterator();
        while (buttons.hasNext()) {
            JButton button = buttons.next().get();
            if (button == null) {
                buttons.remove();
                continue;
            }
            applyPrimaryColors(button);
            button.repaint();
        }
    }

    // --------------------------------------------------------------- borders

    /**
     * Paints a rounded card surface. Borders are painted before children, so
     * filling here keeps the rounded corners visible behind the content.
     */
    public static final class CardBorder extends AbstractBorder {
        private final int padding;
        private final int arc;

        public CardBorder(int padding) {
            this(padding, 14);
        }

        public CardBorder(int padding, int arc) {
            this.padding = padding;
            this.arc = arc;
        }

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D shape = new RoundRectangle2D.Double(
                        x + 0.5, y + 0.5, width - 1.0, height - 1.0, arc, arc);
                g.setColor(ThemeManager.card());
                g.fill(shape);
                g.setColor(ThemeManager.border());
                g.setStroke(new BasicStroke(1f));
                g.draw(shape);
            } finally {
                g.dispose();
            }
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(padding, padding, padding, padding);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(padding, padding, padding, padding);
            return insets;
        }
    }

    /**
     * Rounded input surface. Borders paint before children, so filling here
     * gives text fields and scroll panes rounded corners without needing the
     * look and feel to support them.
     */
    public static final class RoundedFieldBorder extends AbstractBorder {

        private static final int ARC = 10;

        private final int top;
        private final int left;
        private final int bottom;
        private final int right;
        /**
         * Containers paint children after the border, so the border can supply
         * the rounded fill. Leaf text components draw their text during
         * paintComponent, before the border runs, so they fill themselves.
         */
        private final boolean fill;
        /** Draws the outline in the error colour, e.g. for a clashing value. */
        private boolean error;

        public RoundedFieldBorder() {
            this(4, 8, 4, 4, true);
        }

        public RoundedFieldBorder(int top, int left, int bottom, int right) {
            this(top, left, bottom, right, true);
        }

        public RoundedFieldBorder(int top, int left, int bottom, int right, boolean fill) {
            this.top = top;
            this.left = left;
            this.bottom = bottom;
            this.right = right;
            this.fill = fill;
        }

        @Override
        public void paintBorder(Component c, Graphics graphics, int x, int y, int width, int height) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D shape = new RoundRectangle2D.Double(
                        x + 0.5, y + 0.5, width - 1.0, height - 1.0, ARC, ARC);
                if (fill) {
                    g.setColor(ThemeManager.card());
                    g.fill(shape);
                }

                boolean focused = c instanceof JComponent && hasFocusWithin((JComponent) c);
                if (error) {
                    g.setColor(ThemeManager.color("App.badgeErrorFg", Color.RED));
                    g.setStroke(new BasicStroke(1.8f));
                } else {
                    g.setColor(!c.isEnabled() ? ThemeManager.subtle()
                            : focused ? ThemeManager.accent() : ThemeManager.border());
                    g.setStroke(new BasicStroke(focused ? 1.4f : 1f));
                }
                g.draw(shape);
            } finally {
                g.dispose();
            }
        }

        private boolean hasFocusWithin(JComponent c) {
            if (c.isFocusOwner()) return true;
            for (Component child : c.getComponents()) {
                if (child.isFocusOwner()) return true;
                if (child instanceof JViewport) {
                    for (Component inner : ((JViewport) child).getComponents()) {
                        if (inner.isFocusOwner()) return true;
                    }
                }
            }
            return false;
        }

        @Override
        public Insets getBorderInsets(Component c) {
            return new Insets(top, left, bottom, right);
        }

        @Override
        public Insets getBorderInsets(Component c, Insets insets) {
            insets.set(top, left, bottom, right);
            return insets;
        }
    }

    /**
     * Outlines a field in red and sets an explanatory tooltip. Has no effect on
     * components that were not built with a {@link RoundedFieldBorder}.
     */
    public static void setFieldError(JComponent field, boolean error, String tooltip) {
        if (field == null) return;
        Border border = field.getBorder();
        if (border instanceof RoundedFieldBorder) {
            ((RoundedFieldBorder) border).error = error;
        }
        field.setToolTipText(tooltip);
        field.repaint();
    }

    /** One-pixel rule in the theme's border colour. */
    public static Border separator(int top, int bottom) {
        return new AbstractBorder() {
            @Override
            public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
                g.setColor(ThemeManager.border());
                g.fillRect(x, y + height - 1, width, 1);
            }

            @Override
            public Insets getBorderInsets(Component c) {
                return new Insets(top, 0, bottom, 0);
            }
        };
    }

    // ------------------------------------------------------------- renderers

    private static final class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer() {
            setHorizontalAlignment(SwingConstants.LEADING);
            setBorder(new EmptyBorder(7, 10, 7, 10));
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setFont(table.getFont().deriveFont(Font.BOLD, 12f));
            setBackground(ThemeManager.color("App.tableHeaderBackground", ThemeManager.card()));
            setForeground(ThemeManager.color("App.tableHeaderForeground", ThemeManager.accent()));
            return this;
        }

        @Override
        protected void paintBorder(Graphics g) {
            super.paintBorder(g);
            g.setColor(ThemeManager.border());
            g.fillRect(0, getHeight() - 1, getWidth(), 1);
            g.fillRect(getWidth() - 1, 4, 1, getHeight() - 8);
        }
    }

    private static final class PaddedCellRenderer extends DefaultTableCellRenderer {
        PaddedCellRenderer() {
            setBorder(new EmptyBorder(0, 10, 0, 10));
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return this;
        }
    }

    private static final class StatusRenderer extends DefaultTableCellRenderer {
        private String text = "";

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean selected,
                                                       boolean focused, int row, int column) {
            super.getTableCellRendererComponent(table, value, selected, focused, row, column);
            text = value == null ? "" : value.toString();
            setText("");
            setBorder(new EmptyBorder(0, 10, 0, 10));
            return this;
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            if (text.isEmpty()) return;

            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                        RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                Font font = getFont().deriveFont(Font.BOLD, 11f);
                g.setFont(font);
                FontMetrics metrics = g.getFontMetrics();

                int textWidth = metrics.stringWidth(text);
                int pillWidth = textWidth + 20;
                int pillHeight = Math.min(getHeight() - 8, 20);
                int x = 10;
                int y = (getHeight() - pillHeight) / 2;

                g.setColor(badgeBackground(text));
                g.fill(new RoundRectangle2D.Double(x, y, pillWidth, pillHeight, pillHeight, pillHeight));
                g.setColor(badgeForeground(text));
                int baseline = y + (pillHeight - metrics.getHeight()) / 2 + metrics.getAscent();
                g.drawString(text, x + 10, baseline);
            } finally {
                g.dispose();
            }
        }

        private Color badgeBackground(String status) {
            if ("Done".equalsIgnoreCase(status)) return ThemeManager.color("App.badgeDoneBg", Color.LIGHT_GRAY);
            if ("In Progress".equalsIgnoreCase(status)) return ThemeManager.color("App.badgeProgressBg", Color.LIGHT_GRAY);
            return ThemeManager.color("App.badgeTodoBg", Color.LIGHT_GRAY);
        }

        private Color badgeForeground(String status) {
            if ("Done".equalsIgnoreCase(status)) return ThemeManager.color("App.badgeDoneFg", Color.DARK_GRAY);
            if ("In Progress".equalsIgnoreCase(status)) return ThemeManager.color("App.badgeProgressFg", Color.DARK_GRAY);
            return ThemeManager.color("App.badgeTodoFg", Color.DARK_GRAY);
        }
    }
}
