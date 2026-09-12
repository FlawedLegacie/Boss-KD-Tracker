package com.bossdeathtracker;

import net.runelite.api.events.ChatMessage;
import net.runelite.client.events.ChatInput;

/**
 * Extension points for a future reviewed sharing implementation.
 * The production plugin is bound to DISABLED; there is no activation setting,
 * service URL, transport, or automatic approval check in this release.
 */
interface KdSharingHooks
{
    KdSharingHooks DISABLED = new KdSharingHooks()
    {
        @Override
        public boolean submit(ChatInput input, String query, String bossKey,
            String bossName, int kills, int deaths)
        {
            return false;
        }

        @Override
        public void lookup(ChatMessage event, String message)
        {
            // Incoming commands remain untouched while sharing is disabled.
        }
    };

    /**
     * Called after a local boss has been resolved. Return false to display the
     * normal local result. A future implementation may return true only when it
     * takes responsibility for consuming the input and completing the command.
     * It must preserve the exact user-entered message and provide local failure
     * feedback. No implementation may rewrite or auto-type outgoing chat text.
     */
    boolean submit(ChatInput input, String query, String bossKey,
        String bossName, int kills, int deaths);

    /**
     * A future implementation may look up a received command and format its
     * local display. It must not send a reply or expose this client's statistics
     * in response to another player's command.
     */
    void lookup(ChatMessage event, String message);
}
