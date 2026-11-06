package com.bossdeathtracker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Centralized safeguards for event-sourced kill/death records.
 */
final class TrackerEventPolicy
{
    private TrackerEventPolicy()
    {
    }

    static boolean isDuplicateAutomaticEvent(
        List<TrackerEvent> events,
        String bossId,
        TrackerEventType type,
        Instant now,
        Duration window)
    {
        if (events == null
            || bossId == null
            || type == null
            || now == null
            || window == null
            || window.isNegative())
        {
            return false;
        }

        for (int i = events.size() - 1; i >= 0; i--)
        {
            TrackerEvent event = events.get(i);
            if (event == null || event.getTimestamp() == null)
            {
                continue;
            }

            Duration age = Duration.between(event.getTimestamp(), now);
            if (!age.isNegative() && age.compareTo(window) > 0)
            {
                break;
            }

            if (event.getSource() == TrackerEventSource.AUTOMATIC
                && event.getAmount() > 0
                && bossId.equals(event.getBossId())
                && type == event.getType()
                && !age.isNegative()
                && age.compareTo(window) <= 0)
            {
                return true;
            }
        }

        return false;
    }
}
