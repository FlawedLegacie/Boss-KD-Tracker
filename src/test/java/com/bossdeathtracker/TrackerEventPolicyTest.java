package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TrackerEventPolicyTest
{
    private static final Duration WINDOW = Duration.ofSeconds(5);

    @Test
    void suppressesSameAutomaticEventInsideWindow()
    {
        Instant now = Instant.parse("2026-11-06T06:00:05Z");
        List<TrackerEvent> events = new ArrayList<>();
        events.add(TrackerEvent.createAt(
            "vorkath",
            TrackerEventType.KILL,
            TrackerEventSource.AUTOMATIC,
            1,
            "automatic",
            now.minusSeconds(2)));

        assertTrue(TrackerEventPolicy.isDuplicateAutomaticEvent(
            events,
            "vorkath",
            TrackerEventType.KILL,
            now,
            WINDOW));
    }

    @Test
    void permitsDifferentBossTypeOrSource()
    {
        Instant now = Instant.parse("2026-11-06T06:00:05Z");
        List<TrackerEvent> events = new ArrayList<>();
        events.add(TrackerEvent.createAt(
            "vorkath",
            TrackerEventType.KILL,
            TrackerEventSource.MANUAL,
            1,
            "manual",
            now.minusSeconds(1)));

        assertFalse(TrackerEventPolicy.isDuplicateAutomaticEvent(
            events,
            "vorkath",
            TrackerEventType.KILL,
            now,
            WINDOW));
        assertFalse(TrackerEventPolicy.isDuplicateAutomaticEvent(
            events,
            "zulrah",
            TrackerEventType.KILL,
            now,
            WINDOW));
        assertFalse(TrackerEventPolicy.isDuplicateAutomaticEvent(
            events,
            "vorkath",
            TrackerEventType.DEATH,
            now,
            WINDOW));
    }

    @Test
    void permitsEventAfterDeduplicationWindow()
    {
        Instant now = Instant.parse("2026-11-06T06:00:10Z");
        List<TrackerEvent> events = new ArrayList<>();
        events.add(TrackerEvent.createAt(
            "vorkath",
            TrackerEventType.KILL,
            TrackerEventSource.AUTOMATIC,
            1,
            "automatic",
            now.minusSeconds(6)));

        assertFalse(TrackerEventPolicy.isDuplicateAutomaticEvent(
            events,
            "vorkath",
            TrackerEventType.KILL,
            now,
            WINDOW));
    }
}
