package com.bossdeathtracker;

import java.util.ArrayList;
import java.util.List;

public class TrackerData
{
    private int schemaVersion = 1;
    private List<BossProfile> bosses = new ArrayList<>();
    private List<TrackerEvent> events = new ArrayList<>();

    public int getSchemaVersion()
    {
        return schemaVersion;
    }

    public List<BossProfile> getBosses()
    {
        if (bosses == null)
        {
            bosses = new ArrayList<>();
        }
        return bosses;
    }

    public List<TrackerEvent> getEvents()
    {
        if (events == null)
        {
            events = new ArrayList<>();
        }
        return events;
    }
}
