package com.bossdeathtracker;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.events.ChatInput;
import net.runelite.client.util.Text;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class LocalKdCommandTest
{
    @Mock private BossDeathTrackerStore store;
    @Mock private ChatMessageManager chatMessageManager;
    @Mock private Client client;
    @Mock private ChatInput chatInput;
    @InjectMocks private BossDeathTrackerPlugin plugin;

    private Method inputHandler;

    @Before
    public void setUp() throws Exception
    {
        // Reflection is confined to tests; the shipped plugin does not use it.
        inputHandler = BossDeathTrackerPlugin.class.getDeclaredMethod(
            "handleKdCommandInput", ChatInput.class, String.class);
        inputHandler.setAccessible(true);
    }

    @Test
    public void shorthandQueuesOneLocalResultAndConsumesInput() throws Exception
    {
        BossProfile boss = boss("the-whisperer", "The Whisperer");
        when(store.getBosses()).thenReturn(Collections.singletonList(boss));
        when(store.getKillCount(boss.getId())).thenReturn(100);
        when(store.getDeathCount(boss.getId())).thenReturn(5);

        assertLocal("!kd whisp", "Whisperer - Kills: 100 | Deaths: 5 | K/D: 20.00");
    }

    @Test
    public void missingQueryIsConsumedWithLocalUsage() throws Exception
    {
        assertLocal("!KD", "Boss KD Tracker: Usage: !KD [boss name]");
        verifyNoInteractions(store);
    }

    @Test
    public void unknownBossIsConsumedWithLocalError() throws Exception
    {
        when(store.getBosses()).thenReturn(Collections.emptyList());
        assertLocal("!kd missing", "Boss KD Tracker: Boss not found.");
    }

    @Test
    public void ambiguousNameDoesNotGuessOrSend() throws Exception
    {
        when(store.getBosses()).thenReturn(Arrays.asList(
            boss("lord-drakan", "Lord Lowerniel Drakan"),
            boss("vanstrom-drakan", "Vanstrom Drakan")));
        assertLocal("!kd drakan",
            "Boss KD Tracker: Multiple matches: Lord Lowerniel Drakan, Vanstrom Drakan");
    }

    @Test
    public void zeroDeathsDisplaysPerfect() throws Exception
    {
        BossProfile boss = boss("vorkath", "Vorkath");
        when(store.getBosses()).thenReturn(Collections.singletonList(boss));
        when(store.getKillCount(boss.getId())).thenReturn(3);
        assertLocal("!KD vork", "Vorkath - Kills: 3 | Deaths: 0 | K/D: Perfect");
    }

    @Test
    public void emptyRecordDisplaysZeroRatio() throws Exception
    {
        when(store.getBosses()).thenReturn(Collections.singletonList(boss("vorkath", "Vorkath")));
        assertLocal("!KD Vorkath", "Vorkath - Kills: 0 | Deaths: 0 | K/D: 0.00");
    }

    private void assertLocal(String command, String expected) throws Exception
    {
        assertEquals("RuneLite must consume the command", Boolean.TRUE,
            inputHandler.invoke(plugin, chatInput, command));
        ArgumentCaptor<QueuedMessage> result = ArgumentCaptor.forClass(QueuedMessage.class);
        verify(chatMessageManager).queue(result.capture());
        assertEquals(ChatMessageType.CONSOLE, result.getValue().getType());
        assertEquals(expected, Text.removeTags(result.getValue().getRuneLiteFormattedMessage()));
        verifyNoMoreInteractions(chatMessageManager);
        // In particular, no ChatInput.resume(), outgoing-message change, or client script.
        verifyNoInteractions(chatInput, client);
    }

    private static BossProfile boss(String key, String name)
    {
        return BossProfile.createFromDefinition(new BossDefinition(key, name, "Bosses", ""));
    }
}
