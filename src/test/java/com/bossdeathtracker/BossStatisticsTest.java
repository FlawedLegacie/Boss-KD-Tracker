package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BossStatisticsTest
{
    @Test
    void calculatesWinRateStreaksAndKillTimes()
    {
        Instant start = Instant.parse("2026-11-06T06:00:00Z");
        List<EncounterAttempt> attempts = new ArrayList<>();
        attempts.add(completed(
            "vorkath", start, 60, EncounterOutcome.KILL));
        attempts.add(completed(
            "vorkath", start.plusSeconds(120), 50, EncounterOutcome.KILL));
        attempts.add(completed(
            "vorkath", start.plusSeconds(240), 30, EncounterOutcome.DEATH));
        attempts.add(completed(
            "vorkath", start.plusSeconds(360), 40, EncounterOutcome.KILL));
        attempts.add(completed(
            "zulrah", start.plusSeconds(500), 25, EncounterOutcome.KILL));

        BossStatistics stats = BossStatistics.calculate("vorkath", attempts);

        assertEquals(4, stats.getAttempts());
        assertEquals(3, stats.getKills());
        assertEquals(1, stats.getDeaths());
        assertEquals(0.75, stats.getWinRate(), 0.0001);
        assertEquals(1, stats.getCurrentStreak());
        assertEquals(2, stats.getBestStreak());
        assertEquals(40_000L, stats.getPersonalBestMillis());
        assertEquals(50_000L, stats.getAverageKillMillis());
    }

    @Test
    void doubleKoCountsAsKillAndDeathButBreaksCleanStreak()
    {
        Instant start = Instant.parse("2026-11-06T06:00:00Z");
        List<EncounterAttempt> attempts = new ArrayList<>();
        attempts.add(completed(
            "whisperer", start, 90, EncounterOutcome.KILL));
        attempts.add(completed(
            "whisperer", start.plusSeconds(120), 80, EncounterOutcome.DOUBLE_KO));

        BossStatistics stats =
            BossStatistics.calculate("whisperer", attempts);

        assertEquals(2, stats.getAttempts());
        assertEquals(2, stats.getKills());
        assertEquals(1, stats.getDeaths());
        assertEquals(1, stats.getDoubleKos());
        assertEquals(0, stats.getCurrentStreak());
        assertEquals(1, stats.getBestStreak());
    }

    private static EncounterAttempt completed(
        String bossId,
        Instant startedAt,
        long durationSeconds,
        EncounterOutcome outcome)
    {
        EncounterAttempt attempt = EncounterAttempt.start(
            bossId,
            bossId,
            1,
            startedAt);
        attempt.complete(
            outcome,
            startedAt.plusSeconds(durationSeconds),
            "TEST",
            "");
        return attempt;
    }
}
