package com.bossdeathtracker;

import java.time.Instant;
import java.util.UUID;

public class TrackerEvent
{
    private String id;
    private String encounterId;
    private String reversesEventId;
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
        return createForEncounter(
            null,
            bossId,
            type,
            source,
            amount,
            note);
    }

    static TrackerEvent createForEncounter(
        String encounterId,
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note)
    {
        return createAt(
            encounterId,
            bossId,
            type,
            source,
            amount,
            note,
            Instant.now());
    }

    static TrackerEvent createUndo(
        TrackerEvent original,
        String note)
    {
        if (original == null || original.getAmount() <= 0)
        {
            throw new IllegalArgumentException(
                "A positive event is required for undo");
        }

        TrackerEvent undo = createAt(
            original.getEncounterId(),
            original.getBossId(),
            original.getType(),
            TrackerEventSource.CORRECTED,
            -original.getAmount(),
            note,
            Instant.now());
        undo.reversesEventId = original.getId();
        return undo;
    }

    static TrackerEvent createAt(
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note,
        Instant timestamp)
    {
        return createAt(
            null,
            bossId,
            type,
            source,
            amount,
            note,
            timestamp);
    }

    static TrackerEvent createAt(
        String encounterId,
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note,
        Instant timestamp)
    {
        TrackerEvent event = new TrackerEvent();
        event.id = UUID.randomUUID().toString();
        event.encounterId = encounterId;
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

    public String getEncounterId()
    {
        return encounterId;
    }

    public String getReversesEventId()
    {
        return reversesEventId;
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
