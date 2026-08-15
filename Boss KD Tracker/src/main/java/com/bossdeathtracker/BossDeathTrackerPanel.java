package com.bossdeathtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

public class BossDeathTrackerPanel extends PluginPanel
{
    private static final DateTimeFormatter EVENT_TIME_FORMAT =
        DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    private final BossDeathTrackerStore store;
    private final BossDeathTrackerConfig config;

    private final JLabel totalDeathsValue = new JLabel("0");
    private final JLabel totalKillsValue = new JLabel("0");
    private final JLabel ratioValue = new JLabel("0.00%");
    private final JTextField searchField = new JTextField();
    private final JPanel bossList = new JPanel();
    private String activeBossId;
    private String mostRecentBossId;


    public BossDeathTrackerPanel(
        BossDeathTrackerStore store,
        BossDeathTrackerConfig config)
    {
        super(false);
        this.store = store;
        this.config = config;

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

        JScrollPane scrollPane = new JScrollPane(bossList);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        add(body, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        JButton addBossButton = new JButton("+ Add Boss");
        addBossButton.addActionListener(e -> showAddBossDialog());

        JButton catalogButton = new JButton("Boss Catalog");
        catalogButton.addActionListener(e -> showBossCatalog());

        JPanel footerButtons = new JPanel(new GridLayout(1, 2, 4, 0));
        footerButtons.setOpaque(false);
        footerButtons.add(addBossButton);
        footerButtons.add(catalogButton);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ColorScheme.DARK_GRAY_COLOR);
        footer.setBorder(BorderFactory.createEmptyBorder(6, 8, 8, 8));
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
        }
    }

    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        int deaths = store.getTotalDeaths();
        int kills = store.getTotalKills();

        totalDeathsValue.setText(Integer.toString(deaths));
        totalKillsValue.setText(Integer.toString(kills));

        double ratio = kills <= 0 ? 0.0 : (deaths * 100.0 / kills);
        ratioValue.setText(String.format("%.2f%%", ratio));

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
                bossList.add(Box.createVerticalStrut(6));
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
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel title = new JLabel("BOSS DEATH TRACKER");
        title.setForeground(Color.WHITE);
        panel.add(title, BorderLayout.WEST);

        return panel;
    }

    private JPanel buildSummary()
    {
        JPanel summary = new JPanel(new GridLayout(3, 2, 4, 4));
        summary.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        summary.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));

        addSummaryRow(summary, "Lifetime deaths", totalDeathsValue);
        addSummaryRow(summary, "Boss kills", totalKillsValue);
        addSummaryRow(summary, "Death / kill", ratioValue);

        return summary;
    }

    private void addSummaryRow(JPanel panel, String labelText, JLabel valueLabel)
    {
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.LIGHT_GRAY);
        valueLabel.setForeground(Color.WHITE);

        panel.add(label);
        panel.add(valueLabel);
    }

    private JPanel buildSearch()
    {
        JPanel panel = new JPanel(new BorderLayout(4, 4));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);

        searchField.setToolTipText("Search bosses");
        panel.add(searchField, BorderLayout.CENTER);

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
        card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ColorScheme.MEDIUM_GRAY_COLOR),
            BorderFactory.createEmptyBorder(8, 8, 8, 8)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 130));

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);

        JLabel name = new JLabel(boss.getDisplayName());
        name.setForeground(Color.WHITE);

        JLabel status = new JLabel(boss.getSyncStatus().getDisplayName());
        status.setForeground(statusColor(boss.getSyncStatus()));

        top.add(name, BorderLayout.WEST);
        top.add(status, BorderLayout.EAST);

        int deaths = store.getDeathCount(boss.getId());
        int kills = store.getKillCount(boss.getId());

        JLabel stats = new JLabel(
            "<html>Deaths: <b>" + deaths + "</b> &nbsp;&nbsp; Kills: <b>" + kills
                + "</b><br>Known forms: <b>" + boss.getNpcIds().size() + "</b></html>");
        stats.setForeground(Color.LIGHT_GRAY);

        JPanel actions = new JPanel(new GridLayout(1, 3, 4, 0));
        actions.setOpaque(false);

        JButton deathButton = new JButton("+ Death");
        deathButton.addActionListener(e ->
        {
            store.addDeath(boss.getId());
            refresh();
        });

        JButton killButton = new JButton("+ Kill");
        killButton.addActionListener(e ->
        {
            store.addKill(boss.getId());
            refresh();
        });

        JButton detailsButton = new JButton("Details");
        detailsButton.addActionListener(e -> showBossDetails(boss));

        actions.add(deathButton);
        actions.add(killButton);
        actions.add(detailsButton);

        card.add(top);
        card.add(Box.createVerticalStrut(6));
        card.add(stats);
        card.add(Box.createVerticalStrut(8));
        card.add(actions);

        return card;
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
                "Boss Death Tracker",
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
                    "Boss Death Tracker",
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
            "Boss Death Tracker",
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
