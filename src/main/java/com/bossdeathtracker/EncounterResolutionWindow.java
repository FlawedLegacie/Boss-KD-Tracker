package com.bossdeathtracker;

import java.util.Optional;

/**
 * Resolves RuneLite event-order races. Boss and player ActorDeath events can
 * arrive in either order, so outcomes are held briefly before being committed.
 */
final class EncounterResolutionWindow
{
    private final int delayTicks;
    private int firstSignalTick = -1;
    private boolean bossDeathObserved;
    private boolean playerDeathObserved;
    private boolean killCountConfirmed;

    EncounterResolutionWindow(int delayTicks)
    {
        if (delayTicks < 1)
        {
            throw new IllegalArgumentException("delayTicks must be positive");
        }
        this.delayTicks = delayTicks;
    }

    void observeBossDeath(int tick)
    {
        markSignal(tick);
        bossDeathObserved = true;
    }

    void observePlayerDeath(int tick)
    {
        markSignal(tick);
        playerDeathObserved = true;
    }

    void confirmKillCount(int tick)
    {
        markSignal(tick);
        killCountConfirmed = true;
    }

    boolean hasPendingSignals()
    {
        return firstSignalTick >= 0;
    }

    Optional<EncounterOutcome> resolve(
        int currentTick,
        boolean playerAlive)
    {
        if (!hasPendingSignals()
            || currentTick - firstSignalTick < delayTicks)
        {
            return Optional.empty();
        }

        if (playerDeathObserved)
        {
            return Optional.of(killCountConfirmed
                ? EncounterOutcome.DOUBLE_KO
                : EncounterOutcome.DEATH);
        }

        if (bossDeathObserved || killCountConfirmed)
        {
            return Optional.of(playerAlive
                ? EncounterOutcome.KILL
                : EncounterOutcome.DEATH);
        }

        return Optional.empty();
    }

    String completionSignal()
    {
        StringBuilder signal = new StringBuilder();

        if (bossDeathObserved)
        {
            signal.append("BOSS_ACTOR_DEATH");
        }
        if (playerDeathObserved)
        {
            appendSeparator(signal);
            signal.append("PLAYER_ACTOR_DEATH");
        }
        if (killCountConfirmed)
        {
            appendSeparator(signal);
            signal.append("KILLCOUNT_CHANGE");
        }

        return signal.length() == 0 ? "UNKNOWN" : signal.toString();
    }

    void reset()
    {
        firstSignalTick = -1;
        bossDeathObserved = false;
        playerDeathObserved = false;
        killCountConfirmed = false;
    }

    private void markSignal(int tick)
    {
        if (firstSignalTick < 0 || tick < firstSignalTick)
        {
            firstSignalTick = tick;
        }
    }

    private static void appendSeparator(StringBuilder value)
    {
        if (value.length() > 0)
        {
            value.append('+');
        }
    }
}
