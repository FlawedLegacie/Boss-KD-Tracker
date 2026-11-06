from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} not found")
    return text.replace(old, new, 1)


path = Path("src/main/java/com/bossdeathtracker/BossDeathTrackerStore.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "import java.util.Comparator;\nimport java.util.List;",
    "import java.util.Comparator;\nimport java.util.HashSet;\nimport java.util.List;",
    "HashSet import",
)
source = replace_once(
    source,
    "import java.util.Optional;\nimport java.util.concurrent.Executors;",
    "import java.util.Optional;\nimport java.util.Set;\nimport java.util.concurrent.Executors;",
    "Set import",
)

profile_anchor = """    public synchronized List<BossProfile> getBosses()
    {
"""
profile_methods = """    public synchronized int getSchemaVersion()
    {
        return data.getSchemaVersion();
    }

    public synchronized void setFavorite(String bossId, boolean favorite)
    {
        BossProfile boss = requireBoss(bossId);
        boss.setFavorite(favorite);
        queueSave();
    }

"""
source = replace_once(
    source,
    profile_anchor,
    profile_methods + profile_anchor,
    "profile API anchor",
)

events_anchor = """    public synchronized String beginEncounter(
"""
events_methods = """    public synchronized List<TrackerEvent> getAllEvents()
    {
        List<TrackerEvent> result = new ArrayList<>(data.getEvents());
        result.sort(Comparator.comparing(
            TrackerEvent::getTimestamp,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public synchronized UndoResult undoLastEvent(String bossId)
    {
        for (int index = data.getEvents().size() - 1; index >= 0; index--)
        {
            TrackerEvent event = data.getEvents().get(index);
            if (event == null
                || event.getAmount() <= 0
                || event.getSource() == TrackerEventSource.IMPORTED
                || (bossId != null && !bossId.equals(event.getBossId()))
                || isEventReversedLocked(event.getId()))
            {
                continue;
            }

            return undoEventLocked(event);
        }

        return UndoResult.none("No reversible kill or death was found.");
    }

    public synchronized UndoResult undoEvent(String eventId)
    {
        if (eventId == null)
        {
            return UndoResult.none("No history entry was selected.");
        }

        for (TrackerEvent event : data.getEvents())
        {
            if (event != null && eventId.equals(event.getId()))
            {
                return undoEventLocked(event);
            }
        }

        return UndoResult.none("The selected event no longer exists.");
    }

    public synchronized UndoResult voidEncounter(String encounterId)
    {
        EncounterAttempt attempt = findAttemptLocked(encounterId);
        if (attempt == null)
        {
            return UndoResult.none("The selected encounter no longer exists.");
        }
        if (attempt.isVoided())
        {
            return UndoResult.none("The selected encounter is already voided.");
        }

        attempt.voidAttempt("Voided from history");
        List<TrackerEvent> originals = new ArrayList<>();

        for (TrackerEvent event : data.getEvents())
        {
            if (event != null
                && encounterId.equals(event.getEncounterId())
                && event.getAmount() > 0
                && event.getSource() != TrackerEventSource.IMPORTED
                && !isEventReversedLocked(event.getId()))
            {
                originals.add(event);
            }
        }

        for (TrackerEvent original : originals)
        {
            data.getEvents().add(TrackerEvent.createUndo(
                original,
                "Voided encounter " + encounterId));
        }

        queueSave();
        return UndoResult.success(
            attempt.getBossName(),
            "Voided " + attempt.getOutcome() + " encounter");
    }

    private UndoResult undoEventLocked(TrackerEvent event)
    {
        if (event == null || event.getAmount() <= 0)
        {
            return UndoResult.none("Only positive kill/death records can be undone.");
        }
        if (event.getSource() == TrackerEventSource.IMPORTED)
        {
            return UndoResult.none(
                "Imported historical KC cannot be undone one event at a time.");
        }
        if (isEventReversedLocked(event.getId()))
        {
            return UndoResult.none("That record has already been undone.");
        }

        data.getEvents().add(TrackerEvent.createUndo(
            event,
            "Undo history event " + event.getId()));

        if (event.getEncounterId() != null)
        {
            EncounterAttempt attempt = findAttemptLocked(event.getEncounterId());
            if (attempt != null)
            {
                attempt.voidAttempt("Count record undone from history");
            }
        }

        BossProfile boss = findBoss(event.getBossId()).orElse(null);
        queueSave();
        return UndoResult.success(
            boss == null ? event.getBossId() : boss.getDisplayName(),
            "Undid " + event.getType() + " " + event.getAmount());
    }

    private boolean isEventReversedLocked(String eventId)
    {
        if (eventId == null)
        {
            return false;
        }

        for (TrackerEvent event : data.getEvents())
        {
            if (event != null && eventId.equals(event.getReversesEventId()))
            {
                return true;
            }
        }

        return false;
    }

"""
source = replace_once(
    source,
    events_anchor,
    events_methods + events_anchor,
    "event history API anchor",
)

