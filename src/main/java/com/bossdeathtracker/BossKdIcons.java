package com.bossdeathtracker;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.ImageIcon;

/** Original, cached vector-style UI symbols. No downloaded assets or font glyphs. */
public final class BossKdIcons
{
    public enum Symbol
    {
        EMBLEM("Boss KD Tracker"), SWORDS("Kills"), SKULL("Deaths"),
        TROPHY("Records"), CHART("K/D statistics"), SEARCH("Search"),
        DETAILS("Details"), CHECK("Verified"), SYNC("Synchronize"),
        PLUS("Add"), BOOK("Boss catalog"), TIMER("Kill time"),
        TARGET("Attempts"), FLAME("Kill streak"), HISTORY("History"),
        DATA("Profile data"), FAVORITE("Favorite"), FAVORITE_OFF("Not a favorite"),
        EXPORT("Export profile"), IMPORT("Import profile"), UNDO("Undo");

        private final String description;

        Symbol(String description)
        {
            this.description = description;
        }
    }

    private static final Color INK = new Color(15, 20, 29);
    private static final Color GOLD = new Color(243, 193, 98);
    private static final Color PALE_GOLD = new Color(255, 232, 173);
    private static final Color STEEL = new Color(229, 242, 250);
    private static final Color BLUE = new Color(92, 169, 231);
    private static final Color TEAL = new Color(94, 213, 196);
    private static final Color CORAL = new Color(236, 113, 104);
    private static final Map<String, ImageIcon> CACHE = new ConcurrentHashMap<>();

    private BossKdIcons()
    {
    }

    /** Logical dimensions remain unchanged at 100%, 150%, and 200% UI scaling. */
    public static ImageIcon icon(Symbol symbol, int size)
    {
        validate(symbol, size);
        return CACHE.computeIfAbsent(symbol.name() + ":" + size, key ->
            new ImageIcon(new BaseMultiResolutionImage(
                render(symbol, size), render(symbol, size * 2), render(symbol, size * 3)),
                symbol.description));
    }

    /** NavigationButton requires a BufferedImage rather than a Swing Icon. */
    public static BufferedImage image(Symbol symbol, int size)
    {
        validate(symbol, size);
        return render(symbol, size);
    }

    private static void validate(Symbol symbol, int size)
    {
        if (symbol == null || size < 8 || size > 128)
        {
            throw new IllegalArgumentException("A symbol and size from 8 to 128 are required");
        }
    }

    private static BufferedImage render(Symbol symbol, int pixels)
    {
        BufferedImage image = new BufferedImage(pixels, pixels, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(pixels / 32.0, pixels / 32.0);
            g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            switch (symbol)
            {
                case EMBLEM: emblem(g); break;
                case SWORDS: swords(g); break;
                case SKULL: skull(g); break;
                case TROPHY: trophy(g); break;
                case CHART: chart(g); break;
                case SEARCH: search(g); break;
                case DETAILS: details(g); break;
                case CHECK: shield(g); break;
                case SYNC: sync(g); break;
                case PLUS: plus(g); break;
                case BOOK: book(g); break;
                case TIMER: timer(g); break;
                case TARGET: target(g); break;
                case FLAME: flame(g); break;
                case HISTORY: history(g); break;
                case DATA: data(g); break;
                case FAVORITE: star(g, true); break;
                case FAVORITE_OFF: star(g, false); break;
                case EXPORT: transfer(g, false); break;
                case IMPORT: transfer(g, true); break;
                case UNDO: undo(g); break;
                default: throw new IllegalArgumentException("Unhandled symbol: " + symbol);
            }
        }
        finally
        {
            g.dispose();
        }
        return image;
    }

