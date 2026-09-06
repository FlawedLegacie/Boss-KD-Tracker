package com.bossdeathtracker;

/**
 * Small JSON model exchanged with the optional K/D share service.
 *
 * The service stores only the user's latest explicitly shared !KD result.
 * Incoming payloads are validated before they are ever rendered in chat.
 */
final class KdSharePayload
{
    private static final int MAX_TEXT_LENGTH = 80;
    private static final int MAX_COUNT = 2_000_000;

    private String name;
    private String query;
    private String bossKey;
    private String bossName;
    private int kills;
    private int deaths;
    private long timestamp;

    KdSharePayload()
    {
        // Required for Gson.
    }

    static KdSharePayload create(
        String name,
        String query,
        BossProfile boss,
        int kills,
        int deaths)
    {
        KdSharePayload payload = new KdSharePayload();
        payload.name = safe(name).trim();
        payload.query = normalizeQuery(query);
        payload.bossKey = safe(boss == null ? null : boss.getDefinitionKey()).trim();
        payload.bossName = BossNameResolver.chatDisplayName(boss);
        payload.kills = Math.max(0, kills);
        payload.deaths = Math.max(0, deaths);
        payload.timestamp = System.currentTimeMillis();
        return payload;
    }

    String getName()
    {
        return safe(name).trim();
    }

    String getQuery()
    {
        return normalizeQuery(query);
    }

    String getBossKey()
    {
        return safe(bossKey).trim();
    }

    String getBossName()
    {
        return safe(bossName).trim();
    }

    int getKills()
    {
        return kills;
    }

    int getDeaths()
    {
        return deaths;
    }

    long getTimestamp()
    {
        return timestamp;
    }

    boolean matchesQuery(String value)
    {
        return getQuery().equals(normalizeQuery(value));
    }

    boolean isValid()
    {
        String player = getName();
        String normalizedQuery = getQuery();
        String displayName = getBossName();
        String definitionKey = getBossKey();

        if (player.isEmpty()
            || normalizedQuery.isEmpty()
            || displayName.isEmpty()
            || player.length() > MAX_TEXT_LENGTH
            || normalizedQuery.length() > MAX_TEXT_LENGTH
            || displayName.length() > MAX_TEXT_LENGTH
            || definitionKey.length() > MAX_TEXT_LENGTH)
        {
            return false;
        }

        // RuneLite format messages interpret angle-bracket tags. Do not render
        // server-provided text containing them.
        if (displayName.indexOf('<') >= 0 || displayName.indexOf('>') >= 0)
        {
            return false;
        }

        return kills >= 0
            && deaths >= 0
            && kills <= MAX_COUNT
            && deaths <= MAX_COUNT;
    }

    static String normalizeQuery(String value)
    {
        if (value == null)
        {
            return "";
        }

        String trimmed = value.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder(trimmed.length());
        boolean previousSpace = false;

        for (int i = 0; i < trimmed.length(); i++)
        {
            char c = trimmed.charAt(i);
            if (Character.isWhitespace(c))
            {
                if (!previousSpace && out.length() > 0)
                {
                    out.append(' ');
                }
                previousSpace = true;
            }
            else
            {
                out.append(c);
                previousSpace = false;
            }
        }

        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ')
        {
            out.setLength(length - 1);
        }

        return out.toString();
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }
}
