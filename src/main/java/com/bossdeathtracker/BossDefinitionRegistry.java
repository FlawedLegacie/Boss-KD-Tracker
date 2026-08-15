package com.bossdeathtracker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Built-in boss definitions.
 *
 * NPC IDs were curated from RuneLite's current generated
 * net.runelite.api.gameval.NpcID definitions. A boss may have more than one
 * ID because RuneScape frequently changes the NPC ID between phases/forms.
 *
 * This registry intentionally stores the numeric values in the plugin data
 * model so that profiles and exports remain readable even outside RuneLite.
 */
public final class BossDefinitionRegistry
{
    private static final Map<String, BossDefinition> BY_KEY = new LinkedHashMap<>();
    private static final Map<Integer, BossDefinition> BY_NPC_ID = new LinkedHashMap<>();

    static
    {
        add("king-black-dragon", "King Black Dragon", "Bosses", "Classic", 239);
        add("corporeal-beast", "Corporeal Beast", "Bosses", "Classic", 319);
        add("thermonuclear-smoke-devil", "Thermonuclear smoke devil", "Slayer Bosses", "Slayer", 499);
        add("kraken", "Kraken", "Slayer Bosses", "Slayer", 494);
        add("kalphite-queen", "Kalphite Queen", "Bosses", "Classic", 963, 965);
        add("zulrah", "Zulrah", "Bosses", "Zul-Andra", 2042, 2043, 2044);
        add("chaos-elemental", "Chaos Elemental", "Wilderness Bosses", "Wilderness", 2054);
        add("commander-zilyana", "Commander Zilyana", "God Wars Dungeon", "Saradomin", 2205);
        add("general-graardor", "General Graardor", "God Wars Dungeon", "Bandos", 2215);
        add("dagannoth-supreme", "Dagannoth Supreme", "Bosses", "Dagannoth Kings", 2265);
        add("dagannoth-prime", "Dagannoth Prime", "Bosses", "Dagannoth Kings", 2266);
        add("dagannoth-rex", "Dagannoth Rex", "Bosses", "Dagannoth Kings", 2267);
        add("kril-tsutsaroth", "K'ril Tsutsaroth", "God Wars Dungeon", "Zamorak", 3129);
        add("tztok-jad", "TzTok-Jad", "Minigame Bosses", "Fight Caves", 3127);
        add("kreearra", "Kree'arra", "God Wars Dungeon", "Armadyl", 3162);
        add("giant-mole", "Giant Mole", "Bosses", "Classic", 5779);
        add("cerberus", "Cerberus", "Slayer Bosses", "Slayer", 5862);
        add("abyssal-sire", "Abyssal Sire", "Slayer Bosses", "Slayer", 5887, 5888, 5889, 5890, 5891, 5908);
        add("callisto", "Callisto", "Wilderness Bosses", "Wilderness", 6609);
        add("venenatis", "Venenatis", "Wilderness Bosses", "Wilderness", 6610);
        add("vetion", "Vet'ion", "Wilderness Bosses", "Wilderness", 6611, 6612);
        add("scorpia", "Scorpia", "Wilderness Bosses", "Wilderness", 6615);
        add("crazy-archaeologist", "Crazy archaeologist", "Wilderness Bosses", "Wilderness", 6618);
        add("chaos-fanatic", "Chaos Fanatic", "Wilderness Bosses", "Wilderness", 6619);
        add("skotizo", "Skotizo", "Bosses", "Kourend", 7286);
        add("obor", "Obor", "Bosses", "F2P", 7416);
        add("tekton", "Tekton", "Raids", "Chambers of Xeric", 7540, 7541, 7542, 7545);
        add("vanguard", "Vanguard", "Raids", "Chambers of Xeric", 7525, 7526, 7527, 7528, 7529);
        add("vespula", "Vespula", "Raids", "Chambers of Xeric", 7530, 7531, 7532);
        add("great-olm", "Great Olm", "Raids", "Chambers of Xeric", 7551, 7554);
        add("vasa-nistirio", "Vasa Nistirio", "Raids", "Chambers of Xeric", 7566, 7567);
        add("jaltok-jad", "JalTok-Jad", "Minigame Bosses", "Inferno", 7700, 7704);
        add("dusk", "Dusk", "Slayer Bosses", "Grotesque Guardians", 7849, 7851, 7854, 7855, 7882, 7883, 7886, 7887, 7888);
        add("dawn", "Dawn", "Slayer Bosses", "Grotesque Guardians", 7850, 7852, 7853, 7884);
        add("the-mimic", "The Mimic", "Bosses", "Treasure Trails", 7979, 8633);
        add("vorkath", "Vorkath", "Bosses", "Dragon Slayer II", 8061);
        add("bryophyta", "Bryophyta", "Bosses", "F2P", 8195);
        add("xarpus", "Xarpus", "Raids", "Theatre of Blood", 8338, 8339, 8340);
        add("nylocas-vasilias", "Nylocas Vasilias", "Raids", "Theatre of Blood", 8354, 8355, 8356, 8357);
        add("pestilent-bloat", "Pestilent Bloat", "Raids", "Theatre of Blood", 8359);
        add("maiden", "The Maiden of Sugadinti", "Raids", "Theatre of Blood", 8360, 8361, 8362, 8363);
        add("verzik-vitur", "Verzik Vitur", "Raids", "Theatre of Blood", 8370, 8371, 8372, 8373, 8374);
        add("sotetseg", "Sotetseg", "Raids", "Theatre of Blood", 8387, 8388);
        add("hespori", "Hespori", "Skilling Bosses", "Farming", 8583);
        add("alchemical-hydra", "Alchemical Hydra", "Slayer Bosses", "Slayer", 8615, 8616, 8617, 8618, 8619, 8620, 8621);
        add("sarachnis", "Sarachnis", "Bosses", "Kourend", 8713);
        add("corrupted-hunllef", "Corrupted Hunllef", "Minigame Bosses", "Corrupted Gauntlet", 9035, 9036, 9037);
        add("zalcano", "Zalcano", "Skilling Bosses", "Mining", 9049, 9050);
        add("phosanis-nightmare", "Phosani's Nightmare", "Bosses", "Morytania", 9416, 9417, 9418, 9419, 9420, 9421, 9422, 9423, 11153, 11154);
        add("the-nightmare", "The Nightmare", "Bosses", "Morytania", 9425, 9426, 9427, 9428, 9429, 9430, 9431, 9432);
        add("tempoross", "Tempoross", "Skilling Bosses", "Fishing", 10572, 10574, 10575);
        add("kephri", "Kephri", "Raids", "Tombs of Amascut", 11719, 11720, 11721);
        add("zebak", "Zebak", "Raids", "Tombs of Amascut", 11730, 11732);
        add("elidinis-warden", "Elidinis' Warden", "Raids", "Tombs of Amascut", 11748, 11753, 11754, 11755, 11761, 11763);
        add("tumekens-warden", "Tumeken's Warden", "Raids", "Tombs of Amascut", 11749, 11756, 11757, 11758, 11762, 11764);
        add("baba", "Ba-Ba", "Raids", "Tombs of Amascut", 11778, 11779, 11780);
        add("akkha", "Akkha", "Raids", "Tombs of Amascut", 11789, 11790, 11791, 11792, 11793, 11794, 11795);
        add("artio", "Artio", "Wilderness Bosses", "Wilderness", 11992);
        add("calvarion", "Calvar'ion", "Wilderness Bosses", "Wilderness", 11993, 11994, 11995);
        add("spindel", "Spindel", "Wilderness Bosses", "Wilderness", 11998);
        add("phantom-muspah", "Phantom Muspah", "Bosses", "Secrets of the North", 12077, 12078, 12079, 12080, 12082);
        add("duke-sucellus", "Duke Sucellus", "Desert Treasure II", "DT2", 12167, 12191);
        add("the-whisperer", "The Whisperer", "Desert Treasure II", "DT2", 12204, 12205);
        add("the-leviathan", "The Leviathan", "Desert Treasure II", "DT2", 12214);
        add("vardorvis", "Vardorvis", "Desert Treasure II", "DT2", 12223);
        add("branda", "Branda the Fire Queen", "Bosses", "Royal Titans", 12596);
        add("sol-heredit", "Sol Heredit", "Minigame Bosses", "Fortis Colosseum", 12821);
        add("araxxor", "Araxxor", "Slayer Bosses", "Slayer", 13668);
        add("amoxliatl", "Amoxliatl", "Bosses", "Varlamore", 13685);
        add("eldric", "Eldric the Ice King", "Bosses", "Royal Titans", 14147);
        add("the-hueycoatl", "The Hueycoatl", "Bosses", "Varlamore", 14009, 14011, 14013);
        add("yama", "Yama", "Bosses", "Varlamore", 14176);
        add("doom-of-mokhaiotl", "Doom of Mokhaiotl", "Bosses", "Varlamore", 14707, 14708, 14709);
        add("scurrius", "Scurrius", "Bosses", "Varrock Sewers", 7221, 7222);
        add("deranged-archaeologist", "Deranged archaeologist", "Bosses", "Fossil Island", 7806);

        // Quest bosses. These are intentionally separate from repeatable boss
        // definitions so one-off quest encounters can still track attempts,
        // deaths, and the completion kill.
        add("elvarg", "Elvarg", "Quest Bosses", "Dragon Slayer I", 817);
        add("jungle-demon", "Jungle Demon", "Quest Bosses", "Monkey Madness I", 1443);
        add("count-draynor", "Count Draynor", "Quest Bosses", "Vampyre Slayer", 3481, 3482, 16278);
        add("delrith", "Delrith", "Quest Bosses", "Demon Slayer", 5079, 5080);
        add("glough", "Glough", "Quest Bosses", "Monkey Madness II", 7100, 7101, 7102, 7103);
        add("galvek", "Galvek", "Quest Bosses", "Dragon Slayer II", 8094, 8095, 8096, 8097, 8098);
        add("ranis-drakan", "Ranis Drakan", "Quest Bosses", "A Taste of Hope", 8241, 8242, 8243, 8244, 8245);
        add("fragment-of-seren", "Fragment of Seren", "Quest Bosses", "Song of the Elves", 8917, 8918, 8919, 8920);
        add("the-jormungand", "The Jormungand", "Quest Bosses", "The Fremennik Exiles", 9289, 9290, 9291, 9292);
        add("vanstrom-klause", "Vanstrom Klause", "Quest Bosses", "Sins of the Father", 9566, 9567, 9568, 9569, 9570, 9571);
        add("balance-elemental", "Balance Elemental", "Quest Bosses", "While Guthix Sleeps", 13528, 13529, 13530);
        add("lowerniel-drakan", "Lord Lowerniel Drakan", "Quest Bosses", "The Blood Moon Rises",
            15919, 15920, 15921, 15922, 15923, 15924, 15925,
            15929, 15930, 15931, 15932, 15933, 15934, 15935, 15936,
            16204, 16209, 16210, 16211);

    }

    private BossDefinitionRegistry()
    {
    }

    private static void add(
        String key,
        String displayName,
        String category,
        String subcategory,
        int... npcIds)
    {
        BossDefinition definition =
            new BossDefinition(key, displayName, category, subcategory, npcIds);

        BY_KEY.put(key, definition);

        for (int npcId : npcIds)
        {
            // Keep the first registration if an ID is ever intentionally shared.
            BY_NPC_ID.putIfAbsent(npcId, definition);
        }
    }

    public static List<BossDefinition> getAll()
    {
        return Collections.unmodifiableList(new ArrayList<>(BY_KEY.values()));
    }

    public static Optional<BossDefinition> findByKey(String key)
    {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    public static Optional<BossDefinition> findByNpcId(int npcId)
    {
        return Optional.ofNullable(BY_NPC_ID.get(npcId));
    }
}
