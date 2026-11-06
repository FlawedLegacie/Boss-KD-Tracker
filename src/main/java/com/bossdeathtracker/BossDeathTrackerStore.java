package com.bossdeathtracker;

import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BossDeathTrackerStore
{
    private static final Logger log =
        LoggerFactory.getLogger(BossDeathTrackerStore.class);

    private static final Path DATA_DIR =
        RuneLite.RUNELITE_DIR.toPath().resolve("boss-death-tracker");
    private static final Path PROFILES_DIR = DATA_DIR.resolve("profiles");
    private static final Path LEGACY_DATA_FILE =
        DATA_DIR.resolve("tracker-data.json");
    private static final Path LEGACY_MIGRATION_MARKER =
        DATA_DIR.resolve(".legacy-profile-migrated");

    private static final int BACKUP_COUNT = 3;
    private static final long SAVE_DELAY_MILLIS = 250L;
    private static final Duration AUTOMATIC_EVENT_DEDUP_WINDOW =
        Duration.ofSeconds(5);

    private final Gson gson;
    private final ConfigManager configManager;
    private final Object ioLock = new Object();

    private ScheduledExecutorService ioExecutor;
    private ScheduledFuture<?> pendingSave;
    private TrackerData data = new TrackerData();
    private Path activeDataFile = LEGACY_DATA_FILE;
    private String activeStorageKey = "legacy";
    private boolean loaded;

    @Inject
    public BossDeathTrackerStore(
        Gson gson,
        ConfigManager configManager)
    {
        this.gson = gson;
        this.configManager = configManager;
    }

    public void loadAsync(Consumer<Throwable> completion)
    {
        loadCurrentProfileAsync(completion);
    }

    public void loadCurrentProfileAsync(Consumer<Throwable> completion)
    {
        String profileKey =
            configManager == null ? null : configManager.getRSProfileKey();
        String storageKey = ProfileStorageKey.from(profileKey);
        Path targetFile = resolveDataFile(storageKey);

        ensureExecutor().execute(() ->
        {
            Throwable error = null;

            try
            {
                switchProfile(storageKey, targetFile);
            }
            catch (Exception ex)
            {
                error = ex;
                log.warn(
                    "Unable to load Boss Death Tracker profile {}",
                    storageKey,
                    ex);
            }

            if (completion != null)
            {
                completion.accept(error);
            }
        });
    }

    public synchronized String getActiveStorageKey()
    {
        return activeStorageKey;
    }

    public synchronized int getSchemaVersion()
    {
        return data.getSchemaVersion();
    }

    public synchronized void setFavorite(String bossId, boolean favorite)
    {
        BossProfile boss = requireBoss(bossId);
        boss.setFavorite(favorite);
        queueSave();
    }

    public synchronized List<BossProfile> getBosses()
    {
        List<BossProfile> result = new ArrayList<>(data.getBosses());
        result.sort(Comparator.comparing(
            BossProfile::getDisplayName,
            String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    public synchronized List<BossProfile> searchBosses(String query)
    {
        if (query == null || query.trim().isEmpty())
        {
            return getBosses();
        }

        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<BossProfile> result = new ArrayList<>();

        for (BossProfile boss : data.getBosses())
        {
            String npcIdText = boss.getNpcIds()
                .stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(" "));

            String haystack =
                (safe(boss.getDisplayName()) + " " +
                 safe(boss.getCategory()) + " " +
                 safe(boss.getSubcategory()) + " " +
                 npcIdText).toLowerCase(Locale.ROOT);

            if (haystack.contains(needle))
            {
                result.add(boss);
            }
        }

        result.sort(Comparator.comparing(
            BossProfile::getDisplayName,
            String.CASE_INSENSITIVE_ORDER));

        return result;
    }

    public synchronized Optional<BossProfile> findBoss(String bossId)
    {
        return data.getBosses()
            .stream()
            .filter(b -> b.getId().equals(bossId))
            .findFirst();
    }

    public synchronized Optional<BossProfile> findBossByNpcId(int npcId)
    {
        return data.getBosses()
            .stream()
            .filter(boss -> boss.hasNpcId(npcId))
            .findFirst();
    }

    public synchronized BossProfile addManualBoss(
        String displayName,
        String category,
        String subcategory,
        List<Integer> npcIds,
        String notes)
    {
        String cleanName = displayName == null ? "" : displayName.trim();

        if (cleanName.isEmpty())
        {
            throw new IllegalArgumentException("Boss name is required.");
        }

        for (BossProfile existing : data.getBosses())
        {
            if (existing.getDisplayName().equalsIgnoreCase(cleanName))
            {
                throw new IllegalArgumentException(
                    "A boss with that name already exists.");
            }
        }

        BossProfile boss = BossProfile.createManual(
            cleanName,
            normalize(category, "Custom"),
            normalize(subcategory, "Uncategorized"),
            npcIds,
            notes);

        data.getBosses().add(boss);
        queueSave();
        return boss;
    }

    public synchronized void addDeath(String bossId)
    {
        addEvent(
            bossId,
            TrackerEventType.DEATH,
            TrackerEventSource.MANUAL,
            1,
            "");
    }

    public synchronized void addAutomaticDeath(String bossId, String note)
    {
        addEvent(
            bossId,
            TrackerEventType.DEATH,
            TrackerEventSource.AUTOMATIC,
            1,
            note);
    }

    public synchronized void removeDeath(String bossId)
    {
        requireBoss(bossId);

        if (getDeathCount(bossId) <= 0)
        {
            return;
        }

        addEvent(
            bossId,
            TrackerEventType.DEATH,
            TrackerEventSource.CORRECTED,
            -1,
            "Manual death correction");
    }

    public synchronized void addKill(String bossId)
    {
        addEvent(
            bossId,
            TrackerEventType.KILL,
            TrackerEventSource.MANUAL,
            1,
            "");
    }

    public synchronized void addAutomaticKill(String bossId, String note)
    {
        addEvent(
            bossId,
            TrackerEventType.KILL,
            TrackerEventSource.AUTOMATIC,
            1,
            note);
    }

    public synchronized void removeKill(String bossId)
    {
        requireBoss(bossId);

        if (getKillCount(bossId) <= 0)
        {
            return;
        }

        addEvent(
            bossId,
            TrackerEventType.KILL,
            TrackerEventSource.CORRECTED,
            -1,
            "Manual kill correction");
    }

    private void addEvent(
        String bossId,
        TrackerEventType type,
        TrackerEventSource source,
        int amount,
        String note)
    {
        requireBoss(bossId);
        Instant now = Instant.now();

        if (source == TrackerEventSource.AUTOMATIC
            && TrackerEventPolicy.isDuplicateAutomaticEvent(
                data.getEvents(),
                bossId,
                type,
                now,
                AUTOMATIC_EVENT_DEDUP_WINDOW))
        {
            log.debug(
                "Suppressed duplicate automatic {} event for {}",
                type,
                bossId);
            return;
        }

        data.getEvents().add(TrackerEvent.createAt(
            bossId,
            type,
            source,
            amount,
            note,
            now));
        queueSave();
    }

    public synchronized Integer getCachedRuneLiteKillCount(
        BossProfile boss)
    {
        if (boss == null
            || configManager == null
            || configManager.getRSProfileKey() == null)
        {
            return null;
        }

        Integer cachedKc = null;

        for (String killcountKey :
            BossNameResolver.runeLiteKillcountKeys(boss))
        {
            Integer candidate =
                configManager.getRSProfileConfiguration(
                    "killcount",
                    killcountKey,
                    int.class);

            if (candidate != null
                && candidate >= 0
                && (cachedKc == null || candidate > cachedKc))
            {
                cachedKc = candidate;
            }
        }

        return cachedKc;
    }

    public synchronized HistoricalKillSyncResult syncRuneLiteKillCounts(
        ConfigManager sourceConfigManager)
    {
        int bossesWithCachedKc = 0;
        int bossesUpdated = 0;
        int killsImported = 0;

        if (sourceConfigManager == null
            || sourceConfigManager.getRSProfileKey() == null)
        {
            return new HistoricalKillSyncResult(
                0,
                0,
                0,
                data.getBosses().size(),
                false);
        }

        for (BossProfile boss : data.getBosses())
        {
            Integer cachedKc = null;
            String matchedKey = null;

            for (String killcountKey :
                BossNameResolver.runeLiteKillcountKeys(boss))
            {
                Integer candidate =
                    sourceConfigManager.getRSProfileConfiguration(
                        "killcount",
                        killcountKey,
                        int.class);

                if (candidate != null
                    && candidate >= 0
                    && (cachedKc == null || candidate > cachedKc))
                {
                    cachedKc = candidate;
                    matchedKey = killcountKey;
                }
            }

            if (cachedKc == null)
            {
                continue;
            }

            bossesWithCachedKc++;

            int currentKills = getKillCount(boss.getId());

            if (cachedKc > currentKills)
            {
                int delta = cachedKc - currentKills;

                data.getEvents().add(TrackerEvent.create(
                    boss.getId(),
                    TrackerEventType.KILL,
                    TrackerEventSource.IMPORTED,
                    delta,
                    "Historical KC synchronized from RuneLite killcount cache; reported KC: "
                        + cachedKc
                        + "; cache key: "
                        + matchedKey));

                bossesUpdated++;
                killsImported += delta;
            }
        }

        if (bossesUpdated > 0)
        {
            queueSave();
        }

        return new HistoricalKillSyncResult(
            bossesWithCachedKc,
            bossesUpdated,
            killsImported,
            data.getBosses().size() - bossesWithCachedKc,
            true);
    }

    public static final class HistoricalKillSyncResult
    {
        private final int bossesWithCachedKc;
        private final int bossesUpdated;
        private final int killsImported;
        private final int bossesWithoutCachedKc;
        private final boolean runeScapeProfileAvailable;

        private HistoricalKillSyncResult(
            int bossesWithCachedKc,
            int bossesUpdated,
            int killsImported,
            int bossesWithoutCachedKc,
            boolean runeScapeProfileAvailable)
        {
            this.bossesWithCachedKc = bossesWithCachedKc;
            this.bossesUpdated = bossesUpdated;
            this.killsImported = killsImported;
            this.bossesWithoutCachedKc = bossesWithoutCachedKc;
            this.runeScapeProfileAvailable = runeScapeProfileAvailable;
        }

        public int getBossesWithCachedKc()
        {
            return bossesWithCachedKc;
        }

        public int getBossesUpdated()
        {
            return bossesUpdated;
        }

        public int getKillsImported()
        {
            return killsImported;
        }

        public int getBossesWithoutCachedKc()
        {
            return bossesWithoutCachedKc;
        }

        public boolean isRuneScapeProfileAvailable()
        {
            return runeScapeProfileAvailable;
        }
    }

    public synchronized int getDeathCount(String bossId)
    {
        return sumEvents(bossId, TrackerEventType.DEATH);
    }

    public synchronized int getKillCount(String bossId)
    {
        return sumEvents(bossId, TrackerEventType.KILL);
    }

    public synchronized BossProfile getBossWithMostDeaths()
    {
        BossProfile best = null;
        int bestDeaths = 0;

        for (BossProfile boss : data.getBosses())
        {
            int deaths = getDeathCount(boss.getId());

            if (deaths > bestDeaths)
            {
                best = boss;
                bestDeaths = deaths;
            }
            else if (deaths == bestDeaths && deaths > 0 && best != null
                && boss.getDisplayName()
                    .compareToIgnoreCase(best.getDisplayName()) < 0)
            {
                best = boss;
            }
        }

        return best;
    }

    public synchronized BossProfile getBossWithMostKills()
    {
        BossProfile best = null;
        int bestKills = 0;

        for (BossProfile boss : data.getBosses())
        {
            int kills = getKillCount(boss.getId());

            if (kills > bestKills)
            {
                best = boss;
                bestKills = kills;
            }
            else if (kills == bestKills && kills > 0 && best != null
                && boss.getDisplayName()
                    .compareToIgnoreCase(best.getDisplayName()) < 0)
            {
                best = boss;
            }
        }

        return best;
    }

    public synchronized int getTotalDeaths()
    {
        return sumEvents(null, TrackerEventType.DEATH);
    }

    public synchronized int getTotalKills()
    {
        return sumEvents(null, TrackerEventType.KILL);
    }

    public synchronized List<TrackerEvent> getEventsForBoss(String bossId)
    {
        List<TrackerEvent> result = new ArrayList<>();

        for (TrackerEvent event : data.getEvents())
        {
            if (event.getBossId().equals(bossId))
            {
                result.add(event);
            }
        }

        result.sort(
            Comparator.comparing(TrackerEvent::getTimestamp).reversed());
        return result;
    }

    public synchronized List<TrackerEvent> getAllEvents()
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

        if (event.getEncounterId() != null)
        {
            return voidEncounter(event.getEncounterId());
        }

        data.getEvents().add(TrackerEvent.createUndo(
            event,
            "Undo history event " + event.getId()));

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

    public synchronized String beginEncounter(
        String bossId,
        String bossName,
        int npcId,
        Instant startedAt)
    {
        requireBoss(bossId);

        for (int index = data.getAttempts().size() - 1; index >= 0; index--)
        {
            EncounterAttempt existing = data.getAttempts().get(index);
            if (existing != null
                && !existing.isCompleted()
                && bossId.equals(existing.getBossId()))
            {
                return existing.getId();
            }
        }

        EncounterAttempt attempt = EncounterAttempt.start(
            bossId,
            bossName,
            npcId,
            startedAt);
        data.getAttempts().add(attempt);
        queueSave();
        return attempt.getId();
    }

    public synchronized void completeEncounter(
        String encounterId,
        EncounterOutcome outcome,
        String completionSignal,
        String note)
    {
        completeEncounter(
            encounterId,
            outcome,
            completionSignal,
            note,
            true,
            true);
    }

    public synchronized void completeEncounter(
        String encounterId,
        EncounterOutcome outcome,
        String completionSignal,
        String note,
        boolean recordKill,
        boolean recordDeath)
    {
        EncounterAttempt attempt = findAttemptLocked(encounterId);
        if (attempt == null || attempt.isCompleted())
        {
            return;
        }

        EncounterOutcome resolved = outcome == null
            ? EncounterOutcome.ABANDONED
            : outcome;
        Instant endedAt = Instant.now();
        attempt.complete(
            resolved,
            endedAt,
            completionSignal,
            note);

        BossProfile boss = requireBoss(attempt.getBossId());
        boss.markEncounterVerified();

        if (recordKill
            && resolved.countsKill()
            && !hasEncounterEventLocked(
                encounterId,
                TrackerEventType.KILL))
        {
            data.getEvents().add(TrackerEvent.createAt(
                encounterId,
                attempt.getBossId(),
                TrackerEventType.KILL,
                TrackerEventSource.AUTOMATIC,
                1,
                "Encounter outcome " + resolved
                    + "; signal: " + safe(completionSignal),
                endedAt));
            boss.markKillVerified();
        }

        if (recordDeath
            && resolved.countsDeath()
            && !hasEncounterEventLocked(
                encounterId,
                TrackerEventType.DEATH))
        {
            data.getEvents().add(TrackerEvent.createAt(
                encounterId,
                attempt.getBossId(),
                TrackerEventType.DEATH,
                TrackerEventSource.AUTOMATIC,
                1,
                "Encounter outcome " + resolved
                    + "; signal: " + safe(completionSignal),
                endedAt));
            boss.markDeathVerified();
        }

        queueSave();
    }

    public synchronized void abandonEncounter(
        String encounterId,
        EncounterOutcome outcome,
        String note)
    {
        EncounterOutcome resolved = outcome == EncounterOutcome.DISCONNECTED
            ? EncounterOutcome.DISCONNECTED
            : EncounterOutcome.ABANDONED;
        completeEncounter(
            encounterId,
            resolved,
            resolved.name(),
            note);
    }

    public synchronized List<EncounterAttempt> getAttemptsForBoss(
        String bossId)
    {
        List<EncounterAttempt> result = new ArrayList<>();

        for (EncounterAttempt attempt : data.getAttempts())
        {
            if (attempt != null && bossId.equals(attempt.getBossId()))
            {
                result.add(attempt);
            }
        }

        result.sort(Comparator.comparing(
            EncounterAttempt::getStartedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        return result;
    }

    public synchronized List<EncounterAttempt> getAllAttempts()
    {
        return new ArrayList<>(data.getAttempts());
    }

    public synchronized BossStatistics getBossStatistics(String bossId)
    {
        return BossStatistics.calculate(bossId, data.getAttempts());
    }

    public synchronized SessionStatistics getSessionStatistics(
        Instant sessionStartedAt)
    {
        return SessionStatistics.calculate(
            sessionStartedAt,
            data.getAttempts());
    }

    private EncounterAttempt findAttemptLocked(String encounterId)
    {
        if (encounterId == null)
        {
            return null;
        }

        for (EncounterAttempt attempt : data.getAttempts())
        {
            if (attempt != null && encounterId.equals(attempt.getId()))
            {
                return attempt;
            }
        }

        return null;
    }

    private boolean hasEncounterEventLocked(
        String encounterId,
        TrackerEventType type)
    {
        for (TrackerEvent event : data.getEvents())
        {
            if (event != null
                && encounterId != null
                && encounterId.equals(event.getEncounterId())
                && type == event.getType()
                && event.getAmount() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public synchronized DefinitionSyncResult synchronizeDefinitions()
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
        if (imported.getSchemaVersion() > TrackerData.CURRENT_SCHEMA_VERSION)
        {
            throw new IOException(
                "This profile was created by a newer Boss KD Tracker version.");
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

    public synchronized void markEncounterVerified(String bossId)
    {
        BossProfile boss = requireBoss(bossId);
        boss.markEncounterVerified();
        queueSave();
    }

    public synchronized void markDeathVerified(String bossId)
    {
        BossProfile boss = requireBoss(bossId);
        boss.markDeathVerified();
        queueSave();
    }

    public synchronized void markKillVerified(String bossId)
    {
        BossProfile boss = requireBoss(bossId);
        boss.markKillVerified();
        queueSave();
    }

    public synchronized void setSyncStatus(String bossId, SyncStatus status)
    {
        BossProfile boss = requireBoss(bossId);
        boss.setSyncStatus(status);
        queueSave();
    }

    public synchronized void updateBoss(
        String bossId,
        String displayName,
        String category,
        String subcategory,
        List<Integer> npcIds,
        String notes)
    {
        BossProfile boss = requireBoss(bossId);
        String cleanName = displayName == null ? "" : displayName.trim();

        if (cleanName.isEmpty())
        {
            throw new IllegalArgumentException("Boss name is required.");
        }

        for (BossProfile existing : data.getBosses())
        {
            if (!existing.getId().equals(bossId)
                && existing.getDisplayName().equalsIgnoreCase(cleanName))
            {
                throw new IllegalArgumentException(
                    "A boss with that name already exists.");
            }
        }

        boss.setDisplayName(cleanName);
        boss.setCategory(normalize(category, "Custom"));
        boss.setSubcategory(normalize(subcategory, "Uncategorized"));
        boss.setNpcIds(npcIds);
        boss.setNotes(notes);
        queueSave();
    }

    public synchronized void deleteBoss(String bossId)
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

    public synchronized int seedBuiltInDefinitions()
    {
        int added = seedBuiltInDefinitionsLocked();

        if (added > 0)
        {
            queueSave();
        }

        return added;
    }

    private int seedBuiltInDefinitionsLocked()
    {
        int added = 0;

        for (BossDefinition definition : BossDefinitionRegistry.getAll())
        {
            boolean exists = data.getBosses()
                .stream()
                .anyMatch(boss ->
                    definition.getKey().equals(boss.getDefinitionKey())
                    || boss.getDisplayName().equalsIgnoreCase(
                        definition.getDisplayName()));

            if (!exists)
            {
                data.getBosses().add(
                    BossProfile.createFromDefinition(definition));
                added++;
            }
        }

        return added;
    }

    public void flush()
    {
        final Path file;
        final String json;

        synchronized (this)
        {
            if (!loaded || activeDataFile == null)
            {
                return;
            }

            if (pendingSave != null)
            {
                pendingSave.cancel(false);
                pendingSave = null;
            }

            data.migrateToCurrent();
            file = activeDataFile;
            json = gson.toJson(data);
        }

        writeSnapshot(file, json);
    }

    public void shutdown()
    {
        flush();

        ScheduledExecutorService executor;

        synchronized (this)
        {
            executor = ioExecutor;
            ioExecutor = null;
            pendingSave = null;
        }

        if (executor == null)
        {
            return;
        }

        executor.shutdown();

        try
        {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS))
            {
                executor.shutdownNow();
            }
        }
        catch (InterruptedException ex)
        {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }

    private void switchProfile(
        String storageKey,
        Path targetFile)
        throws IOException
    {
        synchronized (this)
        {
            if (loaded && storageKey.equals(activeStorageKey))
            {
                return;
            }
        }

        flush();
        Files.createDirectories(DATA_DIR);

        boolean migrated = migrateLegacyIfNeeded(storageKey, targetFile);
        LoadResult loadResult = loadWithRecovery(targetFile);

        boolean staleAttemptsClosed;

        synchronized (this)
        {
            data = loadResult.data;
            data.migrateToCurrent();
            activeStorageKey = storageKey;
            activeDataFile = targetFile;
            loaded = true;
            seedBuiltInDefinitionsLocked();
            staleAttemptsClosed = closeStaleAttemptsLocked();
        }

        if (migrated
            || loadResult.recovered
            || !loadResult.existed
            || staleAttemptsClosed)
        {
            flush();
        }

        log.info(
            "Loaded Boss Death Tracker storage profile {}",
            storageKey);
    }

    private boolean migrateLegacyIfNeeded(
        String storageKey,
        Path targetFile)
        throws IOException
    {
        if ("legacy".equals(storageKey)
            || Files.exists(targetFile)
            || Files.exists(LEGACY_MIGRATION_MARKER)
            || !Files.exists(LEGACY_DATA_FILE))
        {
            return false;
        }

        Files.createDirectories(targetFile.getParent());
        Files.copy(
            LEGACY_DATA_FILE,
            targetFile,
            StandardCopyOption.REPLACE_EXISTING);

        Path migratedBackup =
            DATA_DIR.resolve("tracker-data.legacy-migrated.json");
        Files.move(
            LEGACY_DATA_FILE,
            migratedBackup,
            StandardCopyOption.REPLACE_EXISTING);

        Files.writeString(
            LEGACY_MIGRATION_MARKER,
            storageKey,
            StandardCharsets.UTF_8);

        log.info(
            "Migrated legacy Boss Death Tracker data into profile {}",
            storageKey);
        return true;
    }

    private LoadResult loadWithRecovery(Path targetFile)
        throws IOException
    {
        boolean existed = Files.exists(targetFile);
        IOException failure = null;

        for (int index = 0; index <= BACKUP_COUNT; index++)
        {
            Path candidate =
                index == 0 ? targetFile : backupPath(targetFile, index);

            if (!Files.exists(candidate))
            {
                continue;
            }

            try
            {
                String json =
                    Files.readString(candidate, StandardCharsets.UTF_8);
                TrackerData loadedData =
                    gson.fromJson(json, TrackerData.class);

                if (loadedData == null)
                {
                    throw new IOException(
                        "Tracker data was empty: " + candidate);
                }

                loadedData.migrateToCurrent();

                if (index > 0)
                {
                    preserveCorruptPrimary(targetFile);
                    Files.copy(
                        candidate,
                        targetFile,
                        StandardCopyOption.REPLACE_EXISTING);
                    log.warn(
                        "Recovered Boss Death Tracker data from backup {}",
                        candidate.getFileName());
                }

                return new LoadResult(
                    loadedData,
                    index > 0,
                    existed);
            }
            catch (Exception ex)
            {
                failure = new IOException(
                    "Unable to read tracker data from " + candidate,
                    ex);
                log.warn(
                    "Unable to read Boss Death Tracker data from {}",
                    candidate,
                    ex);
            }
        }

        if (existed && failure != null)
        {
            preserveCorruptPrimary(targetFile);
            log.warn(
                "All Boss Death Tracker backups were unreadable; "
                    + "starting a clean profile");
        }

        return new LoadResult(
            new TrackerData(),
            failure != null,
            existed);
    }

    private boolean closeStaleAttemptsLocked()
    {
        boolean changed = false;
        Instant now = Instant.now();

        for (EncounterAttempt attempt : data.getAttempts())
        {
            if (attempt != null && !attempt.isCompleted())
            {
                attempt.complete(
                    EncounterOutcome.DISCONNECTED,
                    now,
                    "PROFILE_RELOAD",
                    "Recovered unfinished attempt after reload");
                changed = true;
            }
        }

        return changed;
    }

    private void preserveCorruptPrimary(Path targetFile)
        throws IOException
    {
        if (!Files.exists(targetFile))
        {
            return;
        }

        String fileName = targetFile.getFileName().toString();
        Path corruptFile = targetFile.resolveSibling(
            fileName + ".corrupt-" + System.currentTimeMillis());

        Files.move(
            targetFile,
            corruptFile,
            StandardCopyOption.REPLACE_EXISTING);
    }

    private synchronized BossProfile requireBoss(String bossId)
    {
        return findBoss(bossId)
            .orElseThrow(() ->
                new IllegalArgumentException("Boss not found: " + bossId));
    }

    private synchronized int sumEvents(
        String bossId,
        TrackerEventType type)
    {
        int total = 0;

        for (TrackerEvent event : data.getEvents())
        {
            if (event.getType() != type)
            {
                continue;
            }

            if (bossId != null && !event.getBossId().equals(bossId))
            {
                continue;
            }

            total += event.getAmount();
        }

        return Math.max(0, total);
    }

    private void queueSave()
    {
        final Path file;
        final String json;

        synchronized (this)
        {
            if (!loaded || activeDataFile == null)
            {
                return;
            }

            data.migrateToCurrent();
            file = activeDataFile;
            json = gson.toJson(data);

            if (pendingSave != null)
            {
                pendingSave.cancel(false);
            }

            pendingSave = ensureExecutor().schedule(
                () -> writeSnapshot(file, json),
                SAVE_DELAY_MILLIS,
                TimeUnit.MILLISECONDS);
        }
    }

    private void writeSnapshot(Path file, String json)
    {
        synchronized (ioLock)
        {
            try
            {
                Files.createDirectories(file.getParent());
                rotateBackups(file);

                Path tempFile = file.resolveSibling(
                    file.getFileName().toString() + ".tmp");

                Files.writeString(
                    tempFile,
                    json,
                    StandardCharsets.UTF_8);

                try
                {
                    Files.move(
                        tempFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException atomicMoveFailure)
                {
                    Files.move(
                        tempFile,
                        file,
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
            catch (IOException ex)
            {
                log.warn(
                    "Unable to save Boss Death Tracker data to {}",
                    file,
                    ex);
            }
        }
    }

    private void rotateBackups(Path file)
        throws IOException
    {
        if (!Files.exists(file))
        {
            return;
        }

        Files.deleteIfExists(backupPath(file, BACKUP_COUNT));

        for (int index = BACKUP_COUNT - 1; index >= 1; index--)
        {
            Path source = backupPath(file, index);
            if (Files.exists(source))
            {
                Files.move(
                    source,
                    backupPath(file, index + 1),
                    StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.copy(
            file,
            backupPath(file, 1),
            StandardCopyOption.REPLACE_EXISTING);
    }

    private static Path backupPath(Path file, int index)
    {
        return file.resolveSibling(
            file.getFileName().toString() + ".bak" + index);
    }

    private static Path resolveDataFile(String storageKey)
    {
        if ("legacy".equals(storageKey))
        {
            return LEGACY_DATA_FILE;
        }

        return PROFILES_DIR
            .resolve(storageKey)
            .resolve("tracker-data.json");
    }

    private synchronized ScheduledExecutorService ensureExecutor()
    {
        if (ioExecutor == null || ioExecutor.isShutdown())
        {
            ioExecutor = Executors.newSingleThreadScheduledExecutor(r ->
            {
                Thread thread =
                    new Thread(r, "boss-death-tracker-storage");
                thread.setDaemon(true);
                return thread;
            });
        }

        return ioExecutor;
    }

    private static String normalize(String value, String fallback)
    {
        if (value == null || value.trim().isEmpty())
        {
            return fallback;
        }

        return value.trim();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    public static final class UndoResult
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

    private static final class LoadResult
    {
        private final TrackerData data;
        private final boolean recovered;
        private final boolean existed;

        private LoadResult(
            TrackerData data,
            boolean recovered,
            boolean existed)
        {
            this.data = data;
            this.recovered = recovered;
            this.existed = existed;
        }
    }
}
