package rpa.rpaman;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.geom.RoundRectangle2D;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.TextStyle;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A self-contained date field with a drop-down calendar, written against plain
 * Swing so the project carries no third-party date picker dependency.
 * <p>
 * The editor accepts typed dates in the configured format (ISO
 * {@code yyyy-MM-dd} by default) and the calendar popup offers month and year
 * navigation plus Today and Clear shortcuts. Colours are read from
 * {@link ThemeManager} at paint time, so the control follows theme switches.
 */
public class DatePicker extends JPanel {

    private static final int CELL_WIDTH = 36;
    private static final int CELL_HEIGHT = 28;

    private final JTextField editor = new JTextField();
    private final JButton toggleButton = new JButton();
    private final JPopupMenu popup = new JPopupMenu();
    private final CalendarPanel calendar = new CalendarPanel();
    private final List<Consumer<LocalDate>> listeners = new ArrayList<>();

    private DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DatePicker() {
        super(new BorderLayout(4, 0));
        setOpaque(false);

        editor.setBorder(new EmptyBorder(0, 2, 0, 2));
        editor.setOpaque(false);
        editor.setColumns(10);
        editor.addActionListener(e -> commitText());
        editor.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                commitText();
            }
        });

        toggleButton.setFocusable(false);
        toggleButton.setContentAreaFilled(false);
        toggleButton.setBorderPainted(false);
        toggleButton.setOpaque(false);
        toggleButton.setBorder(new EmptyBorder(2, 6, 2, 4));
        toggleButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        toggleButton.addActionListener(e -> togglePopup());

        popup.setLayout(new BorderLayout());
        popup.setBorder(new EmptyBorder(6, 6, 6, 6));
        popup.add(calendar, BorderLayout.CENTER);

        add(editor, BorderLayout.CENTER);
        add(toggleButton, BorderLayout.EAST);
    }

    // ------------------------------------------------------------------- api

    /** Sets the format used for both display and parsing. */
    public void setDateFormat(DateTimeFormatter format) {
        if (format == null) return;
        LocalDate current = getDate();
        formatter = format;
        setDate(current);
    }

    /** The text field, exposed so callers can restyle it. */
    public JTextField getEditor() {
        return editor;
    }

    /** The calendar toggle, exposed so callers can set its icon. */
    public JButton getToggleButton() {
        return toggleButton;
    }

    /** The selected date, or {@code null} when the field is empty or invalid. */
    public LocalDate getDate() {
        return parse(editor.getText());
    }

    /** Sets the field from a date; {@code null} clears it. */
    public void setDate(LocalDate date) {
        editor.setText(date == null ? "" : date.format(formatter));
        editor.setCaretPosition(0);
        markValidity(true);
        fireDateChanged(date);
    }

    /**
     * The selected date as {@code yyyy-MM-dd}, or an empty string when nothing
     * valid is entered. Used when writing to the database.
     */
    public String getDateStringOrEmptyString() {
        LocalDate date = getDate();
        return date == null ? "" : date.toString();
    }

    /** Puts raw text in the field, parsing it if possible. */
    public void setText(String text) {
        editor.setText(text == null ? "" : text.trim());
        editor.setCaretPosition(0);
        markValidity(editor.getText().isEmpty() || getDate() != null);
    }

    public String getText() {
        return editor.getText();
    }

    /** Empties the field. */
    public void clear() {
        editor.setText("");
        markValidity(true);
        fireDateChanged(null);
    }

    /** Notified whenever the selected date changes; may receive {@code null}. */
    public void addDateChangeListener(Consumer<LocalDate> listener) {
        if (listener != null) listeners.add(listener);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        editor.setEnabled(enabled);
        toggleButton.setEnabled(enabled);
    }

    // ---------------------------------------------------------------- parsing

    private LocalDate parse(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) return null;
        try {
            return LocalDate.parse(text, formatter);
        } catch (DateTimeParseException ignored) {
            // fall through to ISO, which is what the database stores
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    /** Reformats valid input; flags anything unparseable in the error colour. */
    private void commitText() {
        String text = editor.getText().trim();
        if (text.isEmpty()) {
            markValidity(true);
            fireDateChanged(null);
            return;
        }
        LocalDate date = parse(text);
        if (date == null) {
            markValidity(false);
            return;
        }
        editor.setText(date.format(formatter));
        markValidity(true);
        fireDateChanged(date);
    }

    private void markValidity(boolean valid) {
        editor.setForeground(valid
                ? ThemeManager.color("App.foreground", Color.DARK_GRAY)
                : ThemeManager.color("App.badgeErrorFg", Color.RED));
        editor.setToolTipText(valid ? null
                : "Expected a date like " + LocalDate.now().format(formatter));
    }

    private void fireDateChanged(LocalDate date) {
        for (Consumer<LocalDate> listener : new ArrayList<>(listeners)) {
            try {
                listener.accept(date);
            } catch (RuntimeException ignored) {
                // a listener must not break the control
            }
        }
    }

    // ------------------------------------------------------------------ popup

    private void togglePopup() {
        if (popup.isVisible()) {
            popup.setVisible(false);
            return;
        }
        calendar.showMonthOf(getDate());
        popup.pack();
        popup.show(this, 0, getHeight() + 2);
    }

    private void chooseDate(LocalDate date) {
        popup.setVisible(false);
        setDate(date);
        editor.requestFocusInWindow();
    }

    // --------------------------------------------------------------- calendar

    /** Month grid with navigation, Today and Clear. */
    private final class CalendarPanel extends JPanel {

        private final JLabel monthLabel = new JLabel("", SwingConstants.CENTER);
        private final JPanel grid = new JPanel(new GridLayout(0, 7, 2, 2));
        private YearMonth displayed = YearMonth.now();

        CalendarPanel() {
            super(new BorderLayout(0, 6));
            setOpaque(false);
            setBorder(new EmptyBorder(2, 2, 2, 2));

            // ---- header: year back, month back, label, month forward, year forward
            JPanel header = new JPanel(new BorderLayout(4, 0));
            header.setOpaque(false);

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
            left.setOpaque(false);
            left.add(navButton("«", "Previous year", e -> shift(-12)));
            left.add(navButton("‹", "Previous month", e -> shift(-1)));

            JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
            right.setOpaque(false);
            right.add(navButton("›", "Next month", e -> shift(1)));
            right.add(navButton("»", "Next year", e -> shift(12)));

            monthLabel.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 13f));
            monthLabel.setBorder(new EmptyBorder(2, 4, 2, 4));

            header.add(left, BorderLayout.WEST);
            header.add(monthLabel, BorderLayout.CENTER);
            header.add(right, BorderLayout.EAST);

            grid.setOpaque(false);

            // ---- footer: Today / Clear
            JPanel footer = new JPanel(new BorderLayout());
            footer.setOpaque(false);
            footer.setBorder(new EmptyBorder(4, 0, 0, 0));
            footer.add(linkButton("Today", e -> chooseDate(LocalDate.now())), BorderLayout.WEST);
            footer.add(linkButton("Clear", e -> {
                popup.setVisible(false);
                clear();
            }), BorderLayout.EAST);

            add(header, BorderLayout.NORTH);
            add(grid, BorderLayout.CENTER);
            add(footer, BorderLayout.SOUTH);
        }

        void showMonthOf(LocalDate date) {
            displayed = (date == null) ? YearMonth.now() : YearMonth.from(date);
            rebuild();
        }

        private void shift(int months) {
            displayed = displayed.plusMonths(months);
            rebuild();
        }

        private void rebuild() {
            monthLabel.setForeground(ThemeManager.color("App.foreground", Color.DARK_GRAY));
            monthLabel.setText(displayed.getMonth()
                    .getDisplayName(TextStyle.FULL, Locale.getDefault())
                    + " " + displayed.getYear());

            grid.removeAll();

            DayOfWeek firstDay = WeekFields.of(Locale.getDefault()).getFirstDayOfWeek();
            for (int i = 0; i < 7; i++) {
                DayOfWeek day = firstDay.plus(i);
                JLabel header = new JLabel(
                        day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        SwingConstants.CENTER);
                header.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 11f));
                header.setForeground(ThemeManager.subtle());
                header.setPreferredSize(new Dimension(CELL_WIDTH, 20));
                grid.add(header);
            }

            LocalDate firstOfMonth = displayed.atDay(1);
            // Back up to the first cell of the week containing the 1st
            int offset = (firstOfMonth.getDayOfWeek().getValue() - firstDay.getValue() + 7) % 7;
            LocalDate cursor = firstOfMonth.minusDays(offset);

            LocalDate selected = getDate();
            for (int i = 0; i < 42; i++) {
                grid.add(new DayCell(cursor, displayed, selected));
                cursor = cursor.plusDays(1);
            }

            grid.revalidate();
            grid.repaint();
            revalidate();
        }

        private JButton navButton(String glyph, String tooltip, java.awt.event.ActionListener action) {
            JButton button = new JButton(glyph);
            button.setToolTipText(tooltip);
            button.setFocusable(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setOpaque(false);
            button.setBorder(new EmptyBorder(2, 7, 2, 7));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFont(ThemeManager.appFont().deriveFont(Font.BOLD, 14f));
            ThemeManager.styleOnThemeChange(button,
                    b -> b.setForeground(ThemeManager.accent()));
            button.addActionListener(action);
            return button;
        }

        private JButton linkButton(String text, java.awt.event.ActionListener action) {
            JButton button = new JButton(text);
            button.setFocusable(false);
            button.setContentAreaFilled(false);
            button.setBorderPainted(false);
            button.setOpaque(false);
            button.setBorder(new EmptyBorder(2, 6, 2, 6));
            button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            button.setFont(ThemeManager.appFont().deriveFont(Font.PLAIN, 12f));
            ThemeManager.styleOnThemeChange(button,
                    b -> b.setForeground(ThemeManager.accent()));
            button.addActionListener(action);
            return button;
        }

        @Override
        protected void paintComponent(Graphics g) {
            g.setColor(ThemeManager.card());
            g.fillRect(0, 0, getWidth(), getHeight());
            super.paintComponent(g);
        }
    }

    /** One day in the grid: rounded selection fill, ring for today. */
    private final class DayCell extends JButton {

        private final LocalDate date;
        private final boolean inMonth;
        private final boolean selected;
        private final boolean today;

        DayCell(LocalDate date, YearMonth month, LocalDate selectedDate) {
            super(String.valueOf(date.getDayOfMonth()));
            this.date = date;
            this.inMonth = YearMonth.from(date).equals(month);
            this.selected = date.equals(selectedDate);
            this.today = date.equals(LocalDate.now());

            setFocusable(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setOpaque(false);
            setRolloverEnabled(true);
            setBorder(new EmptyBorder(2, 2, 2, 2));
            setPreferredSize(new Dimension(CELL_WIDTH, CELL_HEIGHT));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setFont(ThemeManager.appFont().deriveFont(
                    selected || today ? Font.BOLD : Font.PLAIN, 12f));
            setForeground(foregroundColor());
            addActionListener((ActionEvent e) -> chooseDate(this.date));
        }

        private Color foregroundColor() {
            if (selected) return ThemeManager.color("App.onAccent", Color.WHITE);
            if (!inMonth) return ThemeManager.blend(ThemeManager.subtle(), ThemeManager.card(), 0.55f);
            if (today) return ThemeManager.accent();
            return ThemeManager.color("App.foreground", Color.DARK_GRAY);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                RoundRectangle2D shape = new RoundRectangle2D.Double(
                        0.5, 0.5, getWidth() - 1.0, getHeight() - 1.0, 8, 8);

                if (selected) {
                    g.setColor(ThemeManager.accent());
                    g.fill(shape);
                } else if (getModel().isRollover()) {
                    g.setColor(ThemeManager.color("App.accentSoft", ThemeManager.card()));
                    g.fill(shape);
                }
                if (today && !selected) {
                    g.setColor(ThemeManager.accent());
                    g.setStroke(new BasicStroke(1f));
                    g.draw(shape);
                }
            } finally {
                g.dispose();
            }
            super.paintComponent(graphics);
        }
    }
}
