from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} not found")
    return text.replace(old, new, 1)


panel_path = Path("src/main/java/com/bossdeathtracker/BossDeathTrackerPanel.java")
source = panel_path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "import java.net.URL;\nimport java.time.ZoneId;",
    "import java.net.URL;\nimport java.time.Instant;\nimport java.time.ZoneId;",
    "Instant import",
)
source = replace_once(
    source,
    """    private enum HeaderMode
    {
        CURRENT,
        NEMESIS,
        RECORDS
    }""",
    """    private enum HeaderMode
    {
        CURRENT,
        SESSION,
        NEMESIS,
        RECORDS
    }""",
    "HeaderMode",
)
source = replace_once(
    source,
    """    private final JButton currentHeaderButton = new JButton(\"Current\");
    private final JButton nemesisHeaderButton = new JButton(\"Nemesis\");
    private final JButton recordsHeaderButton = new JButton(\"Records\");
    private final JLabel insightBossValue = new JLabel(\"No recent boss\");
    private final JLabel insightKillsValue = new JLabel(\"0\");
    private final JLabel insightDeathsValue = new JLabel(\"0\");
    private final JLabel insightKdValue = new JLabel(\"0.00\");
    private HeaderMode headerMode = HeaderMode.CURRENT;
""",
    """    private final JButton currentHeaderButton = new JButton(\"Current\");
    private final JButton sessionHeaderButton = new JButton(\"Session\");
    private final JButton nemesisHeaderButton = new JButton(\"Nemesis\");
    private final JButton recordsHeaderButton = new JButton(\"Records\");
    private final JLabel insightBossValue = new JLabel(\"No recent boss\");
    private final JLabel insightKillsValue = new JLabel(\"0\");
    private final JLabel insightDeathsValue = new JLabel(\"0\");
    private final JLabel insightKdValue = new JLabel(\"0.00\");
    private final JLabel insightAttemptsValue = new JLabel(\"0\");
    private final JLabel insightWinRateValue = new JLabel(\"0.0%\");
    private final JLabel insightPbValue = new JLabel(\"--\");
    private final JLabel insightAverageValue = new JLabel(\"--\");
    private final JLabel insightStreakValue = new JLabel(\"0\");
    private HeaderMode headerMode = HeaderMode.CURRENT;
""",
    "metric fields",
)
source = replace_once(
    source,
    """    private String activeBossId;
    private String mostRecentBossId;
""",
    """    private String activeBossId;
    private String mostRecentBossId;
    private Instant sessionStartedAt = Instant.now();
""",
    "session field",
)
source = replace_once(
    source,
    """    public void refresh()
    {
""",
    """    public void setSessionStartedAt(Instant sessionStartedAt)
    {
        this.sessionStartedAt = sessionStartedAt == null
            ? Instant.now()
            : sessionStartedAt;
        refresh();
    }

    public void refresh()
    {
""",
    "session setter",
)
source = replace_once(
    source,
    "new GridLayout(1, 3, 3, 0)",
    "new GridLayout(1, 4, 3, 0)",
    "header grid",
)
source = replace_once(
    source,
    """        configureHeaderButton(currentHeaderButton, HeaderMode.CURRENT);
        configureHeaderButton(nemesisHeaderButton, HeaderMode.NEMESIS);
        configureHeaderButton(recordsHeaderButton, HeaderMode.RECORDS);

        modeButtons.add(currentHeaderButton);
        modeButtons.add(nemesisHeaderButton);
        modeButtons.add(recordsHeaderButton);
""",
    """        configureHeaderButton(currentHeaderButton, HeaderMode.CURRENT);
        configureHeaderButton(sessionHeaderButton, HeaderMode.SESSION);
        configureHeaderButton(nemesisHeaderButton, HeaderMode.NEMESIS);
        configureHeaderButton(recordsHeaderButton, HeaderMode.RECORDS);

        modeButtons.add(currentHeaderButton);
        modeButtons.add(sessionHeaderButton);
        modeButtons.add(nemesisHeaderButton);
        modeButtons.add(recordsHeaderButton);
""",
    "header buttons",
)
source = replace_once(
    source,
    """        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(chartIcon, \"K/D\", insightKdValue));
""",
    """        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(chartIcon, \"K/D\", insightKdValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(detailsIcon, \"Attempts\", insightAttemptsValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(trophyIcon, \"Win rate\", insightWinRateValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(trophyIcon, \"Personal best\", insightPbValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(chartIcon, \"Average kill\", insightAverageValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(swordIcon, \"Current streak\", insightStreakValue));
""",
    "summary rows",
)
source = replace_once(
    source,
    """        else if (mode == HeaderMode.NEMESIS)
        {
            button.setIcon(skullIcon);
            button.setToolTipText(\"Boss with the most tracked deaths\");
        }
        else
        {
            button.setIcon(trophyIcon);
            button.setToolTipText(\"Boss with your highest kill count\");
        }
""",
    """        else if (mode == HeaderMode.SESSION)
        {
            button.setIcon(chartIcon);
            button.setToolTipText(\"Statistics since this login session began\");
        }
        else if (mode == HeaderMode.NEMESIS)
        {
            button.setIcon(skullIcon);
            button.setToolTipText(\"Boss with the most tracked deaths\");
        }
        else
        {
            button.setIcon(trophyIcon);
            button.setToolTipText(\"Boss with your highest kill count\");
        }
""",
    "header styles",
)
source = replace_once(
    source,
    """        styleModeButton(currentHeaderButton, headerMode == HeaderMode.CURRENT);
        styleModeButton(nemesisHeaderButton, headerMode == HeaderMode.NEMESIS);
        styleModeButton(recordsHeaderButton, headerMode == HeaderMode.RECORDS);
""",
    """        styleModeButton(currentHeaderButton, headerMode == HeaderMode.CURRENT);
        styleModeButton(sessionHeaderButton, headerMode == HeaderMode.SESSION);
        styleModeButton(nemesisHeaderButton, headerMode == HeaderMode.NEMESIS);
        styleModeButton(recordsHeaderButton, headerMode == HeaderMode.RECORDS);
""",
    "header state",
)

