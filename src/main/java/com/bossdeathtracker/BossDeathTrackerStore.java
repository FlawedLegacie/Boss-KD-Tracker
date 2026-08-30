package com.bossdeathtracker;

import com.google.gson.Gson;
import com.google.inject.Inject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import net.runelite.client.RuneLite;
import net.runelite.client.config.ConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BossDeathTrackerStore
{
    private static final Logger log = LoggerFactory.getLogger(BossDeathTrackerStore.class);

    private static final Path DATA_DIR =
        RuneLite.RUNELITE_DIR.toPath().resolve("boss-death-tracker");

    private static final Path DATA_FILE = DATA_DIR.resolve("tracker-data.json");
    private static final Path TEMP_FILE = DATA_DIR.resolve("tracker-data.json.tmp");

    private final Gson gson;
    private ExecutorService ioExecutor;

    private TrackerData data = new TrackerData();

    @Inject
    public BossDeathTrackerStore(Gson gson)
    {
        this.gson = gson;
    }

    public void loadAsync(Consumer<Throwable> completion)
    {
        ensureExecutor().execute(() ->
        {
            Throwable error = null;

            try
            {
                Files.createDirectories(DATA_DIR);

                if (Files.exists(DATA_FILE))
                {
                    String json = Files.readString(DATA_FILE, StandardCharsets.UTF_8);
                    TrackerData loaded = gson.fromJson(json, TrackerData.class);

                    synchronized (this)
                    {
                        data = loaded == null ? new TrackerData() : loaded;
                        seedBuiltInDefinitionsLocked();
                    }
                }
                else
                {
                    synchronized (this)
                    {
                        seedBuiltInDefinitionsLocked();
                    }
                    queueSave();
                }
            }
            catch (Exception ex)
            {
                error = ex;
                log.warn("Unable to load Boss Death Tracker data", ex);
            }

            if (completion != null)
            {
                completion.accept(error);
            }
        });
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
                throw new IllegalArgumentException("A boss with that name already exists.");
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
        addDeath(bossId, TrackerEventSource.MANUAL, "");
    }

    public synchronized void addAutomaticDeath(String bossId, String note)
    {
        addDeath(bossId, TrackerEventSource.AUTOMATIC, note);
    }

    private synchronized void addDeath(
        String bossId,
        TrackerEventSource source,
        String note)
    {
        requireBoss(bossId);
        data.getEvents().add(TrackerEvent.create(
            bossId,
            TrackerEventType.DEATH,
            source,
            1,
            note));
        queueSave();
    }

    public synchronized void removeDeath(String bossId)
    {
        requireBoss(bossId);

        if (getDeathCount(bossId) <= 0)
        {
            return;
        }

        data.getEvents().add(TrackerEvent.create(
            bossId,
            TrackerEventType.DEATH,
            TrackerEventSource.CORRECTED,
            -1,
            "Manual death correction"));
        queueSave();
    }

    public synchronized void addKill(String bossId)
    {
        addKill(bossId, TrackerEventSource.MANUAL, "");
    }

    public synchronized void addAutomaticKill(String bossId, String note)
    {
        addKill(bossId, TrackerEventSource.AUTOMATIC, note);
    }

    private synchronized void addKill(
        String bossId,
        TrackerEventSource source,
        String note)
    {
        requireBoss(bossId);
        data.getEvents().add(TrackerEvent.create(
            bossId,
            TrackerEventType.KILL,
            source,
            1,
            note));
        queueSave();
    }

    public synchronized void removeKill(String bossId)
    {
        requireBoss(bossId);

        if (getKillCount(bossId) <= 0)
        {
            return;
        }

        data.getEvents().add(TrackerEvent.create(
            bossId,
            TrackerEventType.KILL,
            TrackerEventSource.CORRECTED,
            -1,
            "Manual kill correction"));
        queueSave();
    }

    public synchronized HistoricalKillSyncResult syncRuneLiteKillCounts(
        ConfigManager configManager)
    {
        int bossesWithCachedKc = 0;
        int bossesUpdated = 0;
        int killsImported = 0;

        if (configManager == null || configManager.getRSProfileKey() == null)
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

            for (String killcountKey : BossNameResolver.runeLiteKillcountKeys(boss))
            {
                Integer candidate = configManager.getRSProfileConfiguration(
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

            // RuneLite's cached KC is treated as a historical floor. We only
            // add the missing difference. This makes repeated synchronization
            // idempotent and never erases kills Boss Death Tracker has already
            // recorded live.
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
                && boss.getDisplayName().compareToIgnoreCase(best.getDisplayName()) < 0)
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
                && boss.getDisplayName().compareToIgnoreCase(best.getDisplayName()) < 0)
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

        result.sort(Comparator.comparing(TrackerEvent::getTimestamp).reversed());
        return result;
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
                throw new IllegalArgumentException("A boss with that name already exists.");
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
        data.getBosses().removeIf(boss -> boss.getId().equals(bossId));
        data.getEvents().removeIf(event -> event.getBossId().equals(bossId));
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
                    || boss.getDisplayName().equalsIgnoreCase(definition.getDisplayName()));

            if (!exists)
            {
                data.getBosses().add(BossProfile.createFromDefinition(definition));
                added++;
            }
        }

        return added;
    }

    public synchronized void shutdown()
    {
        if (ioExecutor != null)
        {
            ioExecutor.shutdownNow();
            ioExecutor = null;
        }
    }

    private synchronized BossProfile requireBoss(String bossId)
    {
        return findBoss(bossId)
            .orElseThrow(() -> new IllegalArgumentException("Boss not found: " + bossId));
    }

    private synchronized int sumEvents(String bossId, TrackerEventType type)
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
        final String json;

        synchronized (this)
        {
            json = gson.toJson(data);
        }

        ensureExecutor().execute(() ->
        {
            try
            {
                Files.createDirectories(DATA_DIR);
                Files.writeString(TEMP_FILE, json, StandardCharsets.UTF_8);

                try
                {
                    Files.move(
                        TEMP_FILE,
                        DATA_FILE,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException atomicMoveFailure)
                {
                    Files.move(
                        TEMP_FILE,
                        DATA_FILE,
                        StandardCopyOption.REPLACE_EXISTING);
                }
            }
            catch (IOException ex)
            {
                log.warn("Unable to save Boss Death Tracker data", ex);
            }
        });
    }

    private synchronized ExecutorService ensureExecutor()
    {
        if (ioExecutor == null || ioExecutor.isShutdown())
        {
            ioExecutor = Executors.newSingleThreadExecutor(r ->
            {
                Thread t = new Thread(r, "boss-death-tracker-storage");
                t.setDaemon(true);
                return t;
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
}
