package com.bossdeathtracker;

import java.time.Instant;
import java.util.UUID;

public class TrackerEvent
{
    private String id;
    private String bossId;
    private TrackerEventType type;
    private TrackerEventSource source;
    private int amount;
    private Instant timestamp;
    private String note;

    public TrackerEvent()
    {
        // Required for Gson.
    }

    public static TrackerEvent create(
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note)
    {
        return createAt(
            bossId,
            type,
            source,
            amount,
            note,
            Instant.now());
    }

    static TrackerEvent createAt(
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note,
        Instant timestamp)
    {
        TrackerEvent event = new TrackerEvent();
        event.id = UUID.randomUUID().toString();
        event.bossId = bossId;
        event.type = type;
        event.source = source;
        event.amount = amount;
        event.timestamp = timestamp == null ? Instant.now() : timestamp;
        event.note = note == null ? "" : note;
        return event;
    }

    public String getId()
    {
        return id;
    }

    public String getBossId()
    {
        return bossId;
    }

    public TrackerEventType getType()
    {
        return type;
    }

    public TrackerEventSource getSource()
    {
        return source;
    }

    public int getAmount()
    {
        return amount;
    }

    public Instant getTimestamp()
    {
        return timestamp;
    }

    public String getNote()
    {
        return note == null ? "" : note;
    }
}
