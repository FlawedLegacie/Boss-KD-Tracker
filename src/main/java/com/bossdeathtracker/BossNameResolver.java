package com.bossdeathtracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Resolves player-friendly boss names and common shorthand to tracker profiles.
 *
 * The resolver intentionally prefers explicit aliases and exact normalized
 * names before falling back to unique partial-name matches. Ambiguous partial
 * matches are returned as multiple candidates instead of guessing.
 */
final class BossNameResolver
{
    private static final Map<String, String> ALIASES;

    static
    {
        Map<String, String> aliases = new LinkedHashMap<>();

        // Classic / general bosses.
        alias(aliases, "kbd", "king-black-dragon");
        alias(aliases, "kq", "kalphite-queen");
        alias(aliases, "corp", "corporeal-beast");
        alias(aliases, "thermy", "thermonuclear-smoke-devil");
        alias(aliases, "cerb", "cerberus");
        alias(aliases, "sire", "abyssal-sire");
        alias(aliases, "hydra", "alchemical-hydra");
        alias(aliases, "mole", "giant-mole");
        alias(aliases, "muspah", "phantom-muspah");

        // God Wars Dungeon.
        alias(aliases, "kree", "kreearra");
        alias(aliases, "zily", "commander-zilyana");
        alias(aliases, "zilyana", "commander-zilyana");
        alias(aliases, "bandos", "general-graardor");
        alias(aliases, "graardor", "general-graardor");
        alias(aliases, "kril", "kril-tsutsaroth");
        alias(aliases, "zammy", "kril-tsutsaroth");

        // Raids / minigame shorthand.
        alias(aliases, "olm", "great-olm");
        alias(aliases, "bloat", "pestilent-bloat");
        alias(aliases, "maiden", "maiden");
        alias(aliases, "nylo", "nylocas-vasilias");
        alias(aliases, "verzik", "verzik-vitur");
        alias(aliases, "pnm", "phosanis-nightmare");
        alias(aliases, "phosani", "phosanis-nightmare");
        alias(aliases, "nightmare", "the-nightmare");
        alias(aliases, "baba", "baba");
        alias(aliases, "cg", "corrupted-hunllef");
        alias(aliases, "jad", "tztok-jad");

        // Desert Treasure II.
        alias(aliases, "wisp", "the-whisperer");
        alias(aliases, "whisp", "the-whisperer");
        alias(aliases, "whisperer", "the-whisperer");
        alias(aliases, "levi", "the-leviathan");
        alias(aliases, "leviathan", "the-leviathan");
        alias(aliases, "vard", "vardorvis");
        alias(aliases, "duke", "duke-sucellus");

        // Wilderness / Varlamore shorthand.
        alias(aliases, "calv", "calvarion");
        alias(aliases, "chaos ele", "chaos-elemental");
        alias(aliases, "crazy arch", "crazy-archaeologist");
        alias(aliases, "deranged arch", "deranged-archaeologist");
        alias(aliases, "huey", "the-hueycoatl");
        alias(aliases, "hueycoatl", "the-hueycoatl");
        alias(aliases, "arax", "araxxor");

        ALIASES = Collections.unmodifiableMap(aliases);
    }

    private BossNameResolver()
    {
    }

