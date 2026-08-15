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
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.ScriptID;
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
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ChatInput;
import net.runelite.client.events.ChatboxInput;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
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

    private static final Logger log =
        LoggerFactory.getLogger(BossDeathTrackerPlugin.class);

    @Inject
    private Client client;

    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private ChatCommandManager chatCommandManager;

    @Inject
    private ClientThread clientThread;

    @Inject
    private BossDeathTrackerStore store;

    @Inject
    private BossDeathTrackerConfig config;

    @Inject
    private ConfigManager configManager;

    private final BossEncounterTracker encounterTracker =
        new BossEncounterTracker();

    private BossDeathTrackerPanel panel;
    private NavigationButton navigationButton;
    private int gameTickCounter;
    private String lastPanelActiveBossId;
    private String pendingKdDisplayMessage;


    @Override
    protected void startUp()
    {
        encounterTracker.reset();
        gameTickCounter = 0;

        panel = new BossDeathTrackerPanel(store, config, configManager);

        navigationButton = NavigationButton.builder()
            .tooltip("Boss Death Tracker")
            .icon(createIcon())
            .priority(5)
            .panel(panel)
            .build();

        clientToolbar.addNavigation(navigationButton);

        chatCommandManager.registerCommand(
            KD_COMMAND,
            (chatMessage, message) -> { },
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
        pendingKdDisplayMessage = null;
        encounterTracker.reset();

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
    public void onChatMessage(ChatMessage event)
    {
        if (pendingKdDisplayMessage == null
            || !pendingKdDisplayMessage.equals(event.getMessage()))
        {
            return;
        }

        switch (event.getType())
        {
            case PUBLICCHAT:
            case MODCHAT:
            case FRIENDSCHAT:
            case CLAN_CHAT:
            case CLAN_GUEST_CHAT:
            case CLAN_GIM_CHAT:
                break;
            default:
                return;
        }

        String formatted = new ChatMessageBuilder()
            .append(ChatColorType.NORMAL)
            .append(event.getMessage())
            .build();

        event.getMessageNode().setRuneLiteFormatMessage(formatted);
        pendingKdDisplayMessage = null;
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
        String query = message == null ? "" : message.trim();

        if (query.length() >= KD_COMMAND.length()
            && query.regionMatches(true, 0, KD_COMMAND, 0, KD_COMMAND.length()))
        {
            query = query.substring(KD_COMMAND.length()).trim();
        }

        if (query.isEmpty())
        {
            client.addChatMessage(
                ChatMessageType.GAMEMESSAGE,
                "",
                "Boss Death Tracker: Usage: !KD <boss name>",
                null);
            return true;
        }

        List<BossProfile> matches = store.searchBosses(query);
        BossProfile selected = null;

        for (BossProfile boss : matches)
        {
            if (boss.getDisplayName().equalsIgnoreCase(query))
            {
                selected = boss;
                break;
            }
        }

        if (selected == null)
        {
            if (matches.size() == 1)
            {
                selected = matches.get(0);
            }
            else if (matches.isEmpty())
            {
                client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Boss Death Tracker: Boss not found.",
                    null);
                return true;
            }
            else
            {
                client.addChatMessage(
                    ChatMessageType.GAMEMESSAGE,
                    "",
                    "Boss Death Tracker: Multiple boss matches found.",
                    null);
                return true;
            }
        }

        int kills = store.getKillCount(selected.getId());
        int deaths = store.getDeathCount(selected.getId());

        String result =
            selected.getDisplayName()
                + " - Kills: " + kills
                + " | Deaths: " + deaths;

        // Consume !KD itself so the command is never sent to the game.
        // Then send the formatted result through RuneLite's normal CHAT_SEND
        // script as public game chat so nearby players can see it.
        if (chatInput instanceof ChatboxInput)
        {
            pendingKdDisplayMessage = result;

            ChatboxInput chatboxInput = (ChatboxInput) chatInput;
            int chatType = chatboxInput.getChatType();

            // RuneLite's ChatInputManager passes both chatType and clanTarget
            // to ScriptID.CHAT_SEND. Clan/guest-clan messages need the target
            // value from the current script stack; using -1 can suppress the
            // outgoing message.
            int clanTarget = -1;
            int intStackSize = client.getIntStackSize();
            int[] intStack = client.getIntStack();

            if (intStack != null && intStackSize > 0)
            {
                clanTarget = intStack[intStackSize - 1];
            }

            final int outgoingChatType = chatType;
            final int outgoingClanTarget = clanTarget;

            clientThread.invokeLater(() ->
                client.runScript(
                    ScriptID.CHAT_SEND,
                    result,
                    outgoingChatType,
                    outgoingClanTarget,
                    0,
                    -1));

            return true;
        }

        // Fallback for non-chatbox contexts: show it locally rather than
        // accidentally sending to an unexpected channel.
        client.addChatMessage(
            ChatMessageType.GAMEMESSAGE,
            "",
            result,
            null);

        return true;
    }

    private void syncEncounterToPanel()
    {
        String activeBossId = encounterTracker.getActiveBossId();

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