management_anchor = """    public synchronized void markEncounterVerified(String bossId)
"""
management_methods = """    public synchronized DefinitionSyncResult synchronizeDefinitions()
    {
        int added = 0;
        int updated = 0;
        int unchanged = 0;

        for (BossDefinition definition : BossDefinitionRegistry.getAll())
        {
            BossProfile matched = null;

            for (BossProfile boss : data.getBosses())
            {
                if (definition.getKey().equals(boss.getDefinitionKey())
                    || definition.getDisplayName().equalsIgnoreCase(
                        boss.getDisplayName()))
                {
                    matched = boss;
                    break;
                }
            }

            if (matched == null)
            {
                data.getBosses().add(
                    BossProfile.createFromDefinition(definition));
                added++;
            }
            else if (matched.synchronizeDefinition(definition))
            {
                updated++;
            }
            else
            {
                unchanged++;
            }
        }

        int conflicts = countNpcIdConflictsLocked();

        if (added > 0 || updated > 0)
        {
            queueSave();
        }

        return new DefinitionSyncResult(
            added,
            updated,
            unchanged,
            conflicts);
    }

    public void exportCurrentProfile(Path destination) throws IOException
    {
        if (destination == null)
        {
            throw new IllegalArgumentException("Export destination is required.");
        }

        final String json;
        synchronized (this)
        {
            data.migrateToCurrent();
            json = gson.toJson(data);
        }

        Path parent = destination.toAbsolutePath().getParent();
        if (parent != null)
        {
            Files.createDirectories(parent);
        }
        Files.writeString(
            destination,
            json,
            StandardCharsets.UTF_8);
    }

    public ImportResult importCurrentProfile(Path sourceFile)
        throws IOException
    {
        if (sourceFile == null || !Files.isRegularFile(sourceFile))
        {
            throw new IllegalArgumentException("A valid import file is required.");
        }

        String json = Files.readString(sourceFile, StandardCharsets.UTF_8);
        TrackerData imported;

        try
        {
            imported = gson.fromJson(json, TrackerData.class);
        }
        catch (RuntimeException ex)
        {
            throw new IOException("The selected file is not valid tracker data.", ex);
        }

        validateImportedData(imported);
        imported.migrateToCurrent();

        synchronized (this)
        {
            data = imported;
            seedBuiltInDefinitionsLocked();
        }

        flush();
        return new ImportResult(
            imported.getBosses().size(),
            imported.getEvents().size(),
            imported.getAttempts().size());
    }

    private void validateImportedData(TrackerData imported)
        throws IOException
    {
        if (imported == null)
        {
            throw new IOException("The selected file contained no tracker data.");
        }

        imported.migrateToCurrent();
        Set<String> bossIds = new HashSet<>();

        for (BossProfile boss : imported.getBosses())
        {
            if (boss == null
                || boss.getId() == null
                || boss.getId().trim().isEmpty()
                || !bossIds.add(boss.getId()))
            {
                throw new IOException(
                    "The import contains an invalid or duplicate boss ID.");
            }
        }

        for (TrackerEvent event : imported.getEvents())
        {
            if (event == null || !bossIds.contains(event.getBossId()))
            {
                throw new IOException(
                    "The import contains an event for an unknown boss.");
            }
        }

        for (EncounterAttempt attempt : imported.getAttempts())
        {
            if (attempt == null || !bossIds.contains(attempt.getBossId()))
            {
                throw new IOException(
                    "The import contains an encounter for an unknown boss.");
            }
        }
    }

    private int countNpcIdConflictsLocked()
    {
        Set<Integer> seen = new HashSet<>();
        Set<Integer> conflicts = new HashSet<>();

        for (BossProfile boss : data.getBosses())
        {
            for (Integer npcId : boss.getNpcIds())
            {
                if (!seen.add(npcId))
                {
                    conflicts.add(npcId);
                }
            }
        }

        return conflicts.size();
    }

"""
source = replace_once(
    source,
    management_anchor,
    management_methods + management_anchor,
    "data management API anchor",
)

