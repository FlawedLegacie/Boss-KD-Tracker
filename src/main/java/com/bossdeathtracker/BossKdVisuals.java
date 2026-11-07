package com.bossdeathtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/** Visible release identity is packaged with the code, never inferred from stored data. */
public final class BossKdVisuals
{
    private static final Properties BUILD = loadBuild();

    private BossKdVisuals()
    {
    }

    private static Properties loadBuild()
    {
        Properties values = new Properties();
        try (InputStream stream = BossKdVisuals.class.getResourceAsStream("/bosskd/build.properties"))
        {
            if (stream != null)
            {
                values.load(stream);
            }
        }
        catch (IOException ignored)
        {
            // Never claim a released version if the embedded identity cannot be read.
        }
        return values;
    }

    public static String version()
    {
        return BUILD.getProperty("version", "development");
    }

    public static String badgeText()
    {
        return "v" + version() + "  /  ICONS " + BUILD.getProperty("icons", "unknown");
    }

    public static JPanel createHeader()
    {
        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBackground(new Color(25, 33, 45));
        header.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 2, 0, new Color(214, 166, 83)),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        header.setPreferredSize(new Dimension(0, 58));
        JLabel emblem = new JLabel(BossKdIcons.icon(BossKdIcons.Symbol.EMBLEM, 30));
        emblem.setToolTipText("Boss KD Tracker: crowned-skull visual edition");
        header.add(emblem, BorderLayout.WEST);

        JPanel identity = new JPanel();
        identity.setOpaque(false);
        identity.setLayout(new BoxLayout(identity, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Boss KD Tracker");
        title.setForeground(new Color(242, 235, 219));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 12f));
        JLabel badge = new JLabel(badgeText());
        badge.setForeground(new Color(114, 221, 201));
        badge.setFont(badge.getFont().deriveFont(Font.PLAIN, 10f));
        badge.setToolTipText("Identity of this running build; independent of your character statistics");
        identity.add(title);
        identity.add(Box.createVerticalStrut(3));
        identity.add(badge);
        header.add(identity, BorderLayout.CENTER);
        header.getAccessibleContext().setAccessibleName("Boss KD Tracker " + badgeText());
        return header;
    }
}
