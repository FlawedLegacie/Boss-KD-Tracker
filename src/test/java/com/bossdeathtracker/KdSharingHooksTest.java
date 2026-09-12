package com.bossdeathtracker;

import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.ChatInput;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class KdSharingHooksTest
{
    @Test
    public void disabledSubmitLeavesInputUntouchedAndRequestsLocalFallback()
    {
        ChatInput input = mock(ChatInput.class);
        assertFalse(KdSharingHooks.DISABLED.submit(input, "whisp",
            "the-whisperer", "Whisperer", 100, 5));
        verifyNoInteractions(input);
    }

    @Test
    public void disabledLookupDoesNotReadOrModifyAnotherPlayersMessage()
    {
        ChatMessage event = mock(ChatMessage.class);
        KdSharingHooks.DISABLED.lookup(event, "!kd whisp");
        verifyNoInteractions(event);
    }
}
