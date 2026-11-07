package com.bossdeathtracker;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MessageNode;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.NpcChanged;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatCommandManager;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
    name = "Boss Death Tracker",
    description = "Track boss deaths and kills with automatic death attribution and manual boss support",
    tags = {"boss", "death", "tracker", "pvm", "kills", "statistics"}
)
public class BossDeathTrackerPlugin extends Plugin
{
    private static final String KD_COMMAND = "!KD";
    private static final int RECENT_BOSS_CONTEXT_TICKS = 100;
    private static final int ENCOUNTER_RESOLUTION_DELAY_TICKS = 3;

    private static final Logger log =
        LoggerFactory.getLogger(BossDeathTrackerPlugin.class);

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ChatMessageManager chatMessageManager;

    @Inject
    private BossDeathTrackerStore store;

    @Inject
    private BossDeathTrackerConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KdShareClient kdShareClient;

    private final BossEncounterTracker encounterTracker =
        new BossEncounterTracker();

    private BossDeathTrackerPanel panel;
    private NavigationButton navigationButton;
    private int gameTickCounter;
    private String lastPanelActiveBossId;
    private String recentBossId;
    private int recentBossTick = -1;
    private final EncounterResolutionWindow encounterResolution =
        new EncounterResolutionWindow(ENCOUNTER_RESOLUTION_DELAY_TICKS);
    private String activeEncounterId;
    private String activeEncounterBossId;
    private Integer activeEncounterStartingKc;
    private BossEncounterTracker.Attribution pendingAttribution;
    private Instant sessionStartedAt;
    private volatile KdSharePayload lastLocalShare;

    @Override
    protected void startUp()
    {
        encounterTracker.reset();
        gameTickCounter = 0;
        recentBossId = null;
        recentBossTick = -1;
        resetEncounterResolutionState();
        sessionStartedAt = Instant.now();
        lastLocalShare = null;

        panel = new BossDeathTrackerPanel(store, config, configManager);
        panel.setSessionStartedAt(sessionStartedAt);

        navigationButton = NavigationButton.builder()
            .tooltip("Boss KD Tracker " + BossKdVisuals.badgeText())
            .icon(createIcon())
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navigationButton);

        // Match RuneLite's !task / !kc pattern: the input handler may consume
        // the original command while data is submitted, then resume that same
        // user-entered message. Incoming commands are handled asynchronously.
        chatCommandManager.registerCommandAsync(
            KD_COMMAND,
            this::handleKdCommandLookup,
            this::handleKdCommandInput);

