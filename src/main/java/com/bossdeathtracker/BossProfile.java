package com.bossdeathtracker;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class BossProfile
{
    private String id;
    private String definitionKey;
    private String displayName;
    private String category;
    private String subcategory;
    private boolean customBoss;
    private boolean favorite;
    private SyncStatus syncStatus;
    private List<Integer> npcIds = new ArrayList<>();
    private boolean encounterVerified;
    private boolean deathVerified;
    private boolean killVerified;
    private String notes;
    private Instant createdAt;
    private Instant updatedAt;

    public BossProfile()
    {
        // Required for Gson.
    }

    public static BossProfile createManual(
        String displayName,
        String category,
        String subcategory,
        List<Integer> npcIds,
        String notes)
    {
        BossProfile boss = new BossProfile();
        boss.id = UUID.randomUUID().toString();
        boss.displayName = displayName;
        boss.category = category;
        boss.subcategory = subcategory;
        boss.customBoss = true;
        boss.syncStatus =
            npcIds == null || npcIds.isEmpty()
                ? SyncStatus.MANUAL
                : SyncStatus.PARTIAL;
        boss.npcIds = copyIds(npcIds);
        boss.notes = notes == null ? "" : notes;
        boss.createdAt = Instant.now();
        boss.updatedAt = boss.createdAt;
        return boss;
    }

    public static BossProfile createFromDefinition(BossDefinition definition)
    {
        BossProfile boss = new BossProfile();
        boss.id = UUID.randomUUID().toString();
        boss.definitionKey = definition.getKey();
        boss.displayName = definition.getDisplayName();
        boss.category = definition.getCategory();
        boss.subcategory = definition.getSubcategory();
        boss.customBoss = false;

        for (int npcId : definition.getNpcIds())
        {
            boss.npcIds.add(npcId);
        }

        boss.syncStatus = SyncStatus.PARTIAL;
        boss.notes = "";
        boss.createdAt = Instant.now();
        boss.updatedAt = boss.createdAt;
        return boss;
    }

    public String getId()
    {
        return id;
    }

    public String getDefinitionKey()
    {
        return definitionKey;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public String getCategory()
    {
        return category;
    }

    public String getSubcategory()
    {
        return subcategory;
    }

    public boolean isCustomBoss()
    {
        return customBoss;
    }

    public boolean isFavorite()
    {
        return favorite;
    }

    public SyncStatus getSyncStatus()
    {
        return syncStatus == null ? SyncStatus.MANUAL : syncStatus;
    }

    public List<Integer> getNpcIds()
    {
        if (npcIds == null)
        {
            npcIds = new ArrayList<>();
        }

        return Collections.unmodifiableList(npcIds);
    }

    public boolean hasNpcId(int npcId)
    {
        return getNpcIds().contains(npcId);
    }

    public boolean isEncounterVerified()
    {
        return encounterVerified;
    }

    public boolean isDeathVerified()
    {
        return deathVerified;
    }

    public boolean isKillVerified()
    {
        return killVerified;
    }

    public String getNotes()
    {
        return notes == null ? "" : notes;
    }

    public Instant getCreatedAt()
    {
        return createdAt;
    }

    public Instant getUpdatedAt()
    {
        return updatedAt;
    }

    public void setDisplayName(String displayName)
    {
        this.displayName = displayName;
        touch();
    }

    public void setCategory(String category)
    {
        this.category = category;
        touch();
    }

    public void setSubcategory(String subcategory)
    {
        this.subcategory = subcategory;
        touch();
    }

    public void setFavorite(boolean favorite)
    {
        this.favorite = favorite;
        touch();
    }

    public void setNpcIds(List<Integer> ids)
    {
        npcIds = copyIds(ids);

        if (customBoss
            && !npcIds.isEmpty()
            && getSyncStatus() == SyncStatus.MANUAL)
        {
            syncStatus = SyncStatus.PARTIAL;
        }

        touch();
    }

    /**
     * Replace the profile with the authoritative built-in definition.
     * User-supplied extra NPC IDs, notes, favorites, event history, and
     * verification flags are intentionally retained.
     */
    public boolean synchronizeDefinition(BossDefinition definition)
    {
        if (definition == null)
        {
            return false;
        }

        boolean changed = false;

        if (!safeEquals(definitionKey, definition.getKey()))
        {
            definitionKey = definition.getKey();
            changed = true;
        }
        if (!safeEquals(displayName, definition.getDisplayName()))
        {
            displayName = definition.getDisplayName();
            changed = true;
        }
        if (!safeEquals(category, definition.getCategory()))
        {
            category = definition.getCategory();
            changed = true;
        }
        if (!safeEquals(subcategory, definition.getSubcategory()))
        {
            subcategory = definition.getSubcategory();
            changed = true;
        }
        if (customBoss)
        {
            customBoss = false;
            changed = true;
        }

        List<Integer> mergedIds = copyIds(npcIds);
        for (int npcId : definition.getNpcIds())
        {
            if (!mergedIds.contains(npcId))
            {
                mergedIds.add(npcId);
                changed = true;
            }
        }

        if (changed)
        {
            npcIds = mergedIds;
            syncStatus = deathVerified && killVerified
                ? SyncStatus.AUTO
                : SyncStatus.SYNCED;
            touch();
        }

        return changed;
    }

    public void applyDefinition(BossDefinition definition)
    {
        definitionKey = definition.getKey();
        displayName = definition.getDisplayName();
        category = definition.getCategory();
        subcategory = definition.getSubcategory();
        customBoss = false;

        npcIds = new ArrayList<>();
        for (int npcId : definition.getNpcIds())
        {
            npcIds.add(npcId);
        }

        syncStatus = SyncStatus.SYNCED;
        touch();
    }

    public void markEncounterVerified()
    {
        encounterVerified = true;
        updateVerificationStatus();
        touch();
    }

    public void markDeathVerified()
    {
        encounterVerified = true;
        deathVerified = true;
        updateVerificationStatus();
        touch();
    }

    public void markKillVerified()
    {
        encounterVerified = true;
        killVerified = true;
        updateVerificationStatus();
        touch();
    }

    private void updateVerificationStatus()
    {
        if (deathVerified && killVerified)
        {
            syncStatus = SyncStatus.AUTO;
        }
        else if (getSyncStatus() == SyncStatus.MANUAL
            && !getNpcIds().isEmpty())
        {
            syncStatus = SyncStatus.PARTIAL;
        }
    }

    public void setNotes(String notes)
    {
        this.notes = notes == null ? "" : notes;
        touch();
    }

    public void setSyncStatus(SyncStatus syncStatus)
    {
        this.syncStatus = syncStatus;
        touch();
    }

    private void touch()
    {
        updatedAt = Instant.now();
    }

    private static boolean safeEquals(String left, String right)
    {
        return left == null ? right == null : left.equals(right);
    }

    private static List<Integer> copyIds(List<Integer> ids)
    {
        List<Integer> copy = new ArrayList<>();

        if (ids != null)
        {
            for (Integer id : ids)
            {
                if (id != null && id >= 0 && !copy.contains(id))
                {
                    copy.add(id);
                }
            }
        }

        return copy;
    }
}
