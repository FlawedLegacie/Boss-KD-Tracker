package com.bossdeathtracker;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(BossDeathTrackerConfig.GROUP)
public interface BossDeathTrackerConfig extends Config
{
    String GROUP = "boss-death-tracker";

    @ConfigItem(
        keyName = "automaticDeaths",
        name = "Automatic deaths",
        description = "Automatically attribute your death to the active recognized boss",
        position = 0
    )
    default boolean automaticDeaths()
    {
        return true;
    }

    @ConfigItem(
        keyName = "automaticKills",
        name = "Automatic kills",
        description = "Automatically record a kill when the active recognized boss dies",
        position = 1
    )
    default boolean automaticKills()
    {
        return true;
    }

    @ConfigItem(
        keyName = "confirmCorrections",
        name = "Confirm corrections",
        description = "Ask before subtracting a manually recorded death or kill"
    )
    default boolean confirmCorrections()
    {
        return true;
    }
}