    static List<BossProfile> resolve(String query, List<BossProfile> bosses)
    {
        if (query == null || query.trim().isEmpty() || bosses == null)
        {
            return Collections.emptyList();
        }

        String normalizedQuery = normalize(query);
        String compactQuery = compact(query);

        if (compactQuery.isEmpty())
        {
            return Collections.emptyList();
        }

        String aliasTarget = ALIASES.get(normalizedQuery);
        if (aliasTarget != null)
        {
            for (BossProfile boss : bosses)
            {
                if (aliasTarget.equals(boss.getDefinitionKey()))
                {
                    return Collections.singletonList(boss);
                }
            }
        }

        List<BossProfile> exact = new ArrayList<>();

        for (BossProfile boss : bosses)
        {
            String displayName = safe(boss.getDisplayName());
            String definitionKey = safe(boss.getDefinitionKey());

            if (compact(displayName).equals(compactQuery)
                || compact(stripLeadingThe(displayName)).equals(compactQuery)
                || compact(definitionKey).equals(compactQuery))
            {
                exact.add(boss);
            }
        }

        if (!exact.isEmpty())
        {
            sort(exact);
            return exact;
        }

        // Only attempt abbreviation/partial matching on meaningful input.
        // Shorter input is too likely to collide with several boss names.
        if (compactQuery.length() < 3)
        {
            return Collections.emptyList();
        }

        Set<BossProfile> partial = new LinkedHashSet<>();

        for (BossProfile boss : bosses)
        {
            String displayName = stripLeadingThe(safe(boss.getDisplayName()));
            String compactDisplay = compact(displayName);
            String compactKey = compact(safe(boss.getDefinitionKey()));

            if (compactDisplay.startsWith(compactQuery)
                || compactDisplay.contains(compactQuery)
                || compactKey.startsWith(compactQuery)
                || compactKey.contains(compactQuery)
                || initials(displayName).equals(compactQuery))
            {
                partial.add(boss);
            }
        }

        List<BossProfile> result = new ArrayList<>(partial);
        sort(result);
        return result;
    }

    static List<String> runeLiteKillcountKeys(BossProfile boss)
    {
        if (boss == null)
        {
            return Collections.emptyList();
        }

        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addKillcountKey(keys, boss.getDisplayName());
        addKillcountKey(keys, stripLeadingThe(boss.getDisplayName()));

        return new ArrayList<>(keys);
    }

    static String chatDisplayName(BossProfile boss)
    {
        if (boss == null)
        {
            return "Boss";
        }

        return stripLeadingThe(boss.getDisplayName());
    }

    static String formatKdRatio(int kills, int deaths)
    {
        if (deaths <= 0)
        {
            return kills <= 0 ? "0.00" : "Perfect";
        }

        return String.format(Locale.ROOT, "%.2f", (double) kills / deaths);
    }

    private static void alias(Map<String, String> aliases, String alias, String definitionKey)
    {
        aliases.put(normalize(alias), definitionKey);
    }

    private static void addKillcountKey(Set<String> keys, String value)
    {
        if (value == null)
        {
            return;
        }

        String key = value.trim().toLowerCase(Locale.ROOT);
        if (!key.isEmpty())
        {
            keys.add(key);
        }
    }

    private static String normalize(String value)
    {
        if (value == null)
        {
            return "";
        }

        StringBuilder out = new StringBuilder();
        boolean previousSpace = true;

        for (char c : value.trim().toLowerCase(Locale.ROOT).toCharArray())
        {
            if (Character.isLetterOrDigit(c))
            {
                out.append(c);
                previousSpace = false;
            }
            else if (!previousSpace)
            {
                out.append(' ');
                previousSpace = true;
            }
        }

        int length = out.length();
        if (length > 0 && out.charAt(length - 1) == ' ')
        {
            out.setLength(length - 1);
        }

        return out.toString();
    }

    private static String compact(String value)
    {
        return normalize(value).replace(" ", "");
    }

    private static String initials(String value)
    {
        String normalized = normalize(value);
        if (normalized.isEmpty())
        {
            return "";
        }

        StringBuilder initials = new StringBuilder();
        for (String word : normalized.split(" "))
        {
            if (word.isEmpty()
                || "the".equals(word)
                || "of".equals(word)
                || "a".equals(word)
                || "an".equals(word))
            {
                continue;
            }

            initials.append(word.charAt(0));
        }

        return initials.length() >= 3 ? initials.toString() : "";
    }

    private static String stripLeadingThe(String value)
    {
        String safe = safe(value).trim();
        return safe.regionMatches(true, 0, "The ", 0, 4)
            ? safe.substring(4).trim()
            : safe;
    }

    private static String safe(String value)
    {
        return value == null ? "" : value;
    }

    private static void sort(List<BossProfile> bosses)
    {
        bosses.sort((a, b) ->
            safe(a.getDisplayName()).compareToIgnoreCase(safe(b.getDisplayName())));
    }
}
