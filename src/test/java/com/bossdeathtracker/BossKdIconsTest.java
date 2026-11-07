package com.bossdeathtracker;

import java.awt.Component;
import java.awt.Container;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.MultiResolutionImage;
import java.util.HashSet;
import java.util.Set;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossKdIconsTest
{
    @Test
    void everySymbolRendersAtToolbarAndPanelSizes()
    {
        for (BossKdIcons.Symbol symbol : BossKdIcons.Symbol.values())
        {
            for (int size : new int[] {14, 16, 20, 30, 32})
            {
                BufferedImage image = BossKdIcons.image(symbol, size);
                assertEquals(size, image.getWidth());
                assertEquals(size, image.getHeight());
                assertEquals(0, image.getRGB(0, 0) >>> 24, symbol.name());
                assertEquals(0, image.getRGB(size - 1, size - 1) >>> 24, symbol.name());
                int visible = 0;
                for (int y = 0; y < size; y++)
                {
                    for (int x = 0; x < size; x++)
                    {
                        if ((image.getRGB(x, y) >>> 24) > 64)
                        {
                            visible++;
                        }
                    }
                }
                assertTrue(visible > size * size / 12, symbol.name() + " must not be blank");
            }
        }
    }

    @Test
    void allSymbolsHaveDistinctPixels()
    {
        Set<Integer> fingerprints = new HashSet<>();
        for (BossKdIcons.Symbol symbol : BossKdIcons.Symbol.values())
        {
            BufferedImage image = BossKdIcons.image(symbol, 32);
            int hash = java.util.Arrays.hashCode(image.getRGB(0, 0, 32, 32, null, 0, 32));
            assertTrue(fingerprints.add(hash), "Duplicate symbol: " + symbol);
        }
    }

    @Test
    void iconsAreCachedAndCarryHighDpiVariants()
    {
        ImageIcon icon = BossKdIcons.icon(BossKdIcons.Symbol.SWORDS, 16);
        assertSame(icon, BossKdIcons.icon(BossKdIcons.Symbol.SWORDS, 16));
        assertEquals(16, icon.getIconWidth());
        assertEquals("Kills", icon.getDescription());
        assertTrue(icon.getImage() instanceof MultiResolutionImage);
        MultiResolutionImage multi = (MultiResolutionImage) icon.getImage();
        assertEquals(3, multi.getResolutionVariants().size());
        Image doubled = multi.getResolutionVariant(32, 32);
        assertEquals(32, doubled.getWidth(null));
        assertEquals(32, doubled.getHeight(null));
        assertEquals(48, multi.getResolutionVariant(48, 48).getWidth(null));
    }

    @Test
    void invalidSizesAndNullSymbolsFailClearly()
    {
        assertThrows(IllegalArgumentException.class, () -> BossKdIcons.icon(null, 16));
        assertThrows(IllegalArgumentException.class, () -> BossKdIcons.image(BossKdIcons.Symbol.SKULL, 0));
        assertThrows(IllegalArgumentException.class, () -> BossKdIcons.icon(BossKdIcons.Symbol.SKULL, 129));
    }

    @Test
    void packagedVersionAndBadgeAreVisibleAtNarrowSidebarWidths() throws Exception
    {
        assertEquals("0.11.1", BossKdVisuals.version());
        assertEquals("v0.11.1  /  ICONS 01", BossKdVisuals.badgeText());
        SwingUtilities.invokeAndWait(() ->
        {
            for (int width : new int[] {210, 225, 250})
            {
                JPanel header = BossKdVisuals.createHeader();
                header.setSize(width, 58);
                layout(header);
                JLabel badge = findLabel(header, BossKdVisuals.badgeText());
                assertNotNull(badge);
                assertTrue(badge.getWidth() >= badge.getPreferredSize().width);
                JLabel title = findLabel(header, "Boss KD Tracker");
                assertNotNull(title);
                assertTrue(title.getWidth() >= title.getPreferredSize().width);
            }
        });
    }

    private static void layout(Container parent)
    {
        parent.doLayout();
        for (Component child : parent.getComponents())
        {
            if (child instanceof Container)
            {
                layout((Container) child);
            }
        }
    }

    private static JLabel findLabel(Container parent, String text)
    {
        for (Component child : parent.getComponents())
        {
            if (child instanceof JLabel && text.equals(((JLabel) child).getText()))
            {
                return (JLabel) child;
            }
            if (child instanceof Container)
            {
                JLabel found = findLabel((Container) child, text);
                if (found != null)
                {
                    return found;
                }
            }
        }
        return null;
    }
}
