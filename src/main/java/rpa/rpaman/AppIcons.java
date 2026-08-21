package rpa.rpaman;

import javax.swing.Icon;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;

/**
 * Lightweight, theme-aware vector icons drawn with Java2D.
 * <p>
 * Every icon is authored on a 24x24 grid and scaled at paint time, so it stays
 * crisp on HiDPI displays. Colours are resolved from {@link javax.swing.UIManager}
 * on each paint, which means icons re-tint automatically when the theme changes.
 */
public final class AppIcons {

    /** Draws an icon into a canvas whose coordinate space is 24x24. */
    public interface Draw {
        void paint(Graphics2D g);
    }

    private AppIcons() {
    }

    public static Icon of(int size, String colorKey, Draw draw) {
        return new VectorIcon(size, colorKey, null, draw);
    }

    public static Icon of(int size, Color color, Draw draw) {
        return new VectorIcon(size, null, color, draw);
    }

    // ---------------------------------------------------------- app icon

    /**
     * Multi-resolution robot icon for the application title bar and taskbar.
     * Returns 16, 32, 48 and 64 px images so every context picks a crisp size.
     */
    public static List<Image> appWindowIcons() {
        return Arrays.asList(
                renderAppIcon(16), renderAppIcon(32),
                renderAppIcon(48), renderAppIcon(64));
    }

    private static Image renderAppIcon(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            double s = size / 24.0;
            g.scale(s, s);

            Color accent = ThemeManager.color("App.accent", new Color(0x4A90D9));
            Color face   = ThemeManager.color("App.cardSurface", new Color(0xE8EDF2));

            // Head (filled rounded rect)
            g.setColor(accent);
            g.fill(new RoundRectangle2D.Double(3, 7, 18, 14, 5, 5));

            // Antenna stalk and ball
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(12, 7, 12, 3.5));
            g.fill(new Ellipse2D.Double(10.2, 1.8, 3.6, 3.6));

            // Eyes (white rounded squares)
            g.setColor(face);
            g.fill(new RoundRectangle2D.Double(5.5, 10, 4.6, 4.2, 2, 2));
            g.fill(new RoundRectangle2D.Double(13.9, 10, 4.6, 4.2, 2, 2));

            // Pupils
            g.setColor(accent.darker().darker());
            g.fill(new Ellipse2D.Double(7, 11, 2.2, 2.2));
            g.fill(new Ellipse2D.Double(15.3, 11, 2.2, 2.2));

            // Mouth grille (three horizontal lines)
            g.setColor(face);
            g.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.draw(new Line2D.Double(8.5, 17, 15.5, 17));
            g.draw(new Line2D.Double(8.5, 19, 15.5, 19));

