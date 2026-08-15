package com.bossdeathtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import java.net.URL;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class BossDeathTrackerPanel extends PluginPanel
{
    private enum HeaderMode
    {
        CURRENT,
        NEMESIS,
        RECORDS
    }

    private static ImageIcon loadIcon(String name)
    {
        URL resource = BossDeathTrackerPanel.class.getResource(
            "/bosskd/icons/" + name);

        return resource == null ? null : new ImageIcon(resource);
    }

    private static final DateTimeFormatter EVENT_TIME_FORMAT =
        DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    private final BossDeathTrackerStore store;
    private final BossDeathTrackerConfig config;
    private final ConfigManager configManager;

    private final ImageIcon swordIcon = loadIcon("swords.png");
    private final ImageIcon skullIcon = loadIcon("skull.png");
    private final ImageIcon trophyIcon = loadIcon("trophy.png");
    private final ImageIcon chartIcon = loadIcon("chart.png");
    private final ImageIcon searchIcon = loadIcon("search.png");
    private final ImageIcon detailsIcon = loadIcon("details.png");
    private final ImageIcon checkIcon = loadIcon("check.png");
    private final ImageIcon syncIcon = loadIcon("sync.png");
    private final ImageIcon plusIcon = loadIcon("plus.png");
    private final ImageIcon bookIcon = loadIcon("book.png");

    private final JButton currentHeaderButton = new JButton("Current");
    private final JButton nemesisHeaderButton = new JButton("Nemesis");
    private final JButton recordsHeaderButton = new JButton("Records");
    private final JLabel insightBossValue = new JLabel("No recent boss");
    private final JLabel insightKillsValue = new JLabel("0");
    private final JLabel insightDeathsValue = new JLabel("0");
    private final JLabel insightKdValue = new JLabel("0.00");
    private HeaderMode headerMode = HeaderMode.CURRENT;

    private final JTextField searchField = new JTextField();
    private final JPanel bossList = new JPanel();
    private String activeBossId;
    private String mostRecentBossId;


    public BossDeathTrackerPanel(
        BossDeathTrackerStore store,
        BossDeathTrackerConfig config,
        ConfigManager configManager)
    {
        super(false);
        this.store = store;
        this.config = config;
        this.configManager = configManager;
        loadHeaderMode();

        setLayout(new BorderLayout());
        setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel body = new JPanel();
        body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
        body.setBackground(ColorScheme.DARK_GRAY_COLOR);
        body.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        body.add(buildHeader());
        body.add(Box.createVerticalStrut(8));
        body.add(buildSummary());
        body.add(Box.createVerticalStrut(8));
        body.add(buildSearch());
        body.add(Box.createVerticalStrut(8));

        bossList.setLayout(new BoxLayout(bossList, BoxLayout.Y_AXIS));
        bossList.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bossList.setAlignmentX(Component.LEFT_ALIGNMENT);

        // Keep the controls fixed and scroll only the long boss-card list.
        JScrollPane bossScrollPane = new JScrollPane(
            bossList,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        bossScrollPane.setBorder(BorderFactory.createEmptyBorder());
        bossScrollPane.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bossScrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);
        bossScrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(body, BorderLayout.NORTH);
        add(bossScrollPane, BorderLayout.CENTER);

        JButton addBossButton = new JButton("Add Boss", plusIcon);
        addBossButton.setMargin(new Insets(2, 4, 2, 4));
        addBossButton.addActionListener(e -> showAddBossDialog());

        JButton catalogButton = new JButton("Catalog", bookIcon);
        catalogButton.setMargin(new Insets(2, 4, 2, 4));
        catalogButton.addActionListener(e -> showBossCatalog());

        JPanel footerButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        footerButtons.setOpaque(false);
        footerButtons.add(addBossButton);
        footerButtons.add(catalogButton);

        JButton syncKillsButton = new JButton("Sync Existing Kills", syncIcon);
        syncKillsButton.setMargin(new Insets(2, 4, 2, 4));
        syncKillsButton.setToolTipText(
            "Import historical boss kill counts already known by RuneLite");
        syncKillsButton.addActionListener(e -> syncExistingKills());

        JPanel footer = new JPanel(new BorderLayout(0, 5));
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
        footer.add(syncKillsButton, BorderLayout.NORTH);
        footer.add(footerButtons, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        refresh();
    }

    public void setActiveBoss(String bossId)
    {
        if (!java.util.Objects.equals(activeBossId, bossId))
        {
            activeBossId = bossId;

            if (bossId != null)
            {
                mostRecentBossId = bossId;
            }

            refresh();
            return;
        }

        if (SwingUtilities.isEventDispatchThread())
        {
            updateInsightSummary();
        }
        else
        {
            SwingUtilities.invokeLater(this::updateInsightSummary);
        }
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        updateInsightSummary();

        bossList.removeAll();

        List<BossProfile> bosses = store.searchBosses(searchField.getText());

        bosses.sort((left, right) ->
        {
            int leftPriority = bossDisplayPriority(left.getId());
            int rightPriority = bossDisplayPriority(right.getId());

            if (leftPriority != rightPriority)
            {
                return Integer.compare(leftPriority, rightPriority);
            }

            return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
        });

        if (bosses.isEmpty())
        {
            JLabel empty = new JLabel(
                "<html><center>No bosses yet.<br>Add one to start tracking.</center></html>");
            empty.setForeground(Color.LIGHT_GRAY);
            empty.setAlignmentX(Component.CENTER_ALIGNMENT);
            bossList.add(Box.createVerticalStrut(16));
            bossList.add(empty);
        }
        else
        {
            for (BossProfile boss : bosses)
            {
                bossList.add(buildBossCard(boss));
                bossList.add(Box.createVerticalStrut(5));
            }
        }

        bossList.revalidate();
        bossList.repaint();
    }

    private int bossDisplayPriority(String bossId)
    {
        if (bossId != null && bossId.equals(activeBossId))
        {
            return 0;
        }

        if (bossId != null && bossId.equals(mostRecentBossId))
        {
            return 1;
        }

        return 2;
    }

    private JPanel buildHeader()
    {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(new Color(31, 31, 31));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(
                0, 0, 1, 0, ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(10, 10, 10, 10)));

        JLabel title = new JLabel("Boss KD Tracker");
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        panel.add(title, BorderLayout.WEST);

        return panel;
    }

    private JPanel buildSummary()
    {
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setBackground(ColorScheme.DARK_GRAY_COLOR);

        JPanel modeButtons = new JPanel(new GridLayout(1, 3, 3, 0));
        modeButtons.setOpaque(false);
        modeButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

        configureHeaderButton(currentHeaderButton, HeaderMode.CURRENT);
        configureHeaderButton(nemesisHeaderButton, HeaderMode.NEMESIS);
        configureHeaderButton(recordsHeaderButton, HeaderMode.RECORDS);

        modeButtons.add(currentHeaderButton);
        modeButtons.add(nemesisHeaderButton);
        modeButtons.add(recordsHeaderButton);

        JPanel summary = new JPanel();
        summary.setLayout(new BoxLayout(summary, BoxLayout.Y_AXIS));
        summary.setBackground(new Color(29, 29, 29));
        summary.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(68, 68, 68)),
            BorderFactory.createEmptyBorder(10, 10, 9, 10)));

        insightBossValue.setForeground(Color.WHITE);
        insightBossValue.setFont(insightBossValue.getFont().deriveFont(Font.BOLD));
        insightBossValue.setHorizontalAlignment(SwingConstants.CENTER);
        insightBossValue.setAlignmentX(Component.CENTER_ALIGNMENT);
        insightBossValue.setMaximumSize(
            new Dimension(Integer.MAX_VALUE, 25));

        summary.add(insightBossValue);
        summary.add(Box.createVerticalStrut(7));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(swordIcon, "Kills", insightKillsValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(skullIcon, "Deaths", insightDeathsValue));
        summary.add(buildMetricSeparator());
        summary.add(buildInsightRow(chartIcon, "K/D", insightKdValue));

        container.add(modeButtons);
        container.add(Box.createVerticalStrut(6));
        container.add(summary);

        updateHeaderButtonState();
        return container;
    }

    private JPanel buildInsightRow(
        ImageIcon rowIcon,
        String labelText,
        JLabel valueLabel)
    {
        JPanel row = new JPanel(new BorderLayout(6, 0));
        row.setOpaque(false);
        row.setBorder(BorderFactory.createEmptyBorder(7, 2, 7, 2));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        JLabel icon = new JLabel(rowIcon);
        icon.setPreferredSize(new Dimension(20, 20));

        JLabel label = new JLabel(labelText);
        label.setForeground(Color.LIGHT_GRAY);

        valueLabel.setForeground(Color.WHITE);
        valueLabel.setFont(valueLabel.getFont().deriveFont(Font.BOLD));
        valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);

        JPanel left = new JPanel(new BorderLayout(4, 0));
        left.setOpaque(false);
        left.add(icon, BorderLayout.WEST);
        left.add(label, BorderLayout.CENTER);

        row.add(left, BorderLayout.WEST);
        row.add(valueLabel, BorderLayout.EAST);
        return row;
    }

    private JSeparator buildMetricSeparator()
    {
        JSeparator separator = new JSeparator();
        separator.setForeground(new Color(56, 56, 56));
        separator.setBackground(new Color(56, 56, 56));
        separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return separator;
    }

    private void configureHeaderButton(JButton button, HeaderMode mode)
    {
        button.setFocusable(false);
        button.setMargin(new Insets(4, 1, 4, 1));
        button.setHorizontalAlignment(SwingConstants.CENTER);
        button.setIconTextGap(4);
        button.setMinimumSize(new Dimension(0, 31));
        button.setPreferredSize(new Dimension(0, 31));
        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));

        if (mode == HeaderMode.CURRENT)
        {
            button.setIcon(swordIcon);
            button.setToolTipText("Current or most recently fought boss");
        }
        else if (mode == HeaderMode.NEMESIS)
        {
            button.setIcon(skullIcon);
            button.setToolTipText("Boss with the most tracked deaths");
        }
        else
        {
            button.setIcon(trophyIcon);
            button.setToolTipText("Boss with your highest kill count");
        }

        button.addActionListener(e ->
        {
            headerMode = mode;
            saveHeaderMode();
            updateInsightSummary();
        });
    }

    private void loadHeaderMode()
    {
        String saved = configManager.getConfiguration(
            "bossdeathtracker",
            "headerMode");

        if (saved == null || saved.trim().isEmpty())
        {
            headerMode = HeaderMode.CURRENT;
            return;
        }

        try
        {
            headerMode = HeaderMode.valueOf(saved.trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex)
        {
            headerMode = HeaderMode.CURRENT;
        }
    }

    private void saveHeaderMode()
    {
        configManager.setConfiguration(
            "bossdeathtracker",
            "headerMode",
            headerMode.name());
    }

    private void updateHeaderButtonState()
    {
        styleModeButton(currentHeaderButton, headerMode == HeaderMode.CURRENT);
        styleModeButton(nemesisHeaderButton, headerMode == HeaderMode.NEMESIS);
        styleModeButton(recordsHeaderButton, headerMode == HeaderMode.RECORDS);
    }

    private void styleModeButton(JButton button, boolean selected)
    {
        button.setEnabled(true);
        button.setForeground(
            selected ? Color.WHITE : new Color(205, 205, 205));
        button.setBackground(
            selected ? new Color(53, 66, 78) : new Color(38, 38, 38));
        button.setBorder(BorderFactory.createLineBorder(
            selected
                ? new Color(86, 112, 135)
                : new Color(67, 67, 67)));
    }

    private void updateInsightSummary()
    {
        updateHeaderButtonState();

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
                String bossId = activeBossId != null ? activeBossId : mostRecentBossId;

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
                insightBossValue.setText("No tracked deaths yet");
            }
            else if (headerMode == HeaderMode.RECORDS)
            {
                insightBossValue.setText("No boss kills yet");
            }
            else
            {
                insightBossValue.setText("No recent boss");
            }

            insightKillsValue.setText("0");
            insightDeathsValue.setText("0");
            insightKdValue.setText("0.00");
            return;
        }

        int kills = store.getKillCount(boss.getId());
        int deaths = store.getDeathCount(boss.getId());

        insightBossValue.setText(boss.getDisplayName());
        insightKillsValue.setText(Integer.toString(kills));
        insightDeathsValue.setText(Integer.toString(deaths));

        if (deaths == 0)
        {
            insightKdValue.setText(kills > 0 ? "\u221e" : "0.00");
        }
        else
        {
            insightKdValue.setText(
                String.format(
                    Locale.ROOT,
                    "%.2f",
                    (double) kills / (double) deaths));
        }
    }

    private JPanel buildSearch()
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));

        searchField.setToolTipText("Search bosses");
        searchField.setPreferredSize(new Dimension(0, 30));
        searchField.setBackground(new Color(27, 27, 27));
        searchField.setForeground(Color.WHITE);
        searchField.setCaretColor(Color.WHITE);
        JLabel searchGlyph = new JLabel(searchIcon);
        searchGlyph.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 2));

        JPanel searchBox = new JPanel(new BorderLayout(3, 0));
        searchBox.setBackground(new Color(27, 27, 27));
        searchBox.setBorder(BorderFactory.createLineBorder(new Color(65, 65, 65)));

        searchField.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 7));
        searchBox.add(searchGlyph, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);

        panel.add(searchBox, BorderLayout.CENTER);

        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent e)
            {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e)
            {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e)
            {
                refresh();
            }
        });

        return panel;
    }

    private JPanel buildBossCard(BossProfile boss)
    {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBackground(new Color(30, 30, 30));
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(67, 67, 67)),
            BorderFactory.createEmptyBorder(8, 7, 8, 7)));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 154));

        JLabel name = new JLabel(boss.getDisplayName());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel status = new JLabel(boss.getSyncStatus().getDisplayName());
        status.setForeground(statusColor(boss.getSyncStatus()));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (boss.getSyncStatus() == SyncStatus.AUTO)
        {
            status.setIcon(checkIcon);
            status.setIconTextGap(3);
        }

        int deaths = store.getDeathCount(boss.getId());
        int kills = store.getKillCount(boss.getId());

        JLabel totals = new JLabel(
            "<html>"
                + "<span style='color:#aaaaaa'>Deaths:</span> <b>" + deaths + "</b>"
                + " &nbsp;&nbsp;&nbsp; "
                + "<span style='color:#aaaaaa'>Kills:</span> <b>" + kills + "</b>"
                + "</html>");
        totals.setForeground(Color.LIGHT_GRAY);
        totals.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel forms = new JLabel(
            "<html><span style='color:#999999'>Known forms:</span> <b>"
                + boss.getNpcIds().size()
                + "</b></html>");
        forms.setForeground(Color.LIGHT_GRAY);
        forms.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel actions = new JPanel(new GridLayout(1, 3, 3, 0));
        actions.setOpaque(false);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));

        JButton deathButton = new JButton("+ Death", skullIcon);
        deathButton.setMargin(new Insets(3, 1, 3, 1));
        deathButton.addActionListener(e ->
        {
            store.addDeath(boss.getId());
            refresh();
        });

        JButton killButton = new JButton("+ Kill", swordIcon);
        killButton.setMargin(new Insets(3, 1, 3, 1));
        killButton.addActionListener(e ->
        {
            store.addKill(boss.getId());
            refresh();
        });

        JButton detailsButton = new JButton("Details", detailsIcon);
        detailsButton.setMargin(new Insets(3, 1, 3, 1));
        detailsButton.addActionListener(e -> showBossDetails(boss));

        actions.add(deathButton);
        actions.add(killButton);
        actions.add(detailsButton);

        card.add(name);
        card.add(Box.createVerticalStrut(3));
        card.add(status);
        card.add(Box.createVerticalStrut(6));
        card.add(totals);
        card.add(Box.createVerticalStrut(2));
        card.add(forms);
        card.add(Box.createVerticalStrut(8));
        card.add(actions);

        return card;
    }

    private void syncExistingKills()
    {
        BossDeathTrackerStore.HistoricalKillSyncResult result =
            store.syncRuneLiteKillCounts(configManager);

        refresh();

        if (!result.isRuneScapeProfileAvailable())
        {
            JOptionPane.showMessageDialog(
                this,
                "RuneLite does not currently have an active RuneScape profile.\n"
                    + "Log into your character, then try the sync again.",
                "Sync Existing Kills",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        StringBuilder message = new StringBuilder();
        message.append("Boss kill synchronization complete.\n\n");
        message.append("Cached bosses found: ")
            .append(result.getBossesWithCachedKc())
            .append('\n');
        message.append("Bosses updated: ")
            .append(result.getBossesUpdated())
            .append('\n');
        message.append("Historical kills imported: ")
            .append(result.getKillsImported())
            .append('\n');
        message.append("No cached KC: ")
            .append(result.getBossesWithoutCachedKc());

        if (result.getBossesWithCachedKc() == 0)
        {
            message.append(
                "\n\nRuneLite does not know any boss kill counts yet.\n"
                    + "Open your in-game Boss Kill Log, then sync again.");
        }
        else if (result.getBossesWithoutCachedKc() > 0)
        {
            message.append(
                "\n\nFor additional historical counts, open your in-game "
                    + "Boss Kill Log. RuneLite will cache entries it can read.");
        }

        JOptionPane.showMessageDialog(
            this,
            message.toString(),
            "Sync Existing Kills",
            JOptionPane.INFORMATION_MESSAGE);
    }

    private void showAddBossDialog()
    {
        JTextField name = new JTextField();
        JTextField category = new JTextField("Custom");
        JTextField subcategory = new JTextField("Uncategorized");
        JTextField npcIds = new JTextField();
        npcIds.setToolTipText("Comma-separated RuneLite NPC IDs, e.g. 8061");
        JTextArea notes = new JTextArea(4, 24);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(new JLabel("Boss name"));
        form.add(name);
        form.add(Box.createVerticalStrut(6));

        form.add(new JLabel("Category"));
        form.add(category);
        form.add(Box.createVerticalStrut(6));

        form.add(new JLabel("Subcategory"));
        form.add(subcategory);
        form.add(Box.createVerticalStrut(6));

        form.add(new JLabel("NPC IDs (optional, comma-separated)"));
        form.add(npcIds);
        form.add(Box.createVerticalStrut(6));

        form.add(new JLabel("Notes"));
        form.add(new JScrollPane(notes));

        int result = JOptionPane.showConfirmDialog(
            this,
            form,
            "Add Boss",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

        if (result != JOptionPane.OK_OPTION)
        {
            return;
        }

        try
        {
            store.addManualBoss(
                name.getText(),
                category.getText(),
                subcategory.getText(),
                parseNpcIds(npcIds.getText()),
                notes.getText());

            refresh();
        }
        catch (IllegalArgumentException ex)
        {
            JOptionPane.showMessageDialog(
                this,
                ex.getMessage(),
                "Boss KD Tracker",
                JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showBossDetails(BossProfile boss)
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));

        JLabel title = new JLabel(boss.getDisplayName());
        title.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel status = new JLabel(
            "Tracking: " + boss.getSyncStatus().getDisplayName());
        status.setForeground(statusColor(boss.getSyncStatus()));
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel counts = new JLabel(
            "Deaths: " + store.getDeathCount(boss.getId())
                + "    Kills: " + store.getKillCount(boss.getId()));

        JLabel ids = new JLabel(
            "<html>Known forms: <b>" + boss.getNpcIds().size() + "</b><br>"
                + "NPC IDs: <b>" + formatNpcIds(boss.getNpcIds()) + "</b></html>");
        ids.setAlignmentX(Component.LEFT_ALIGNMENT);
        counts.setAlignmentX(Component.LEFT_ALIGNMENT);

        panel.add(title);
        panel.add(Box.createVerticalStrut(4));
        panel.add(status);
        panel.add(Box.createVerticalStrut(4));
        panel.add(counts);
        panel.add(Box.createVerticalStrut(4));
        panel.add(ids);
        panel.add(Box.createVerticalStrut(6));

        JLabel verification = new JLabel(
            "<html>"
                + "Encounter verified: <b>" + yesNo(boss.isEncounterVerified()) + "</b><br>"
                + "Death verified: <b>" + yesNo(boss.isDeathVerified()) + "</b><br>"
                + "Kill verified: <b>" + yesNo(boss.isKillVerified()) + "</b>"
                + "</html>");
        verification.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(verification);
        panel.add(Box.createVerticalStrut(8));
        panel.add(new JSeparator());
        panel.add(Box.createVerticalStrut(8));

        JPanel correctionRow = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton minusDeath = new JButton("- Death");
        JButton minusKill = new JButton("- Kill");

        minusDeath.addActionListener(e ->
        {
            if (confirmCorrection("Subtract one death?"))
            {
                store.removeDeath(boss.getId());
                refresh();
            }
        });

        minusKill.addActionListener(e ->
        {
            if (confirmCorrection("Subtract one kill?"))
            {
                store.removeKill(boss.getId());
                refresh();
            }
        });

        correctionRow.add(minusDeath);
        correctionRow.add(minusKill);
        correctionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(correctionRow);

        panel.add(Box.createVerticalStrut(8));

        JButton history = new JButton("View Event History");
        history.setAlignmentX(Component.LEFT_ALIGNMENT);
        history.addActionListener(e -> showHistory(boss));
        panel.add(history);

        panel.add(Box.createVerticalStrut(6));

        JButton edit = new JButton("Edit Boss");
        edit.setAlignmentX(Component.LEFT_ALIGNMENT);
        edit.addActionListener(e ->
        {
            showEditBossDialog(boss);
            refresh();
        });
        panel.add(edit);

        panel.add(Box.createVerticalStrut(6));

        JButton sync = new JButton("Sync Status...");
        sync.setAlignmentX(Component.LEFT_ALIGNMENT);
        sync.addActionListener(e ->
        {
            SyncStatus selected = (SyncStatus) JOptionPane.showInputDialog(
                this,
                "Set the current boss-definition status.\n"
                    + "Actual definition matching will be added in the synchronization layer.",
                "Boss Sync Status",
                JOptionPane.PLAIN_MESSAGE,
                null,
                SyncStatus.values(),
                boss.getSyncStatus());

            if (selected != null)
            {
                store.setSyncStatus(boss.getId(), selected);
                refresh();
            }
        });
        panel.add(sync);

        panel.add(Box.createVerticalStrut(6));

        JButton delete = new JButton("Delete Boss");
        delete.setAlignmentX(Component.LEFT_ALIGNMENT);
        delete.addActionListener(e ->
        {
            int answer = JOptionPane.showConfirmDialog(
                this,
                "Delete " + boss.getDisplayName()
                    + " and all of its recorded events?",
                "Delete Boss",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (answer == JOptionPane.YES_OPTION)
            {
                store.deleteBoss(boss.getId());
                refresh();
            }
        });
        panel.add(delete);

        JOptionPane.showMessageDialog(
            this,
            panel,
            "Boss Details",
            JOptionPane.PLAIN_MESSAGE);
    }

    private void showEditBossDialog(BossProfile boss)
    {
        JTextField name = new JTextField(boss.getDisplayName());
        JTextField category = new JTextField(boss.getCategory());
        JTextField subcategory = new JTextField(boss.getSubcategory());
        JTextField npcIds = new JTextField(formatNpcIds(boss.getNpcIds()));
        JTextArea notes = new JTextArea(boss.getNotes(), 5, 24);
        notes.setLineWrap(true);
        notes.setWrapStyleWord(true);

        JPanel form = new JPanel();
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));

        form.add(new JLabel("Boss name"));
        form.add(name);
        form.add(Box.createVerticalStrut(6));
        form.add(new JLabel("Category"));
        form.add(category);
        form.add(Box.createVerticalStrut(6));
        form.add(new JLabel("Subcategory"));
        form.add(subcategory);
        form.add(Box.createVerticalStrut(6));
        form.add(new JLabel("NPC IDs (comma-separated)"));
        form.add(npcIds);
        form.add(Box.createVerticalStrut(6));
        form.add(new JLabel("Notes"));
        form.add(new JScrollPane(notes));

        int result = JOptionPane.showConfirmDialog(
            this,
            form,
            "Edit " + boss.getDisplayName(),
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION)
        {
            try
            {
                store.updateBoss(
                    boss.getId(),
                    name.getText(),
                    category.getText(),
                    subcategory.getText(),
                    parseNpcIds(npcIds.getText()),
                    notes.getText());
            }
            catch (IllegalArgumentException ex)
            {
                JOptionPane.showMessageDialog(
                    this,
                    ex.getMessage(),
                    "Boss KD Tracker",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void showHistory(BossProfile boss)
    {
        List<TrackerEvent> events = store.getEventsForBoss(boss.getId());

        JTextArea area = new JTextArea(18, 42);
        area.setEditable(false);

        if (events.isEmpty())
        {
            area.setText("No events recorded yet.");
        }
        else
        {
            StringBuilder text = new StringBuilder();

            for (TrackerEvent event : events)
            {
                text.append(EVENT_TIME_FORMAT.format(event.getTimestamp()))
                    .append("  ")
                    .append(event.getType())
                    .append("  ")
                    .append(event.getAmount() > 0 ? "+" : "")
                    .append(event.getAmount())
                    .append("  [")
                    .append(event.getSource())
                    .append("]");

                if (!event.getNote().isEmpty())
                {
                    text.append("  ").append(event.getNote());
                }

                text.append(System.lineSeparator());
            }

            area.setText(text.toString());
            area.setCaretPosition(0);
        }

        JOptionPane.showMessageDialog(
            this,
            new JScrollPane(area),
            boss.getDisplayName() + " - Event History",
            JOptionPane.PLAIN_MESSAGE);
    }

    private void showBossCatalog()
    {
        List<BossDefinition> definitions = BossDefinitionRegistry.getAll();

        StringBuilder text = new StringBuilder();
        text.append("Built-in boss definitions: ")
            .append(definitions.size())
            .append(System.lineSeparator())
            .append(System.lineSeparator());

        for (BossDefinition definition : definitions)
        {
            text.append(definition.getDisplayName())
                .append("  —  ")
                .append(formatNpcIds(
                    java.util.Arrays.stream(definition.getNpcIds())
                        .boxed()
                        .collect(Collectors.toList())))
                .append(System.lineSeparator());
        }

        JTextArea area = new JTextArea(24, 46);
        area.setEditable(false);
        area.setText(text.toString());
        area.setCaretPosition(0);

        JOptionPane.showMessageDialog(
            this,
            new JScrollPane(area),
            "Boss NPC ID Catalog",
            JOptionPane.PLAIN_MESSAGE);
    }

    private static List<Integer> parseNpcIds(String text)
    {
        List<Integer> result = new ArrayList<>();

        if (text == null || text.trim().isEmpty())
        {
            return result;
        }

        for (String token : text.split("[,;\\\\s]+"))
        {
            if (token.trim().isEmpty())
            {
                continue;
            }

            try
            {
                int id = Integer.parseInt(token.trim());

                if (id < 0)
                {
                    throw new NumberFormatException();
                }

                if (!result.contains(id))
                {
                    result.add(id);
                }
            }
            catch (NumberFormatException ex)
            {
                throw new IllegalArgumentException(
                    "Invalid NPC ID: " + token + ". Use numeric IDs separated by commas.");
            }
        }

        return result;
    }

    private static String formatNpcIds(List<Integer> ids)
    {
        if (ids == null || ids.isEmpty())
        {
            return "None";
        }

        return ids.stream()
            .map(String::valueOf)
            .collect(Collectors.joining(", "));
    }

    private boolean confirmCorrection(String message)
    {
        if (!config.confirmCorrections())
        {
            return true;
        }

        return JOptionPane.showConfirmDialog(
            this,
            message,
            "Boss KD Tracker",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private static String yesNo(boolean value)
    {
        return value ? "Yes" : "No";
    }

    private static Color statusColor(SyncStatus status)
    {
        switch (status)
        {
            case AUTO:
            case SYNCED:
                return new Color(92, 184, 92);

            case PARTIAL:
                return new Color(240, 173, 78);

            case DISABLED:
                return new Color(180, 180, 180);

            case MANUAL:
            default:
                return new Color(91, 192, 222);
        }
    }
}
