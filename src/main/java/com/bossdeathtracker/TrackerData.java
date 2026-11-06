package com.bossdeathtracker;

import java.util.ArrayList;
import java.util.List;

public class TrackerData
{
    public static final int CURRENT_SCHEMA_VERSION = 2;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
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

    /**
     * Normalize data loaded from older JSON schemas. Gson initializes missing
     * primitive fields to zero, so migration must be explicit.
     */
    public void migrateToCurrent()
    {
        getBosses();
        getEvents();
        schemaVersion = CURRENT_SCHEMA_VERSION;
    }
}