        loadCurrentProfileData();
    }

    private void loadCurrentProfileData()
    {
        store.loadCurrentProfileAsync(error ->
            SwingUtilities.invokeLater(() ->
            {
                if (error != null)
                {
                    log.warn(
                        "Boss Death Tracker profile load completed with an error",
                        error);
                }

                BossDeathTrackerStore.HistoricalKillSyncResult syncResult =
                    store.syncRuneLiteKillCounts(configManager);

                if (syncResult.getBossesUpdated() > 0)
                {
                    log.info(
                        "Synchronized {} historical kills across {} bosses",
                        syncResult.getKillsImported(),
                        syncResult.getBossesUpdated());
                }

                if (panel != null)
                {
                    panel.refresh();
                }
            }));
    }

    @Override
    protected void shutDown()
    {
        chatCommandManager.unregisterCommand(KD_COMMAND);
        abandonActiveEncounter(
            EncounterOutcome.DISCONNECTED,
            "Plugin shutdown");
        encounterTracker.reset();
        recentBossId = null;
        recentBossTick = -1;
        resetEncounterResolutionState();
        lastLocalShare = null;

        if (navigationButton != null)
        {
            clientToolbar.removeNavigation(navigationButton);
            navigationButton = null;
        }

        panel = null;
        store.shutdown();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (!"killcount".equals(event.getGroup()))
        {
            return;
        }

        // During an active attempt, a KC increase is authoritative evidence for
        // the outcome resolver. Do not import that same increment separately.
        if (confirmKillCountProgress())
        {
            return;
        }

        BossDeathTrackerStore.HistoricalKillSyncResult result =
            store.syncRuneLiteKillCounts(configManager);

        if (result.getBossesUpdated() > 0)
        {
            log.debug(
                "RuneLite killcount cache changed; imported {} kills across {} bosses",
                result.getKillsImported(),
                result.getBossesUpdated());

            refreshPanel();
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event)
    {
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        encounterTracker.observeNpc(
            event.getNpc(),
            localPlayer,
            store,
            gameTickCounter);

        syncEncounterToPanel();
    }

    @Subscribe
    public void onNpcChanged(NpcChanged event)
    {
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        encounterTracker.observeNpcChanged(
            event.getNpc(),
            localPlayer,
            store,
            gameTickCounter);

        syncEncounterToPanel();
    }

    @Subscribe
    public void onInteractingChanged(InteractingChanged event)
    {
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        encounterTracker.observeInteraction(
            event.getSource(),
            event.getTarget(),
            localPlayer,
            store,
            gameTickCounter);

        syncEncounterToPanel();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        gameTickCounter++;
        confirmKillCountProgress();
        resolvePendingEncounter();
        encounterTracker.onGameTick(gameTickCounter);
        closeExpiredEncounterIfNeeded();

        // Refresh the current interaction each game tick. This handles cases
        // where the plugin was enabled after an NPC had already spawned.
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        if (localPlayer.getInteracting() instanceof NPC)
        {
            encounterTracker.observeNpc(
                (NPC) localPlayer.getInteracting(),
                localPlayer,
                store,
                gameTickCounter);

            syncEncounterToPanel();
        }
    }

    @Subscribe
    public void onActorDeath(ActorDeath event)
    {
        Player localPlayer = client.getLocalPlayer();

        if (localPlayer == null)
        {
            return;
        }

        if (event.getActor() == localPlayer)
        {
            BossEncounterTracker.Attribution attribution =
                encounterTracker.getDeathAttribution(gameTickCounter)
                    .orElse(pendingAttribution);

            if (!config.automaticDeaths())
            {
                abandonActiveEncounter(
                    EncounterOutcome.ABANDONED,
                    "Player died while automatic death tracking was disabled");
                encounterTracker.reset();
                syncEncounterToPanel();
                return;
            }

            if (attribution != null)
            {
                ensureActiveEncounter(attribution);
                pendingAttribution = attribution;
                encounterResolution.observePlayerDeath(gameTickCounter);
            }

            // Keep the attempt pending until KC and NPC-death events have had
            // time to arrive. This makes event order irrelevant.
            encounterTracker.reset();
            syncEncounterToPanel();
            return;
        }

        if (!(event.getActor() instanceof NPC)
            || !config.automaticKills())
        {
            return;
        }

        NPC deadNpc = (NPC) event.getActor();
        encounterTracker.getKillAttribution(
            deadNpc,
            store,
            gameTickCounter)
            .ifPresent(attribution ->
            {
                ensureActiveEncounter(attribution);
                pendingAttribution = attribution;
                encounterResolution.observeBossDeath(gameTickCounter);

                log.debug(
                    "Observed boss death for {} (NPC ID {}); awaiting confirmation",
                    attribution.getBossName(),
                    attribution.getNpcId());
            });
    }

    private void resolvePendingEncounter()
    {
        if (activeEncounterId == null
            || !encounterResolution.hasPendingSignals())
        {
            return;
        }

        Player localPlayer = client.getLocalPlayer();
        boolean playerAlive = localPlayer != null
            && localPlayer.getHealthRatio() != 0;

        Optional<EncounterOutcome> outcome =
            encounterResolution.resolve(gameTickCounter, playerAlive);

        if (!outcome.isPresent())
        {
            return;
        }

        String signal = encounterResolution.completionSignal();
        EncounterOutcome resolved = outcome.get();

        store.completeEncounter(
            activeEncounterId,
            resolved,
            signal,
            "Resolved after "
                + ENCOUNTER_RESOLUTION_DELAY_TICKS
                + " game ticks",
            config.automaticKills(),
            config.automaticDeaths());

        log.debug(
            "Completed encounter {} as {} using {}",
            activeEncounterId,
            resolved,
            signal);

        resetEncounterResolutionState();
        encounterTracker.reset();
        syncEncounterToPanel();
        refreshPanel();
    }

    private boolean confirmKillCountProgress()
    {
        if (!config.automaticKills()
            || activeEncounterId == null
            || activeEncounterBossId == null)
        {
            return false;
        }

        BossProfile boss = store.findBoss(activeEncounterBossId)
            .orElse(null);
        Integer cachedKc = store.getCachedRuneLiteKillCount(boss);

        if (cachedKc == null)
        {
            return false;
        }

        if (activeEncounterStartingKc == null)
        {
            activeEncounterStartingKc = cachedKc;
            return false;
        }

        if (cachedKc > activeEncounterStartingKc)
        {
            encounterResolution.confirmKillCount(gameTickCounter);
            return true;
        }

        return false;
    }

    private void ensureActiveEncounter(
        BossEncounterTracker.Attribution attribution)
    {
        if (attribution == null)
        {
            return;
        }

        if (activeEncounterId != null
            && attribution.getBossId().equals(activeEncounterBossId))
        {
            pendingAttribution = attribution;
            return;
        }

        if (activeEncounterId != null)
        {
            store.abandonEncounter(
                activeEncounterId,
                EncounterOutcome.ABANDONED,
                "A different boss encounter became active");
        }

        encounterResolution.reset();
        activeEncounterBossId = attribution.getBossId();
        activeEncounterId = store.beginEncounter(
            attribution.getBossId(),
            attribution.getBossName(),
            attribution.getNpcId(),
            Instant.now());
        pendingAttribution = attribution;

        BossProfile boss = store.findBoss(activeEncounterBossId)
            .orElse(null);
        activeEncounterStartingKc =
            store.getCachedRuneLiteKillCount(boss);
    }

    private void abandonActiveEncounter(
        EncounterOutcome outcome,
        String note)
    {
        if (activeEncounterId != null)
        {
            store.abandonEncounter(
                activeEncounterId,
                outcome,
                note);
        }

        resetEncounterResolutionState();
    }

    private void closeExpiredEncounterIfNeeded()
    {
        if (activeEncounterId != null
            && encounterTracker.getActiveBossId() == null
            && !encounterResolution.hasPendingSignals())
        {
            abandonActiveEncounter(
                EncounterOutcome.ABANDONED,
                "Encounter timed out without a terminal signal");
            syncEncounterToPanel();
            refreshPanel();
        }
    }

    private void resetEncounterResolutionState()
    {
        encounterResolution.reset();
        activeEncounterId = null;
        activeEncounterBossId = null;
        activeEncounterStartingKc = null;
        pendingAttribution = null;
    }

    private boolean handleKdCommandInput(ChatInput chatInput, String message)
    {
        String query = extractKdQuery(message);

        if (query.isEmpty())
        {
            addGameMessage("Boss KD Tracker: Usage: !KD <boss name>");
            return true;
        }

        List<BossProfile> matches =
            BossNameResolver.resolve(query, store.getBosses());

        if (matches.isEmpty())
        {
            addGameMessage("Boss KD Tracker: Boss not found.");
            return true;
        }

        BossProfile selected;

        if (matches.size() == 1)
        {
            selected = matches.get(0);
        }
        else
        {
            selected = resolveContextualMatch(matches);

            if (selected == null)
            {
                addGameMessage(
                    "Boss KD Tracker: Multiple matches: "
                        + formatMatchNames(matches));
                return true;
            }
        }

        int kills = store.getKillCount(selected.getId());
        int deaths = store.getDeathCount(selected.getId());
        String result = formatKdResult(
            BossNameResolver.chatDisplayName(selected),
            kills,
            deaths);

        // Preserve the existing local-only command unless the user explicitly
        // opts in to third-party chat sharing.
        if (!config.shareKdCommand())
        {
            addGameMessage(result);
            return true;
        }

        if (!kdShareClient.isValidBaseUrl(config.kdShareServer()))
        {
            addGameMessage(result);
            addGameMessage("Boss KD Tracker: Set a valid HTTPS K/D share server first.");
            return true;
        }

        Player localPlayer = client.getLocalPlayer();
        if (localPlayer == null || localPlayer.getName() == null)
        {
            addGameMessage(result);
            addGameMessage("Boss KD Tracker: Unable to identify the local player for sharing.");
            return true;
        }

        KdSharePayload payload = KdSharePayload.create(
            localPlayer.getName(),
            query,
            selected,
            kills,
            deaths);

        kdShareClient.submit(
            config.kdShareServer(),
            payload,
            success ->
            {
                if (success)
                {
                    // Store before resuming so our own outgoing !KD message can
                    // be rewritten immediately without another network request.
                    lastLocalShare = payload;
                    chatInput.resume();
                    return;
                }

                clientThread.invoke(() ->
                {
                    addGameMessage(result);
                    addGameMessage("Boss KD Tracker: K/D share failed; command was not sent.");
                });
            });

        // Consume the original user input while the async submission runs.
        // On success, ChatInput.resume() sends that same message normally.
        return true;
    }

    private void handleKdCommandLookup(ChatMessage chatMessage, String message)
    {
        if (!config.shareKdCommand())
        {
            return;
        }

        String query = extractKdQuery(message);
        if (query.isEmpty())
        {
            return;
        }

        String playerName = commandPlayerName(chatMessage);
        if (playerName.isEmpty())
        {
            return;
        }

        KdSharePayload localShare = lastLocalShare;
        Player localPlayer = client.getLocalPlayer();
        String localName = localPlayer == null ? null : localPlayer.getName();

        if (localShare != null
            && localName != null
            && playerName.equalsIgnoreCase(localName)
            && localShare.matchesQuery(query))
        {
            clientThread.invoke(() -> applySharedKd(
                chatMessage.getMessageNode(),
                message,
                localShare));
            return;
        }

        if (!kdShareClient.isValidBaseUrl(config.kdShareServer()))
        {
            return;
        }

        MessageNode messageNode = chatMessage.getMessageNode();
        kdShareClient.lookup(
            config.kdShareServer(),
            playerName,
            query,
            payload ->
            {
                if (payload == null)
                {
                    return;
                }

                clientThread.invoke(() -> applySharedKd(
                    messageNode,
                    message,
                    payload));
            });
    }

    private void applySharedKd(
        MessageNode messageNode,
        String originalMessage,
        KdSharePayload payload)
    {
        if (messageNode == null
            || payload == null
            || !payload.isValid()
            || originalMessage == null
            || !originalMessage.equals(messageNode.getValue()))
        {
            return;
        }

        String result = formatKdResult(
            payload.getBossName(),
            payload.getKills(),
            payload.getDeaths());

        messageNode.setRuneLiteFormatMessage(result);
        client.refreshChat();
    }

    private String commandPlayerName(ChatMessage chatMessage)
    {
        if (chatMessage.getType() == ChatMessageType.PRIVATECHATOUT)
        {
            Player localPlayer = client.getLocalPlayer();
            return localPlayer == null || localPlayer.getName() == null
                ? ""
                : localPlayer.getName();
        }

        String name = chatMessage.getName();
        if (name == null)
        {
            return "";
        }

        return Text.removeTags(name)
            .replace('\u00A0', ' ')
            .trim();
    }

    private static String extractKdQuery(String message)
    {
        String query = message == null ? "" : message.trim();

        if (query.length() >= KD_COMMAND.length()
            && query.regionMatches(true, 0, KD_COMMAND, 0, KD_COMMAND.length()))
        {
            query = query.substring(KD_COMMAND.length()).trim();
        }

        return query;
    }

    private static String formatKdResult(
        String bossName,
        int kills,
        int deaths)
    {
        return new ChatMessageBuilder()
            .append(ChatColorType.HIGHLIGHT)
            .append(bossName)
            .append(ChatColorType.NORMAL)
            .append(" - Kills: ")
            .append(ChatColorType.HIGHLIGHT)
            .append(Integer.toString(kills))
            .append(ChatColorType.NORMAL)
            .append(" | Deaths: ")
            .append(ChatColorType.HIGHLIGHT)
            .append(Integer.toString(deaths))
            .append(ChatColorType.NORMAL)
            .append(" | K/D: ")
            .append(ChatColorType.HIGHLIGHT)
            .append(BossNameResolver.formatKdRatio(kills, deaths))
            .build();
    }

    private static String formatMatchNames(List<BossProfile> matches)
    {
        StringBuilder names = new StringBuilder();
        int shown = Math.min(4, matches.size());

        for (int i = 0; i < shown; i++)
        {
            if (i > 0)
            {
                names.append(", ");
            }
            names.append(BossNameResolver.chatDisplayName(matches.get(i)));
        }

        if (matches.size() > shown)
        {
            names.append(", ...");
        }

        return names.toString();
    }

    private void addGameMessage(String message)
    {
        chatMessageManager.queue(
            QueuedMessage.builder()
                .type(ChatMessageType.CONSOLE)
                .runeLiteFormattedMessage(message)
                .build());
    }

    private BossProfile resolveContextualMatch(List<BossProfile> matches)
    {
        String activeBossId = encounterTracker.getActiveBossId();
        BossProfile activeMatch = findMatchById(matches, activeBossId);

        if (activeMatch != null)
        {
            return activeMatch;
        }

        if (recentBossId != null
            && recentBossTick >= 0
            && gameTickCounter - recentBossTick <= RECENT_BOSS_CONTEXT_TICKS)
        {
            return findMatchById(matches, recentBossId);
        }

        return null;
    }

    private static BossProfile findMatchById(
        List<BossProfile> matches,
        String bossId)
    {
        if (bossId == null)
        {
            return null;
        }

        for (BossProfile boss : matches)
        {
            if (bossId.equals(boss.getId()))
            {
                return boss;
            }
        }

        return null;
    }

    private void syncEncounterToPanel()
    {
        String activeBossId = encounterTracker.getActiveBossId();

        // Remember the live encounter continuously, so an ambiguous shorthand
        // can still resolve for a short window immediately after the fight.
        if (activeBossId != null)
        {
            recentBossId = activeBossId;
            recentBossTick = gameTickCounter;
            encounterTracker.getCurrentAttribution(gameTickCounter)
                .ifPresent(this::ensureActiveEncounter);
        }

        if (!java.util.Objects.equals(lastPanelActiveBossId, activeBossId))
        {
            lastPanelActiveBossId = activeBossId;

            if (activeBossId != null)
            {
                store.markEncounterVerified(activeBossId);
            }

            if (panel != null)
            {
                panel.setActiveBoss(activeBossId);
            }
        }
    }

    private void refreshPanel()
    {
        if (panel != null)
        {
            panel.refresh();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();

        if (state == GameState.LOGGED_IN)
        {
            sessionStartedAt = Instant.now();
            if (panel != null)
            {
                panel.setSessionStartedAt(sessionStartedAt);
            }
            loadCurrentProfileData();
            return;
        }

        if (state == GameState.LOGIN_SCREEN
            || state == GameState.HOPPING
            || state == GameState.CONNECTION_LOST)
        {
            abandonActiveEncounter(
                EncounterOutcome.DISCONNECTED,
                "Game state changed to " + state);
            encounterTracker.reset();
            recentBossId = null;
            recentBossTick = -1;
            lastLocalShare = null;
            syncEncounterToPanel();
        }
    }

    @Provides
    BossDeathTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(BossDeathTrackerConfig.class);
    }

    private static BufferedImage createIcon()
    {
        return BossKdIcons.image(BossKdIcons.Symbol.EMBLEM, 16);
    }
}