insight_start = source.index("    private void updateInsightSummary()")
insight_end = source.index("    private JPanel buildSearch()", insight_start)
insight_method = """    private void updateInsightSummary()
    {
        updateHeaderButtonState();

        if (headerMode == HeaderMode.SESSION)
        {
            SessionStatistics session =
                store.getSessionStatistics(sessionStartedAt);

            insightBossValue.setText(
                \"Current Session (\"
                    + formatDuration(session.getElapsedMillis(Instant.now()))
                    + \")\");
            setSummaryValues(
                session.getKills(),
                session.getDeaths(),
                session.getAttempts(),
                session.getWinRate(),
                session.getPersonalBestMillis(),
                session.getAverageKillMillis(),
                session.getCurrentStreak());
            return;
        }

        BossProfile boss = null;

        switch (headerMode)
        {
            case NEMESIS:
                boss = store.getBossWithMostDeaths();
                break;
            case RECORDS:
                boss = store.getBossWithMostKills();
                break;
            case CURRENT:
            default:
                String bossId = activeBossId != null
                    ? activeBossId
                    : mostRecentBossId;
                if (bossId != null)
                {
                    boss = store.findBoss(bossId).orElse(null);
                }
                break;
        }

        if (boss == null)
        {
            if (headerMode == HeaderMode.NEMESIS)
            {
                insightBossValue.setText(\"No tracked deaths yet\");
            }
            else if (headerMode == HeaderMode.RECORDS)
            {
                insightBossValue.setText(\"No boss kills yet\");
            }
            else
            {
                insightBossValue.setText(\"No recent boss\");
            }

            setSummaryValues(0, 0, 0, 0.0, 0L, 0L, 0);
            return;
        }

        int kills = store.getKillCount(boss.getId());
        int deaths = store.getDeathCount(boss.getId());
        BossStatistics statistics = store.getBossStatistics(boss.getId());

        insightBossValue.setText(boss.getDisplayName());
        setSummaryValues(
            kills,
            deaths,
            statistics.getAttempts(),
            statistics.getWinRate(),
            statistics.getPersonalBestMillis(),
            statistics.getAverageKillMillis(),
            statistics.getCurrentStreak());
    }

    private void setSummaryValues(
        int kills,
        int deaths,
        int attempts,
        double winRate,
        long personalBestMillis,
        long averageKillMillis,
        int currentStreak)
    {
        insightKillsValue.setText(Integer.toString(kills));
        insightDeathsValue.setText(Integer.toString(deaths));
        insightKdValue.setText(formatRatio(kills, deaths));
        insightAttemptsValue.setText(Integer.toString(attempts));
        insightWinRateValue.setText(formatPercent(winRate));
        insightPbValue.setText(formatDuration(personalBestMillis));
        insightAverageValue.setText(formatDuration(averageKillMillis));
        insightStreakValue.setText(Integer.toString(currentStreak));
    }

    private static String formatRatio(int kills, int deaths)
    {
        if (deaths == 0)
        {
            return kills > 0 ? \"∞\" : \"0.00\";
        }

        return String.format(
            Locale.ROOT,
            \"%.2f\",
            (double) kills / (double) deaths);
    }

    private static String formatPercent(double value)
    {
        return String.format(Locale.ROOT, \"%.1f%%\", value * 100.0);
    }

    private static String formatDuration(long millis)
    {
        if (millis <= 0L)
        {
            return \"--\";
        }

        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        if (hours > 0L)
        {
            return String.format(
                Locale.ROOT,
                \"%d:%02d:%02d\",
                hours,
                minutes,
                seconds);
        }

        return String.format(Locale.ROOT, \"%d:%02d\", minutes, seconds);
    }

"""
source = source[:insight_start] + insight_method + source[insight_end:]