            // Ear bolts
            g.setColor(accent.brighter());
            g.fill(new Ellipse2D.Double(1.2, 12.5, 2.6, 2.6));
            g.fill(new Ellipse2D.Double(20.2, 12.5, 2.6, 2.6));
        } finally {
            g.dispose();
        }
        return img;
    }

    // ------------------------------------------------------------------ icons

    public static Icon folder(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(3, 19);
            p.lineTo(3, 6.6);
            p.curveTo(3, 5.8, 3.7, 5.1, 4.5, 5.1);
            p.lineTo(9.1, 5.1);
            p.lineTo(11.1, 7.6);
            p.lineTo(19.5, 7.6);
            p.curveTo(20.3, 7.6, 21, 8.3, 21, 9.1);
            p.lineTo(21, 19);
            p.curveTo(21, 19.8, 20.3, 20.5, 19.5, 20.5);
            p.lineTo(4.5, 20.5);
            p.curveTo(3.7, 20.5, 3, 19.8, 3, 19);
            p.closePath();
            g.draw(p);
        });
    }

    public static Icon layers(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(12, 2.8);
            p.lineTo(21, 7.4);
            p.lineTo(12, 12);
            p.lineTo(3, 7.4);
            p.closePath();
            g.draw(p);
            line(g, 3, 12, 12, 16.4);
            line(g, 12, 16.4, 21, 12);
            line(g, 3, 16.6, 12, 21);
            line(g, 12, 21, 21, 16.6);
        });
    }

    public static Icon file(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(6, 3.2);
            p.lineTo(14, 3.2);
            p.lineTo(19, 8.2);
            p.lineTo(19, 20.8);
            p.lineTo(6, 20.8);
            p.closePath();
            g.draw(p);
            Path2D fold = new Path2D.Double();
            fold.moveTo(14, 3.2);
            fold.lineTo(14, 8.2);
            fold.lineTo(19, 8.2);
            g.draw(fold);
        });
    }

    public static Icon search(int size, String key) {
        return of(size, key, g -> {
            oval(g, 4, 4, 12, 12);
            line(g, 15.6, 15.6, 20.4, 20.4);
        });
    }

    public static Icon calendar(int size, String key) {
        return of(size, key, g -> {
            round(g, 3.5, 5.4, 17, 15.1, 3);
            line(g, 3.5, 10.4, 20.5, 10.4);
            line(g, 8, 3, 8, 7.2);
            line(g, 16, 3, 16, 7.2);
        });
    }

    public static Icon plus(int size, String key) {
        return of(size, key, g -> {
            line(g, 12, 5, 12, 19);
            line(g, 5, 12, 19, 12);
        });
    }

    public static Icon inbox(int size, String key) {
        return of(size, key, g -> {
            round(g, 3, 4.5, 18, 15, 2.5);
            Path2D tray = new Path2D.Double();
            tray.moveTo(3, 13.5);
            tray.lineTo(7.6, 13.5);
            tray.lineTo(9.1, 16.2);
            tray.lineTo(14.9, 16.2);
            tray.lineTo(16.4, 13.5);
            tray.lineTo(21, 13.5);
            g.draw(tray);
        });
    }

    public static Icon upload(int size, String key) {
        return of(size, key, g -> {
            line(g, 12, 3.4, 12, 14.6);
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(7.6, 7.8);
            arrow.lineTo(12, 3.4);
            arrow.lineTo(16.4, 7.8);
            g.draw(arrow);
            Path2D tray = new Path2D.Double();
            tray.moveTo(4.2, 15.4);
            tray.lineTo(4.2, 20.2);
            tray.lineTo(19.8, 20.2);
            tray.lineTo(19.8, 15.4);
            g.draw(tray);
        });
    }

    public static Icon grid(int size, String key) {
        return of(size, key, g -> {
            round(g, 3.5, 3.5, 7.5, 7.5, 1.6);
            round(g, 13, 3.5, 7.5, 7.5, 1.6);
            round(g, 3.5, 13, 7.5, 7.5, 1.6);
            round(g, 13, 13, 7.5, 7.5, 1.6);
        });
    }

    public static Icon clipboard(int size, String key) {
        return of(size, key, g -> {
            round(g, 4.5, 4.6, 15, 16.4, 2.5);
            round(g, 8.4, 2.4, 7.2, 4.2, 1.4);
        });
    }

    public static Icon refresh(int size, String key) {
        return of(size, key, g -> {
            g.draw(new Arc2D.Double(4.5, 4.5, 15, 15, 75, 250, Arc2D.OPEN));
            Path2D head = new Path2D.Double();
            head.moveTo(10.6, 4.8);
            head.lineTo(14.4, 5.1);
            head.lineTo(14.1, 8.9);
            g.draw(head);
        });
    }

    public static Icon minus(int size, String key) {
        return of(size, key, g -> line(g, 5, 12, 19, 12));
    }

    public static Icon close(int size, String key) {
        return of(size, key, g -> {
            line(g, 6, 6, 18, 18);
            line(g, 18, 6, 6, 18);
        });
    }

    public static Icon check(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(4.6, 12.6);
            p.lineTo(9.5, 17.4);
            p.lineTo(19.4, 6.6);
            g.draw(p);
        });
    }

    public static Icon save(int size, String key) {
        return of(size, key, g -> {
            round(g, 3.5, 3.5, 17, 17, 3);
            g.draw(new Rectangle2D.Double(8, 3.5, 8, 6));
            g.draw(new Rectangle2D.Double(7, 13, 10, 7.5));
        });
    }

    public static Icon trash(int size, String key) {
        return of(size, key, g -> {
            line(g, 3.5, 6.6, 20.5, 6.6);
            Path2D body = new Path2D.Double();
            body.moveTo(5.6, 6.6);
            body.lineTo(6.6, 20.6);
            body.lineTo(17.4, 20.6);
            body.lineTo(18.4, 6.6);
            g.draw(body);
            Path2D lid = new Path2D.Double();
            lid.moveTo(9, 6.6);
            lid.lineTo(9, 3.6);
            lid.lineTo(15, 3.6);
            lid.lineTo(15, 6.6);
            g.draw(lid);
            line(g, 10, 10.2, 10.3, 17);
            line(g, 14, 10.2, 13.7, 17);
        });
    }

    public static Icon database(int size, String key) {
        return of(size, key, g -> {
            oval(g, 3.5, 3, 17, 5);
            g.draw(new Arc2D.Double(3.5, 8, 17, 5, 180, 180, Arc2D.OPEN));
            g.draw(new Arc2D.Double(3.5, 13, 17, 5, 180, 180, Arc2D.OPEN));
            line(g, 3.5, 5.5, 3.5, 15.5);
            line(g, 20.5, 5.5, 20.5, 15.5);
        });
    }

    public static Icon checklist(int size, String key) {
        return of(size, key, g -> {
            line(g, 10, 6, 20, 6);
            line(g, 10, 12, 20, 12);
            line(g, 10, 18, 20, 18);
            Path2D c1 = new Path2D.Double();
            c1.moveTo(3.5, 6);
            c1.lineTo(5.1, 7.6);
            c1.lineTo(7.6, 4.4);
            g.draw(c1);
            Path2D c2 = new Path2D.Double();
            c2.moveTo(3.5, 12);
            c2.lineTo(5.1, 13.6);
            c2.lineTo(7.6, 10.4);
            g.draw(c2);
            dot(g, 5.4, 18, 1.3);
        });
    }

    public static Icon exit(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(10.5, 3.8);
            p.lineTo(5, 3.8);
            p.lineTo(5, 20.2);
            p.lineTo(10.5, 20.2);
            g.draw(p);
            line(g, 10.5, 12, 20.4, 12);
            Path2D arrow = new Path2D.Double();
            arrow.moveTo(16.6, 8);
            arrow.lineTo(20.6, 12);
            arrow.lineTo(16.6, 16);
            g.draw(arrow);
        });
    }

    public static Icon help(int size, String key) {
        return of(size, key, g -> {
            oval(g, 3.4, 3.4, 17.2, 17.2);
            Path2D q = new Path2D.Double();
            q.moveTo(9.1, 9.4);
            q.curveTo(9.1, 7.1, 10.5, 6.2, 12.2, 6.2);
            q.curveTo(14.4, 6.2, 15.6, 7.6, 15.6, 9.3);
            q.curveTo(15.6, 11.4, 12, 11.9, 12, 14.4);
            g.draw(q);
            dot(g, 12, 17.6, 1.15);
        });
    }

    public static Icon globe(int size, String key) {
        return of(size, key, g -> {
            oval(g, 3.4, 3.4, 17.2, 17.2);
            line(g, 3.6, 12, 20.4, 12);
            oval(g, 8, 3.4, 8, 17.2);
        });
    }

    public static Icon palette(int size, String key) {
        return of(size, key, g -> {
            oval(g, 3.4, 3.4, 17.2, 17.2);
            dot(g, 8.4, 9.2, 1.25);
            dot(g, 12.2, 6.9, 1.25);
            dot(g, 16, 9.8, 1.25);
            dot(g, 9.6, 15.4, 1.25);
        });
    }

    public static Icon sun(int size, String key) {
        return of(size, key, g -> {
            oval(g, 8, 8, 8, 8);
            line(g, 12, 1.8, 12, 4.4);
            line(g, 12, 19.6, 12, 22.2);
            line(g, 1.8, 12, 4.4, 12);
            line(g, 19.6, 12, 22.2, 12);
            line(g, 4.9, 4.9, 6.7, 6.7);
            line(g, 17.3, 17.3, 19.1, 19.1);
            line(g, 4.9, 19.1, 6.7, 17.3);
            line(g, 17.3, 6.7, 19.1, 4.9);
        });
    }

    public static Icon moon(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(20.5, 14.2);
            p.curveTo(19.6, 18.2, 16.0, 21.0, 11.9, 21.0);
            p.curveTo(7.0, 21.0, 3.0, 17.0, 3.0, 12.1);
            p.curveTo(3.0, 8.0, 5.8, 4.4, 9.8, 3.5);
            p.curveTo(7.6, 6.5, 7.9, 10.7, 10.6, 13.4);
            p.curveTo(13.3, 16.1, 17.5, 16.4, 20.5, 14.2);
            p.closePath();
            g.draw(p);
        });
    }

    public static Icon droplet(int size, String key) {
        return of(size, key, g -> {
            Path2D p = new Path2D.Double();
            p.moveTo(12, 2.8);
            p.curveTo(12, 2.8, 19, 10.4, 19, 15);
            p.curveTo(19, 18.9, 15.9, 21.4, 12, 21.4);
            p.curveTo(8.1, 21.4, 5, 18.9, 5, 15);
            p.curveTo(5, 10.4, 12, 2.8, 12, 2.8);
            p.closePath();
            g.draw(p);
        });
    }

    public static Icon sliders(int size, String key) {
        return of(size, key, g -> {
            line(g, 3.6, 7, 20.4, 7);
            line(g, 3.6, 12, 20.4, 12);
            line(g, 3.6, 17, 20.4, 17);
            dot(g, 9, 7, 2.2);
            dot(g, 15, 12, 2.2);
            dot(g, 11, 17, 2.2);
        });
    }

    public static Icon user(int size, String key) {
        return of(size, key, g -> {
            oval(g, 8, 3.6, 8, 8);
            Path2D p = new Path2D.Double();
            p.moveTo(4, 20.6);
            p.curveTo(4, 16.5, 7.5, 14, 12, 14);
            p.curveTo(16.5, 14, 20, 16.5, 20, 20.6);
            g.draw(p);
        });
    }

    public static Icon monitor(int size, String key) {
        return of(size, key, g -> {
            round(g, 2.6, 4, 18.8, 13, 2.6);
            line(g, 8, 20.6, 16, 20.6);
            line(g, 12, 17, 12, 20.6);
        });
    }

    public static Icon key(int size, String colorKey) {
        return of(size, colorKey, g -> {
            oval(g, 3.4, 8.2, 8.4, 8.4);
            line(g, 11.6, 12.4, 20.6, 12.4);
            line(g, 17.2, 12.4, 17.2, 16);
            line(g, 20.6, 12.4, 20.6, 15.2);
        });
    }

    public static Icon warning(int size, String key) {
        return of(size, key, g -> {
            Path2D triangle = new Path2D.Double();
            triangle.moveTo(12, 3.4);
            triangle.lineTo(21.6, 20.2);
            triangle.lineTo(2.4, 20.2);
            triangle.closePath();
            g.draw(triangle);
            line(g, 12, 9.6, 12, 14.6);
            dot(g, 12, 17.4, 1.05);
        });
    }

    public static Icon expandAll(int size, String key) {
        return of(size, key, g -> {
            // Two stacked downward chevrons
            Path2D top = new Path2D.Double();
            top.moveTo(5, 5.5);
            top.lineTo(12, 11);
            top.lineTo(19, 5.5);
            g.draw(top);
            Path2D bot = new Path2D.Double();
            bot.moveTo(5, 13);
            bot.lineTo(12, 18.5);
            bot.lineTo(19, 13);
            g.draw(bot);
        });
    }

    public static Icon collapseAll(int size, String key) {
        return of(size, key, g -> {
            // Two stacked rightward chevrons
            Path2D top = new Path2D.Double();
            top.moveTo(5.5, 5);
            top.lineTo(11, 12);
            top.lineTo(5.5, 19);
            g.draw(top);
            Path2D bot = new Path2D.Double();
            bot.moveTo(13, 5);
            bot.lineTo(18.5, 12);
            bot.lineTo(13, 19);
            g.draw(bot);
        });
    }

    public static Icon info(int size, String key) {
        return of(size, key, g -> {
            oval(g, 3.4, 3.4, 17.2, 17.2);
            line(g, 12, 11, 12, 16.6);
            dot(g, 12, 7.6, 1.15);
        });
    }

    // --------------------------------------------------------------- drawing

    private static void line(Graphics2D g, double x1, double y1, double x2, double y2) {
        g.draw(new Line2D.Double(x1, y1, x2, y2));
    }

    private static void oval(Graphics2D g, double x, double y, double w, double h) {
        g.draw(new Ellipse2D.Double(x, y, w, h));
    }

    private static void round(Graphics2D g, double x, double y, double w, double h, double arc) {
        g.draw(new RoundRectangle2D.Double(x, y, w, h, arc * 2, arc * 2));
    }

    private static void dot(Graphics2D g, double cx, double cy, double r) {
        g.fill(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
    }

    // ------------------------------------------------------------------ impl

    private static final class VectorIcon implements Icon {
        private final int size;
        private final String colorKey;
        private final Color fixedColor;
        private final Draw draw;

        VectorIcon(int size, String colorKey, Color fixedColor, Draw draw) {
            this.size = size;
            this.colorKey = colorKey;
            this.fixedColor = fixedColor;
            this.draw = draw;
        }

        @Override
        public void paintIcon(Component c, Graphics graphics, int x, int y) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

                Color color = fixedColor;
                if (color == null && colorKey != null) {
                    color = ThemeManager.color(colorKey, null);
                }
                if (color == null) {
                    color = (c != null) ? c.getForeground() : Color.DARK_GRAY;
                }
                g.setColor(color);

                g.translate(x, y);
                double scale = size / 24.0;
                g.scale(scale, scale);
                g.setStroke(new BasicStroke(1.85f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                draw.paint(g);
            } finally {
                g.dispose();
            }
        }

        @Override
        public int getIconWidth() {
            return size;
        }

        @Override
        public int getIconHeight() {
            return size;
        }
    }
}
