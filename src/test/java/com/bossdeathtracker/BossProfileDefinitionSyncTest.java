package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class BossProfileDefinitionSyncTest
{
    @Test
    void synchronizationPreservesUserDataAndMergesOfficialIds()
    {
        BossProfile boss = BossProfile.createManual(
            "Old Vorkath",
            "Custom",
            "Legacy",
            Arrays.asList(99999),
            "Keep this note");
        boss.setFavorite(true);

        BossDefinition definition = new BossDefinition(
            "vorkath",
            "Vorkath",
            "Bosses",
            "Dragon Slayer II",
            8061,
            8062);

        assertTrue(boss.synchronizeDefinition(definition));
        assertEquals("vorkath", boss.getDefinitionKey());
        assertEquals("Vorkath", boss.getDisplayName());
        assertEquals("Bosses", boss.getCategory());
        assertEquals("Dragon Slayer II", boss.getSubcategory());
        assertFalse(boss.isCustomBoss());
        assertTrue(boss.isFavorite());
        assertEquals("Keep this note", boss.getNotes());
        assertTrue(boss.getNpcIds().contains(99999));
        assertTrue(boss.getNpcIds().contains(8061));
        assertTrue(boss.getNpcIds().contains(8062));
    }

    @Test
    void alreadyCurrentDefinitionDoesNotReportChange()
    {
        BossDefinition definition = new BossDefinition(
            "zulrah",
            "Zulrah",
            "Bosses",
            "Zul-Andra",
            2042,
            2043,
            2044);
        BossProfile boss = BossProfile.createFromDefinition(definition);

        assertFalse(boss.synchronizeDefinition(definition));
    }
}
