package com.bossdeathtracker;

import com.google.inject.Provides;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
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
import net.runelite.client.chat.ChatCommandManager;
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
    private volatile KdSharePayload lastLocalShare;

    @Override
    protected void startUp()
    {
        encounterTracker.reset();
        gameTickCounter = 0;
        recentBossId = null;
        recentBossTick = -1;
        lastLocalShare = null;

        panel = new BossDeathTrackerPanel(store, config, configManager);

        navigationButton = NavigationButton.builder()
            .tooltip("Boss Death Tracker")
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

        store.loadAsync(error ->
            SwingUtilities.invokeLater(() ->
            {
                if (error != null)
                {
                    log.warn("Boss Death Tracker data load completed with an error", error);
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
        encounterTracker.reset();
        recentBossId = null;
        recentBossTick = -1;
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
        encounterTracker.onGameTick(gameTickCounter);

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

        // Local-player death attribution.
        if (event.getActor() == localPlayer)
        {
            if (!config.automaticDeaths())
            {
                encounterTracker.reset();
                return;
            }

            encounterTracker.getDeathAttribution(gameTickCounter)
                .ifPresent(attribution ->
                {
                    String note =
                        "Automatically attributed to "
                            + attribution.getBossName()
                            + " (NPC ID "
                            + attribution.getNpcId()
                            + ")";

                    store.addAutomaticDeath(
                        attribution.getBossId(),
                        note);
                    store.markDeathVerified(attribution.getBossId());

                    log.debug(
                        "Automatically recorded player death to {} (NPC ID {})",
                        attribution.getBossName(),
                        attribution.getNpcId());

                    refreshPanel();

                    // Require a new interaction before another death/kill can
                    // be attributed to this encounter.
                    encounterTracker.reset();
                    syncEncounterToPanel();
                });

            return;
        }

        // Boss kill attribution. An NPC death only counts when that NPC maps
        // to the exact boss profile currently held by the encounter tracker.
        if (event.getActor() instanceof NPC && config.automaticKills())
        {
            NPC deadNpc = (NPC) event.getActor();

            encounterTracker.getKillAttribution(
                deadNpc,
                store,
                gameTickCounter)
                .ifPresent(attribution ->
                {
                    String note =
                        "Automatically recorded boss kill for "
                            + attribution.getBossName()
                            + " (NPC ID "
                            + attribution.getNpcId()
                            + ")";

                    store.addAutomaticKill(
                        attribution.getBossId(),
                        note);
                    store.markKillVerified(attribution.getBossId());

                    log.debug(
                        "Automatically recorded boss kill for {} (NPC ID {})",
                        attribution.getBossName(),
                        attribution.getNpcId());

                    refreshPanel();

                    // A completed kill closes this encounter. Any subsequent
                    // fight must establish a fresh interaction.
                    encounterTracker.reset();
                    syncEncounterToPanel();
                });
        }
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
        return bossName
            + " - Kills: " + kills
            + " | Deaths: " + deaths
            + " | K/D: " + BossNameResolver.formatKdRatio(kills, deaths);
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
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            message,
            null);
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

        if (state == GameState.LOGIN_SCREEN
            || state == GameState.HOPPING
            || state == GameState.CONNECTION_LOST)
        {
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
        BufferedImage image =
            new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = image.createGraphics();

        try
        {
            g.setRenderingHint(
                RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);

            g.setColor(new Color(220, 220, 220));
            g.fillOval(2, 1, 12, 11);

            g.setColor(new Color(45, 45, 45));
            g.fillOval(5, 5, 2, 2);
            g.fillOval(9, 5, 2, 2);
            g.fillRect(7, 8, 2, 2);

            g.setColor(new Color(220, 220, 220));
            g.fillRect(4, 10, 8, 5);

            g.setColor(new Color(45, 45, 45));
            g.drawLine(6, 11, 6, 14);
            g.drawLine(9, 11, 9, 14);
        }
        finally
        {
            g.dispose();
        }

        return image;
    }
}
