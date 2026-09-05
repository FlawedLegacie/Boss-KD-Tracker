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
        keyName = "shareKdCommand",
        name = "Share !KD in chat",
        description = "Opt in to sending your RuneScape name, typed boss query, resolved boss, kills, and deaths to the configured K/D share service so other Boss KD Tracker clients can render !KD results",
        warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
        position = 2
    )
    default boolean shareKdCommand()
    {
        return false;
    }

    @ConfigItem(
        keyName = "kdShareServer",
        name = "K/D share server",
        description = "HTTPS base URL for the Boss KD Tracker share service. No network request is made unless Share !KD in chat is enabled.",
        position = 3
    )
    default String kdShareServer()
    {
        return "";
    }

    @ConfigItem(
        keyName = "confirmCorrections",
        name = "Confirm corrections",
        description = "Ask before subtracting a manually recorded death or kill",
        position = 4
    )
    default boolean confirmCorrections()
    {
        return true;
    }
}
