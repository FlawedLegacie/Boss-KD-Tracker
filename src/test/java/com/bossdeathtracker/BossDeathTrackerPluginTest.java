package com.bossdeathtracker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BossDeathTrackerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(BossDeathTrackerPlugin.class);
        RuneLite.main(args);
    }
}
