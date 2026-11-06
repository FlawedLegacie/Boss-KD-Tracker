package com.bossdeathtracker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class SessionStatistics
{
    private final Instant startedAt;
    private final int attempts;
    private final int kills;
    private final int deaths;
    private final int doubleKos;
    private final int abandoned;
    private final long personalBestMillis;
    private final long averageKillMillis;

    private SessionStatistics(
        Instant startedAt,
        int attempts,
        int kills,
        int deaths,
        int doubleKos,
        int abandoned,
        long personalBestMillis,
        long averageKillMillis)
    {
        this.startedAt = startedAt;
        this.attempts = attempts;
        this.kills = kills;
        this.deaths = deaths;
        this.doubleKos = doubleKos;
        this.abandoned = abandoned;
        this.personalBestMillis = personalBestMillis;
        this.averageKillMillis = averageKillMillis;
    }

    static SessionStatistics calculate(
        Instant sessionStartedAt,
        List<EncounterAttempt> source)
    {
        Instant start = sessionStartedAt == null
            ? Instant.now()
            : sessionStartedAt;
        int attempts = 0;
        int kills = 0;
        int deaths = 0;
        int doubleKos = 0;
        int abandoned = 0;
        long personalBest = 0L;
        long durationTotal = 0L;
        int timedKills = 0;

        if (source != null)
        {
            for (EncounterAttempt attempt : source)
            {
                if (attempt == null
                    || attempt.getStartedAt() == null
                    || attempt.getStartedAt().isBefore(start))
                {
                    continue;
                }

                EncounterOutcome outcome = attempt.getOutcome();
                if (outcome.countsAttempt())
                {
                    attempts++;
                }
                if (outcome.countsKill())
                {
                    kills++;
                    long duration = attempt.getDurationMillis();
                    if (duration > 0L)
                    {
                        personalBest = personalBest == 0L
                            ? duration
                            : Math.min(personalBest, duration);
                        durationTotal += duration;
                        timedKills++;
                    }
                }
                if (outcome.countsDeath())
                {
                    deaths++;
                }
                if (outcome == EncounterOutcome.DOUBLE_KO)
                {
                    doubleKos++;
                }
                if (outcome == EncounterOutcome.ABANDONED
                    || outcome == EncounterOutcome.DISCONNECTED)
                {
                    abandoned++;
                }
            }
        }

        return new SessionStatistics(
            start,
            attempts,
            kills,
            deaths,
            doubleKos,
            abandoned,
            personalBest,
            timedKills == 0 ? 0L : durationTotal / timedKills);
    }

    public Instant getStartedAt()
    {
        return startedAt;
    }

    public int getAttempts()
    {
        return attempts;
    }

    public int getKills()
    {
        return kills;
    }

    public int getDeaths()
    {
        return deaths;
    }

    public int getDoubleKos()
    {
        return doubleKos;
    }

    public int getAbandoned()
    {
        return abandoned;
    }

    public long getPersonalBestMillis()
    {
        return personalBestMillis;
    }

    public long getAverageKillMillis()
    {
        return averageKillMillis;
    }

    public long getElapsedMillis(Instant now)
    {
        Instant end = now == null ? Instant.now() : now;
        return Math.max(0L, Duration.between(startedAt, end).toMillis());
    }

    public double getWinRate()
    {
        return attempts == 0 ? 0.0 : (double) kills / (double) attempts;
    }

    public double getKillsPerHour(Instant now)
    {
        double hours = getElapsedMillis(now) / 3_600_000.0;
        return hours <= 0.0 ? 0.0 : kills / hours;
    }
}
