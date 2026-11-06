package com.bossdeathtracker;

import java.util.ArrayList;
import java.util.List;

public class TrackerData
{
    public static final int CURRENT_SCHEMA_VERSION = 4;

    private int schemaVersion = CURRENT_SCHEMA_VERSION;
    private List<BossProfile> bosses = new ArrayList<>();
    private List<TrackerEvent> events = new ArrayList<>();
    private List<EncounterAttempt> attempts = new ArrayList<>();

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

    public List<EncounterAttempt> getAttempts()
    {
        if (attempts == null)
        {
            attempts = new ArrayList<>();
        }
        return attempts;
    }

    /**
     * Normalize data loaded from older JSON schemas. Gson initializes missing
     * fields to null or zero, so migration must be explicit.
     */
    public void migrateToCurrent()
    {
        getBosses();
        getEvents();
        getAttempts();
        schemaVersion = CURRENT_SCHEMA_VERSION;
    }
}
