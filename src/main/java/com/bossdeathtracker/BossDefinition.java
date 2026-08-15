package com.bossdeathtracker;

import java.util.Arrays;

public class BossDefinition
{
    private final String key;
    private final String displayName;
    private final String category;
    private final String subcategory;
    private final int[] npcIds;

    public BossDefinition(
        String key,
        String displayName,
        String category,
        String subcategory,
        int... npcIds)
    {
        this.key = key;
        this.displayName = displayName;
        this.category = category;
        this.subcategory = subcategory;
        this.npcIds = npcIds == null ? new int[0] : Arrays.copyOf(npcIds, npcIds.length);
    }

    public String getKey()
    {
        return key;
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

    public int[] getNpcIds()
    {
        return Arrays.copyOf(npcIds, npcIds.length);
    }
}
