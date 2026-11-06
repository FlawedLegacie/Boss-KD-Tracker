package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EncounterResolutionWindowTest
{
    @Test
    void normalBossDeathResolvesToKillAfterDelay()
    {
        EncounterResolutionWindow window = new EncounterResolutionWindow(3);
        window.observeBossDeath(100);

        assertFalse(window.resolve(102, true).isPresent());
        assertEquals(
            EncounterOutcome.KILL,
            window.resolve(103, true).orElseThrow());
    }

    @Test
    void playerDeathCancelsUnconfirmedBossDeath()
    {
        EncounterResolutionWindow window = new EncounterResolutionWindow(3);
        window.observeBossDeath(100);
        window.observePlayerDeath(101);

        assertEquals(
            EncounterOutcome.DEATH,
            window.resolve(103, false).orElseThrow());
    }

    @Test
    void confirmedKillAndPlayerDeathResolveToDoubleKoInEitherOrder()
    {
        EncounterResolutionWindow first = new EncounterResolutionWindow(3);
        first.observeBossDeath(100);
        first.observePlayerDeath(100);
        first.confirmKillCount(101);

        EncounterResolutionWindow second = new EncounterResolutionWindow(3);
        second.observePlayerDeath(100);
        second.confirmKillCount(101);
        second.observeBossDeath(102);

        assertEquals(
            EncounterOutcome.DOUBLE_KO,
            first.resolve(103, false).orElseThrow());
        assertEquals(
            EncounterOutcome.DOUBLE_KO,
            second.resolve(103, false).orElseThrow());
        assertTrue(first.completionSignal().contains("KILLCOUNT_CHANGE"));
    }

    @Test
    void killCountConfirmationCanRecoverMissingBossDeathEvent()
    {
        EncounterResolutionWindow window = new EncounterResolutionWindow(2);
        window.confirmKillCount(50);

        assertEquals(
            EncounterOutcome.KILL,
            window.resolve(52, true).orElseThrow());
    }
}
