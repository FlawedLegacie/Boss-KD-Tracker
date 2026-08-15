package com.bossdeathtracker;

import java.util.Optional;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;

/**
 * Holds short-lived encounter attribution state.
 *
 * The tracker does not itself subscribe to RuneLite events. RuneLite event
 * subscribers remain in BossDeathTrackerPlugin and forward relevant events
 * here.
 */
public class BossEncounterTracker
{
    // 100 game ticks is about one minute. This prevents an old encounter from
    // receiving a death after the player has clearly left the fight.
    private static final int ENCOUNTER_TIMEOUT_TICKS = 100;

    private String activeBossId;
    private String activeBossName;
    private int activeBossNpcId = -1;
    private int lastConfirmedTick = -1;

    public void reset()
    {
        activeBossId = null;
        activeBossName = null;
        activeBossNpcId = -1;
        lastConfirmedTick = -1;
    }

    public void observeNpc(
        NPC npc,
        Player localPlayer,
        BossDeathTrackerStore store,
        int currentTick)
    {
        if (npc == null || localPlayer == null)
        {
            return;
        }

        Optional<BossProfile> boss = store.findBossByNpcId(npc.getId());

        if (!boss.isPresent())
        {
            return;
        }

        Actor npcTarget = npc.getInteracting();
        Actor playerTarget = localPlayer.getInteracting();

        // A recognized boss becomes active when either side is explicitly
        // interacting with the other. Merely spawning nearby is not enough.
        if (npcTarget == localPlayer || playerTarget == npc)
        {
            activate(boss.get(), npc.getId(), currentTick);
        }
    }

    public void observeInteraction(
        Actor source,
        Actor target,
        Player localPlayer,
        BossDeathTrackerStore store,
        int currentTick)
    {
        if (source == null || target == null || localPlayer == null)
        {
            return;
        }

        NPC npc = null;

        if (source == localPlayer && target instanceof NPC)
        {
            npc = (NPC) target;
        }
        else if (target == localPlayer && source instanceof NPC)
        {
            npc = (NPC) source;
        }

        if (npc == null)
        {
            return;
        }

        Optional<BossProfile> boss = store.findBossByNpcId(npc.getId());

        if (boss.isPresent())
        {
            activate(boss.get(), npc.getId(), currentTick);
        }
    }

    public void observeNpcChanged(
        NPC npc,
        Player localPlayer,
        BossDeathTrackerStore store,
        int currentTick)
    {
        if (npc == null || localPlayer == null)
        {
            return;
        }

        Optional<BossProfile> changedBoss = store.findBossByNpcId(npc.getId());

        if (!changedBoss.isPresent())
        {
            return;
        }

        BossProfile boss = changedBoss.get();

        // Phase/form changes commonly alter an NPC ID. Preserve encounter
        // ownership if the changed NPC belongs to the same boss definition,
        // or if it is interacting with the local player.
        if (boss.getId().equals(activeBossId)
            || npc.getInteracting() == localPlayer
            || localPlayer.getInteracting() == npc)
        {
            activate(boss, npc.getId(), currentTick);
        }
    }

    public void onGameTick(int currentTick)
    {
        if (activeBossId == null || lastConfirmedTick < 0)
        {
            return;
        }

        if (currentTick - lastConfirmedTick > ENCOUNTER_TIMEOUT_TICKS)
        {
            reset();
        }
    }

    public String getActiveBossId()
    {
        return activeBossId;
    }

    public Optional<Attribution> getKillAttribution(
        NPC deadNpc,
        BossDeathTrackerStore store,
        int currentTick)
    {
        if (deadNpc == null || activeBossId == null || lastConfirmedTick < 0)
        {
            return Optional.empty();
        }

        if (currentTick - lastConfirmedTick > ENCOUNTER_TIMEOUT_TICKS)
        {
            reset();
            return Optional.empty();
        }

        Optional<BossProfile> deadBoss = store.findBossByNpcId(deadNpc.getId());

        if (!deadBoss.isPresent() || !deadBoss.get().getId().equals(activeBossId))
        {
            return Optional.empty();
        }

        return Optional.of(new Attribution(
            activeBossId,
            activeBossName,
            deadNpc.getId()));
    }

    public Optional<Attribution> getDeathAttribution(int currentTick)
    {
        if (activeBossId == null || lastConfirmedTick < 0)
        {
            return Optional.empty();
        }

        if (currentTick - lastConfirmedTick > ENCOUNTER_TIMEOUT_TICKS)
        {
            reset();
            return Optional.empty();
        }

        return Optional.of(new Attribution(
            activeBossId,
            activeBossName,
            activeBossNpcId));
    }

    private void activate(BossProfile boss, int npcId, int currentTick)
    {
        activeBossId = boss.getId();
        activeBossName = boss.getDisplayName();
        activeBossNpcId = npcId;
        lastConfirmedTick = currentTick;
    }

    public static final class Attribution
    {
        private final String bossId;
        private final String bossName;
        private final int npcId;

        private Attribution(String bossId, String bossName, int npcId)
        {
            this.bossId = bossId;
            this.bossName = bossName;
            this.npcId = npcId;
        }

        public String getBossId()
        {
            return bossId;
        }

        public String getBossName()
        {
            return bossName;
        }

        public int getNpcId()
        {
            return npcId;
        }
    }
}
