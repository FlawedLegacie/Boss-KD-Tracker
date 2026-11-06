package com.bossdeathtracker;

/**
 * Final state for a tracked boss attempt.
 */
public enum EncounterOutcome
{
    IN_PROGRESS(false, false, false, false),
    KILL(true, false, true, true),
    DEATH(false, true, true, true),
    DOUBLE_KO(true, true, true, true),
    ABANDONED(false, false, true, false),
    DISCONNECTED(false, false, true, false);

    private final boolean countsKill;
    private final boolean countsDeath;
    private final boolean completed;
    private final boolean countsAttempt;

    EncounterOutcome(
        boolean countsKill,
        boolean countsDeath,
        boolean completed,
        boolean countsAttempt)
    {
        this.countsKill = countsKill;
        this.countsDeath = countsDeath;
        this.completed = completed;
        this.countsAttempt = countsAttempt;
    }

    public boolean countsKill()
    {
        return countsKill;
    }

    public boolean countsDeath()
    {
        return countsDeath;
    }

    public boolean isCompleted()
    {
        return completed;
    }

    public boolean countsAttempt()
    {
        return countsAttempt;
    }
}
