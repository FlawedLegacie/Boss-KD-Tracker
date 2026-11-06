package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReversibleHistoryTest
{
    @Test
    void correctionLinksBackToOriginalEvent()
    {
        TrackerEvent original = TrackerEvent.createAt(
            "encounter-1",
            "vorkath",
            TrackerEventType.KILL,
            TrackerEventSource.AUTOMATIC,
            1,
            "confirmed",
            Instant.parse("2026-11-06T06:00:00Z"));

        TrackerEvent correction = TrackerEvent.createUndo(
            original,
            "user correction");

        assertEquals(original.getId(), correction.getReversesEventId());
        assertEquals(-1, correction.getAmount());
        assertEquals(TrackerEventSource.CORRECTED, correction.getSource());
        assertEquals(original.getEncounterId(), correction.getEncounterId());
    }

    @Test
    void voidedEncounterIsExcludedFromStatistics()
    {
        Instant start = Instant.parse("2026-11-06T06:00:00Z");
        EncounterAttempt kept = EncounterAttempt.start(
            "vorkath",
            "Vorkath",
            8061,
            start);
        kept.complete(
            EncounterOutcome.KILL,
            start.plusSeconds(60),
            "TEST",
            "");

        EncounterAttempt voided = EncounterAttempt.start(
            "vorkath",
            "Vorkath",
            8061,
            start.plusSeconds(120));
        voided.complete(
            EncounterOutcome.DEATH,
            start.plusSeconds(180),
            "TEST",
            "");
        voided.voidAttempt("incorrect attribution");

        List<EncounterAttempt> attempts = new ArrayList<>();
        attempts.add(kept);
        attempts.add(voided);

        BossStatistics statistics =
            BossStatistics.calculate("vorkath", attempts);

        assertTrue(voided.isVoided());
        assertEquals(1, statistics.getAttempts());
        assertEquals(1, statistics.getKills());
        assertEquals(0, statistics.getDeaths());
    }
}