source = replace_once(
    source,
    "card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 154));",
    "card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 180));",
    "card height",
)
source = replace_once(
    source,
    """        int deaths = store.getDeathCount(boss.getId());
        int kills = store.getKillCount(boss.getId());

        JLabel totals = new JLabel(
""",
    """        int deaths = store.getDeathCount(boss.getId());
        int kills = store.getKillCount(boss.getId());
        BossStatistics statistics = store.getBossStatistics(boss.getId());

        JLabel totals = new JLabel(
""",
    "card stats",
)
source = replace_once(
    source,
    """        JLabel forms = new JLabel(
            \"<html><span style='color:#999999'>Known forms:</span> <b>\"
""",
    """        JLabel attemptSummary = new JLabel(
            \"<html>\"
                + \"<span style='color:#aaaaaa'>Attempts:</span> <b>\"
                + statistics.getAttempts()
                + \"</b> &nbsp;&nbsp; \"
                + \"<span style='color:#aaaaaa'>Win:</span> <b>\"
                + formatPercent(statistics.getWinRate())
                + \"</b> &nbsp;&nbsp; \"
                + \"<span style='color:#aaaaaa'>PB:</span> <b>\"
                + formatDuration(statistics.getPersonalBestMillis())
                + \"</b></html>\");
        attemptSummary.setForeground(Color.LIGHT_GRAY);
        attemptSummary.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel forms = new JLabel(
            \"<html><span style='color:#999999'>Known forms:</span> <b>\"
""",
    "card attempt label",
)
source = replace_once(
    source,
    """        card.add(totals);
        card.add(Box.createVerticalStrut(2));
        card.add(forms);
""",
    """        card.add(totals);
        card.add(Box.createVerticalStrut(2));
        card.add(attemptSummary);
        card.add(Box.createVerticalStrut(2));
        card.add(forms);
""",
    "card composition",
)
source = replace_once(
    source,
    """        JLabel counts = new JLabel(
            \"Deaths: \" + store.getDeathCount(boss.getId())
                + \"    Kills: \" + store.getKillCount(boss.getId()));

        JLabel ids = new JLabel(
""",
    """        BossStatistics statistics = store.getBossStatistics(boss.getId());

        JLabel counts = new JLabel(
            \"Deaths: \" + store.getDeathCount(boss.getId())
                + \"    Kills: \" + store.getKillCount(boss.getId()));

        JLabel performance = new JLabel(
            \"<html>\"
                + \"Attempts: <b>\" + statistics.getAttempts() + \"</b><br>\"
                + \"Win rate: <b>\" + formatPercent(statistics.getWinRate()) + \"</b><br>\"
                + \"Current / best streak: <b>\"
                + statistics.getCurrentStreak() + \" / \" + statistics.getBestStreak()
                + \"</b><br>\"
                + \"Personal best: <b>\"
                + formatDuration(statistics.getPersonalBestMillis()) + \"</b><br>\"
                + \"Average kill: <b>\"
                + formatDuration(statistics.getAverageKillMillis()) + \"</b><br>\"
                + \"Double KOs: <b>\" + statistics.getDoubleKos() + \"</b>\"
                + \"</html>\");
        performance.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel ids = new JLabel(
""",
    "details performance",
)
source = replace_once(
    source,
    """        panel.add(counts);
        panel.add(Box.createVerticalStrut(4));
        panel.add(ids);
""",
    """        panel.add(counts);
        panel.add(Box.createVerticalStrut(4));
        panel.add(performance);
        panel.add(Box.createVerticalStrut(4));
        panel.add(ids);
""",
    "details composition",
)
panel_path.write_text(source, encoding="utf-8")

plugin_path = Path("src/main/java/com/bossdeathtracker/BossDeathTrackerPlugin.java")
plugin = plugin_path.read_text(encoding="utf-8")
plugin = replace_once(
    plugin,
    """        panel = new BossDeathTrackerPanel(store, config, configManager);

        navigationButton = NavigationButton.builder()""",
    """        panel = new BossDeathTrackerPanel(store, config, configManager);
        panel.setSessionStartedAt(sessionStartedAt);

        navigationButton = NavigationButton.builder()""",
    "startup session",
)
plugin = replace_once(
    plugin,
    """        if (state == GameState.LOGGED_IN)
        {
            sessionStartedAt = Instant.now();
            loadCurrentProfileData();
""",
    """        if (state == GameState.LOGGED_IN)
        {
            sessionStartedAt = Instant.now();
            if (panel != null)
            {
                panel.setSessionStartedAt(sessionStartedAt);
            }
            loadCurrentProfileData();
""",
    "login session",
)
plugin_path.write_text(plugin, encoding="utf-8")