source = replace_once(
    source,
    """    public synchronized void deleteBoss(String bossId)
    {
        data.getBosses().removeIf(boss -> boss.getId().equals(bossId));
        data.getEvents().removeIf(event -> event.getBossId().equals(bossId));
        data.getAttempts().removeIf(
            attempt -> bossId.equals(attempt.getBossId()));
        queueSave();
    }
""",
    """    public synchronized void deleteBoss(String bossId)
    {
        BossProfile boss = requireBoss(bossId);
        if (!boss.isCustomBoss())
        {
            throw new IllegalArgumentException(
                "Built-in bosses cannot be deleted. Synchronize definitions to reset them.");
        }

        data.getBosses().removeIf(item -> item.getId().equals(bossId));
        data.getEvents().removeIf(event -> event.getBossId().equals(bossId));
        data.getAttempts().removeIf(
            attempt -> bossId.equals(attempt.getBossId()));
        queueSave();
    }
""",
    "protect built-in definitions",
)

result_anchor = """    private static final class LoadResult
    {
"""
result_types = """    public static final class UndoResult
    {
        private final boolean success;
        private final String bossName;
        private final String description;

        private UndoResult(
            boolean success,
            String bossName,
            String description)
        {
            this.success = success;
            this.bossName = bossName == null ? "" : bossName;
            this.description = description == null ? "" : description;
        }

        static UndoResult success(String bossName, String description)
        {
            return new UndoResult(true, bossName, description);
        }

        static UndoResult none(String description)
        {
            return new UndoResult(false, "", description);
        }

        public boolean isSuccess()
        {
            return success;
        }

        public String getBossName()
        {
            return bossName;
        }

        public String getDescription()
        {
            return description;
        }
    }

    public static final class DefinitionSyncResult
    {
        private final int added;
        private final int updated;
        private final int unchanged;
        private final int conflicts;

        private DefinitionSyncResult(
            int added,
            int updated,
            int unchanged,
            int conflicts)
        {
            this.added = added;
            this.updated = updated;
            this.unchanged = unchanged;
            this.conflicts = conflicts;
        }

        public int getAdded()
        {
            return added;
        }

        public int getUpdated()
        {
            return updated;
        }

        public int getUnchanged()
        {
            return unchanged;
        }

        public int getConflicts()
        {
            return conflicts;
        }
    }

    public static final class ImportResult
    {
        private final int bosses;
        private final int events;
        private final int attempts;

        private ImportResult(int bosses, int events, int attempts)
        {
            this.bosses = bosses;
            this.events = events;
            this.attempts = attempts;
        }

        public int getBosses()
        {
            return bosses;
        }

        public int getEvents()
        {
            return events;
        }

        public int getAttempts()
        {
            return attempts;
        }
    }

"""
source = replace_once(
    source,
    result_anchor,
    result_types + result_anchor,
    "result type anchor",
)

path.write_text(source, encoding="utf-8")

# Voided attempts must be excluded from analytics.
for filename in ("BossStatistics.java", "SessionStatistics.java"):
    stats_path = Path("src/main/java/com/bossdeathtracker") / filename
    stats = stats_path.read_text(encoding="utf-8")
    if filename == "BossStatistics.java":
        old = """                if (attempt != null
                    && bossId != null
                    && bossId.equals(attempt.getBossId()))
"""
        new = """                if (attempt != null
                    && !attempt.isVoided()
                    && bossId != null
                    && bossId.equals(attempt.getBossId()))
"""
    else:
        old = """                if (attempt != null
                    && attempt.getStartedAt() != null
                    && !attempt.getStartedAt().isBefore(start))
"""
        new = """                if (attempt != null
                    && !attempt.isVoided()
                    && attempt.getStartedAt() != null
                    && !attempt.getStartedAt().isBefore(start))
"""
    stats = replace_once(stats, old, new, f"{filename} void filter")
    stats_path.write_text(stats, encoding="utf-8")