    private static Path2D polygon(double... points)
    {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2)
        {
            path.lineTo(points[i], points[i + 1]);
        }
        path.closePath();
        return path;
    }

    private static void fill(Graphics2D g, Shape shape, Color color)
    {
        g.setColor(color);
        g.fill(shape);
    }

    private static void shaded(Graphics2D g, Shape shape, Color top, Color bottom)
    {
        g.setPaint(new GradientPaint(5, 3, top, 24, 30, bottom));
        g.fill(shape);
        g.setColor(INK);
        g.draw(shape);
    }

    private static void line(Graphics2D g, Color color, float width, double... points)
    {
        Path2D path = new Path2D.Double();
        path.moveTo(points[0], points[1]);
        for (int i = 2; i < points.length; i += 2)
        {
            path.lineTo(points[i], points[i + 1]);
        }
        g.setColor(color);
        g.setStroke(new BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path);
        g.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private static void emblem(Graphics2D g)
    {
        Shape crest = polygon(16, 1.8, 29, 7, 27, 22, 22, 27, 16, 30.2, 10, 27, 5, 22, 3, 7);
        shaded(g, crest, new Color(54, 97, 133), new Color(24, 39, 60));
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.7f));
        g.draw(crest);
        Graphics2D small = (Graphics2D) g.create();
        small.translate(5, 6);
        small.scale(0.69, 0.69);
        skull(small);
        small.dispose();
        shaded(g, polygon(8, 6, 12, 8, 16, 3, 20, 8, 24, 6, 22, 12, 10, 12),
            PALE_GOLD, GOLD);
        fill(g, new Ellipse2D.Double(14.5, 7, 3, 3), CORAL);
    }

    private static void swords(Graphics2D g)
    {
        Graphics2D left = (Graphics2D) g.create();
        left.rotate(-Math.PI / 4, 16, 16);
        sword(left);
        left.dispose();
        Graphics2D right = (Graphics2D) g.create();
        right.rotate(Math.PI / 4, 16, 16);
        sword(right);
        right.dispose();
    }

    private static void sword(Graphics2D g)
    {
        shaded(g, polygon(16, 1.8, 19, 6, 18, 21, 14, 21, 13, 6), STEEL, BLUE);
        line(g, Color.WHITE, 1.0f, 16, 5, 16, 19);
        shaded(g, polygon(10, 20, 22, 20, 22, 23, 18, 22, 18, 28, 14, 28, 14, 22, 10, 23),
            PALE_GOLD, new Color(190, 126, 49));
        fill(g, new RoundRectangle2D.Double(14.4, 23, 3.2, 4, 1, 1), new Color(111, 59, 53));
    }

    private static void skull(Graphics2D g)
    {
        Path2D p = new Path2D.Double();
        p.moveTo(7, 20); p.curveTo(1, 11, 7, 3, 16, 3);
        p.curveTo(25, 3, 31, 11, 25, 20);
        p.lineTo(23, 21); p.lineTo(23, 28); p.lineTo(9, 28); p.lineTo(9, 21); p.closePath();
        shaded(g, p, new Color(255, 243, 222), new Color(203, 171, 153));
        fill(g, polygon(7, 12, 13, 13, 13, 18, 8, 18), INK);
        fill(g, polygon(19, 13, 25, 12, 24, 18, 19, 18), INK);
        fill(g, polygon(16, 18, 19, 22, 13, 22), CORAL);
        line(g, INK, 1.5f, 12, 24, 12, 28);
        line(g, INK, 1.5f, 16, 24, 16, 28);
        line(g, INK, 1.5f, 20, 24, 20, 28);
    }

    private static void trophy(Graphics2D g)
    {
        line(g, GOLD, 3.0f, 9, 7, 4, 7, 4, 13, 9, 17);
        line(g, GOLD, 3.0f, 23, 7, 28, 7, 28, 13, 23, 17);
        Path2D cup = new Path2D.Double();
        cup.moveTo(8, 4); cup.lineTo(24, 4); cup.lineTo(23, 14);
        cup.quadTo(22, 19, 18, 20); cup.lineTo(18, 25);
        cup.lineTo(24, 26); cup.lineTo(24, 29); cup.lineTo(8, 29);
        cup.lineTo(8, 26); cup.lineTo(14, 25); cup.lineTo(14, 20);
        cup.quadTo(10, 19, 9, 14); cup.closePath();
        shaded(g, cup, PALE_GOLD, new Color(206, 142, 49));
        fill(g, polygon(16, 8, 18, 11, 16, 14, 14, 11), new Color(55, 113, 145));
    }

    private static void chart(Graphics2D g)
    {
        shaded(g, new RoundRectangle2D.Double(4, 20, 5, 8, 1, 1), TEAL, new Color(35, 130, 136));
        shaded(g, new RoundRectangle2D.Double(13, 15, 5, 13, 1, 1), STEEL, BLUE);
        shaded(g, new RoundRectangle2D.Double(22, 10, 5, 18, 1, 1), PALE_GOLD, GOLD);
        line(g, TEAL, 2.4f, 5, 14, 13, 8, 19, 10, 27, 3);
    }

    private static void search(Graphics2D g)
    {
        shaded(g, new Ellipse2D.Double(4, 3, 18, 18), STEEL, BLUE);
        fill(g, new Ellipse2D.Double(7, 6, 12, 12), new Color(31, 54, 74));
        line(g, GOLD, 4.6f, 20, 20, 28, 28);
        line(g, new Color(177, 223, 239), 1.7f, 10, 8, 8, 11);
    }

    private static void details(Graphics2D g)
    {
        shaded(g, new Ellipse2D.Double(3, 3, 26, 26), BLUE, new Color(36, 80, 126));
        fill(g, new Ellipse2D.Double(14, 7, 4, 4), STEEL);
        line(g, STEEL, 3.2f, 16, 15, 16, 24);
    }

    private static void shield(Graphics2D g)
    {
        shaded(g, polygon(16, 3, 28, 7, 26, 21, 16, 29, 6, 21, 4, 7),
            TEAL, new Color(35, 120, 113));
        line(g, STEEL, 3.0f, 10, 15, 14, 19, 22, 11);
    }

    private static void sync(Graphics2D g)
    {
        g.setColor(TEAL); g.setStroke(new BasicStroke(3.1f));
        g.draw(new Arc2D.Double(5, 5, 22, 22, 35, 145, Arc2D.OPEN));
        g.setColor(BLUE);
        g.draw(new Arc2D.Double(5, 5, 22, 22, 215, 145, Arc2D.OPEN));
        fill(g, polygon(26, 4, 27, 13, 19, 10), TEAL);
        fill(g, polygon(6, 28, 5, 19, 13, 22), BLUE);
    }

    private static void plus(Graphics2D g)
    {
        shaded(g, new Ellipse2D.Double(3, 3, 26, 26), TEAL, new Color(34, 123, 122));
        line(g, STEEL, 3.2f, 9, 16, 23, 16);
        line(g, STEEL, 3.2f, 16, 9, 16, 23);
    }

    private static void book(Graphics2D g)
    {
        shaded(g, polygon(3, 5, 10, 4, 16, 7, 22, 4, 29, 5, 29, 26, 22, 25, 16, 28, 10, 25, 3, 26),
            PALE_GOLD, new Color(198, 161, 109));
        line(g, new Color(133, 93, 56), 1.6f, 16, 8, 16, 26);
        line(g, new Color(133, 93, 56), 1.5f, 6, 10, 11, 10, 13, 11);
        line(g, new Color(133, 93, 56), 1.5f, 6, 15, 11, 15, 13, 16);
        line(g, new Color(133, 93, 56), 1.5f, 20, 11, 25, 10);
        fill(g, polygon(22, 5, 25, 5, 25, 19, 23.5, 17, 22, 19), CORAL);
    }

    private static void timer(Graphics2D g)
    {
        shaded(g, new RoundRectangle2D.Double(12, 2, 8, 4, 1, 1), PALE_GOLD, GOLD);
        shaded(g, new Ellipse2D.Double(5, 7, 23, 23), PALE_GOLD, GOLD);
        fill(g, new Ellipse2D.Double(8, 10, 17, 17), new Color(34, 61, 83));
        line(g, TEAL, 2.0f, 16.5, 12, 16.5, 18.5, 21, 21);
        fill(g, new Ellipse2D.Double(15, 17, 3, 3), STEEL);
        line(g, STEEL, 1.3f, 26, 6, 28, 8);
    }

    private static void target(Graphics2D g)
    {
        shaded(g, new Ellipse2D.Double(3, 3, 26, 26), PALE_GOLD, GOLD);
        fill(g, new Ellipse2D.Double(7, 7, 18, 18), new Color(33, 55, 72));
        fill(g, new Ellipse2D.Double(11, 11, 10, 10), STEEL);
        fill(g, new Ellipse2D.Double(14, 14, 4, 4), CORAL);
    }

    private static void flame(Graphics2D g)
    {
        Path2D p = new Path2D.Double();
        p.moveTo(17, 2); p.curveTo(19, 10, 27, 10, 27, 20);
        p.curveTo(27, 33, 5, 32, 5, 20); p.quadTo(5, 14, 11, 10);
        p.quadTo(10, 18, 15, 13); p.quadTo(19, 8, 17, 2); p.closePath();
        shaded(g, p, new Color(255, 187, 85), CORAL);
        Path2D core = new Path2D.Double();
        core.moveTo(17, 15); core.quadTo(24, 23, 19, 27);
        core.quadTo(10, 31, 11, 22); core.quadTo(15, 24, 17, 15); core.closePath();
        fill(g, core, PALE_GOLD);
    }

    private static void history(Graphics2D g)
    {
        shaded(g, new RoundRectangle2D.Double(7, 3, 21, 25, 3, 3), STEEL, new Color(138, 178, 207));
        fill(g, new RoundRectangle2D.Double(10, 2, 14, 5, 2, 2), GOLD);
        line(g, new Color(52, 86, 117), 1.8f, 13, 11, 23, 11);
        line(g, new Color(52, 86, 117), 1.8f, 13, 16, 23, 16);
        shaded(g, new Ellipse2D.Double(2, 16, 13, 13), PALE_GOLD, GOLD);
        line(g, INK, 1.5f, 8.5, 19, 8.5, 23, 11, 24);
    }

    private static void data(Graphics2D g)
    {
        for (int y = 20; y >= 6; y -= 7)
        {
            shaded(g, new RoundRectangle2D.Double(4, y, 24, 8, 5, 5), BLUE, new Color(40, 84, 134));
            fill(g, new Ellipse2D.Double(4, y - 2, 24, 6), y == 6 ? TEAL : new Color(103, 177, 215));
        }
        fill(g, new Ellipse2D.Double(22, 23, 3, 3), GOLD);
    }

    private static void star(Graphics2D g, boolean selected)
    {
        double[] points = new double[20];
        for (int i = 0; i < 10; i++)
        {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? 13 : 6;
            points[2 * i] = 16 + radius * Math.cos(angle);
            points[2 * i + 1] = 16 + radius * Math.sin(angle);
        }
        Shape star = polygon(points);
        if (selected)
        {
            shaded(g, star, PALE_GOLD, GOLD);
        }
        else
        {
            fill(g, star, new Color(38, 47, 61));
            g.setColor(new Color(144, 164, 185));
            g.setStroke(new BasicStroke(2.1f));
            g.draw(star);
        }
    }

    private static void transfer(Graphics2D g, boolean inward)
    {
        shaded(g, polygon(4, 20, 10, 20, 10, 24, 22, 24, 22, 20, 28, 20, 28, 29, 4, 29),
            STEEL, BLUE);
        Shape arrow = inward
            ? polygon(13, 3, 19, 3, 19, 12, 25, 12, 16, 21, 7, 12, 13, 12)
            : polygon(13, 21, 19, 21, 19, 12, 25, 12, 16, 3, 7, 12, 13, 12);
        shaded(g, arrow, inward ? PALE_GOLD : TEAL, inward ? GOLD : BLUE);
    }

    private static void undo(Graphics2D g)
    {
        g.setColor(TEAL); g.setStroke(new BasicStroke(4.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Arc2D.Double(7, 8, 20, 20, -70, 225, Arc2D.OPEN));
        fill(g, polygon(4, 3, 4, 15, 16, 15), TEAL);
    }
}
