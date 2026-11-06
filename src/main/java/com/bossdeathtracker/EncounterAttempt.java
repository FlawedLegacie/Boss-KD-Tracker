package com.bossdeathtracker;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * One observed boss attempt from first confirmed interaction to a terminal
 * outcome. Historical imported kill counts do not create synthetic attempts.
 */
public class EncounterAttempt
{
    private String id;
    private String bossId;
    private String bossName;
    private int npcId = -1;
    private Instant startedAt;
    private Instant endedAt;
    private EncounterOutcome outcome = EncounterOutcome.IN_PROGRESS;
    private String completionSignal = "";
    private String note = "";
    private boolean voided;
    private String voidReason = "";
    private Instant voidedAt;

    public EncounterAttempt()
    {
        // Required for Gson.
    }

    static EncounterAttempt start(
        String bossId,
        String bossName,
        int npcId,
        Instant startedAt)
    {
        EncounterAttempt attempt = new EncounterAttempt();
        attempt.id = UUID.randomUUID().toString();
        attempt.bossId = bossId;
        attempt.bossName = bossName == null ? "" : bossName;
        attempt.npcId = npcId;
        attempt.startedAt = startedAt == null ? Instant.now() : startedAt;
        attempt.outcome = EncounterOutcome.IN_PROGRESS;
        return attempt;
    }

    void complete(
        EncounterOutcome outcome,
        Instant endedAt,
        String completionSignal,
        String note)
    {
        if (isCompleted())
        {
            return;
        }

        EncounterOutcome resolved = outcome == null
            ? EncounterOutcome.ABANDONED
            : outcome;

        if (!resolved.isCompleted())
        {
            throw new IllegalArgumentException(
                "Encounter outcome must be terminal");
        }

        this.outcome = resolved;
        this.endedAt = endedAt == null ? Instant.now() : endedAt;
        this.completionSignal = completionSignal == null
            ? ""
            : completionSignal;
        this.note = note == null ? "" : note;
    }

    void voidAttempt(String reason)
    {
        if (voided)
        {
            return;
        }

        voided = true;
        voidReason = reason == null ? "" : reason;
        voidedAt = Instant.now();
    }

    public String getId()
    {
        return id;
    }

    public String getBossId()
    {
        return bossId;
    }

    public String getBossName()
    {
        return bossName == null ? "" : bossName;
    }

    public int getNpcId()
    {
        return npcId;
    }

    public Instant getStartedAt()
    {
        return startedAt;
    }

    public Instant getEndedAt()
    {
        return endedAt;
    }

    public EncounterOutcome getOutcome()
    {
        return outcome == null ? EncounterOutcome.IN_PROGRESS : outcome;
    }

    public String getCompletionSignal()
    {
        return completionSignal == null ? "" : completionSignal;
    }

    public String getNote()
    {
        return note == null ? "" : note;
    }

    public boolean isVoided()
    {
        return voided;
    }

    public String getVoidReason()
    {
        return voidReason == null ? "" : voidReason;
    }

    public Instant getVoidedAt()
    {
        return voidedAt;
    }

    public boolean isCompleted()
    {
        return getOutcome().isCompleted();
    }

    public long getDurationMillis()
    {
        if (startedAt == null || endedAt == null)
        {
            return 0L;
        }

        return Math.max(
            0L,
            Duration.between(startedAt, endedAt).toMillis());
    }
}
