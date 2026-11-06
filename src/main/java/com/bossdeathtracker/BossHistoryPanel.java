package com.bossdeathtracker;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.ColorScheme;

/**
 * Filterable encounter and count history with reversible corrections.
 */
final class BossHistoryPanel extends JPanel
{
    private static final String ALL_BOSSES = "All bosses";
    private static final String ALL_RESULTS = "All results";
    private static final DateTimeFormatter TIME_FORMAT =
        DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a")
            .withZone(ZoneId.systemDefault());

    private final BossDeathTrackerStore store;
    private final Runnable onDataChanged;
    private final JComboBox<String> bossFilter = new JComboBox<>();
    private final JComboBox<String> resultFilter = new JComboBox<>(new String[]
    {
        ALL_RESULTS,
        "KILL",
        "DEATH",
        "DOUBLE_KO",
        "ABANDONED",
        "DISCONNECTED",
        "MANUAL",
        "IMPORTED",
        "CORRECTION",
        "VOIDED"
    });
    private final DefaultTableModel model = new DefaultTableModel(
        new Object[]
        {
            "Time",
            "Boss",
            "Result",
            "Duration",
            "Source",
            "Details"
        },
        0)
    {
        @Override
        public boolean isCellEditable(int row, int column)
        {
            return false;
        }
    };
    private final JTable table = new JTable(model);
    private final List<HistoryRow> visibleRows = new ArrayList<>();
    private boolean updatingFilters;

    BossHistoryPanel(
        BossDeathTrackerStore store,
        Runnable onDataChanged)
    {
        super(new BorderLayout(6, 6));
        this.store = store;
        this.onDataChanged = onDataChanged;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        controls.setOpaque(false);
        controls.add(new JLabel("Boss:"));
        controls.add(bossFilter);
        controls.add(new JLabel("Result:"));
        controls.add(resultFilter);

        JButton refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(event -> refresh());
        controls.add(refreshButton);

        JButton undoButton = new JButton("Undo Selected");
        undoButton.setToolTipText(
            "Append a correction record and void a linked encounter");
        undoButton.addActionListener(event -> undoSelected());
        controls.add(undoButton);

        table.setAutoCreateRowSorter(true);
        table.setFillsViewportHeight(true);
        table.setRowHeight(22);

        JScrollPane scrollPane = new JScrollPane(table);
        scrollPane.getViewport().setBackground(ColorScheme.DARK_GRAY_COLOR);

        add(controls, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);

        bossFilter.addActionListener(event ->
        {
            if (!updatingFilters)
            {
                rebuildRows();
            }
        });
        resultFilter.addActionListener(event -> rebuildRows());

        refresh();
    }

    void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        updateBossFilter();
        rebuildRows();
    }

    private void updateBossFilter()
    {
        String selected = (String) bossFilter.getSelectedItem();
        updatingFilters = true;

        bossFilter.removeAllItems();
        bossFilter.addItem(ALL_BOSSES);

        for (BossProfile boss : store.getBosses())
        {
            bossFilter.addItem(boss.getDisplayName());
        }

        if (selected != null)
        {
            bossFilter.setSelectedItem(selected);
        }
        if (bossFilter.getSelectedIndex() < 0)
        {
            bossFilter.setSelectedIndex(0);
        }

        updatingFilters = false;
    }

    private void rebuildRows()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::rebuildRows);
            return;
        }

        Map<String, BossProfile> bossesById = new HashMap<>();
        for (BossProfile boss : store.getBosses())
        {
            bossesById.put(boss.getId(), boss);
        }

        Set<String> reversedEventIds = new HashSet<>();
        List<TrackerEvent> events = store.getAllEvents();
        for (TrackerEvent event : events)
        {
            if (event.getReversesEventId() != null)
            {
                reversedEventIds.add(event.getReversesEventId());
            }
        }

        List<HistoryRow> allRows = new ArrayList<>();

        for (EncounterAttempt attempt : store.getAllAttempts())
        {
            BossProfile boss = bossesById.get(attempt.getBossId());
            String bossName = boss == null
                ? attempt.getBossName()
                : boss.getDisplayName();
            String result = attempt.isVoided()
                ? "VOIDED " + attempt.getOutcome()
                : attempt.getOutcome().name();
            String details = attempt.isVoided()
                ? attempt.getVoidReason()
                : joinDetails(
                    attempt.getCompletionSignal(),
                    attempt.getNote());

            allRows.add(new HistoryRow(
                attempt.getId(),
                true,
                attempt.getStartedAt(),
                bossName,
                result,
                formatDuration(attempt.getDurationMillis()),
                "Encounter",
                details,
                attempt.isCompleted() && !attempt.isVoided()));
        }

        for (TrackerEvent event : events)
        {
            // Encounter-linked count events are represented by the encounter row.
            if (event.getEncounterId() != null)
            {
                continue;
            }

            BossProfile boss = bossesById.get(event.getBossId());
            String bossName = boss == null
                ? event.getBossId()
                : boss.getDisplayName();
            String result = event.getAmount() < 0
                ? "CORRECTION " + event.getType()
                : event.getType().name();
            String source = event.getSource() == null
                ? "Unknown"
                : event.getSource().name();
            boolean reversible = event.getAmount() > 0
                && event.getSource() != TrackerEventSource.IMPORTED
                && !reversedEventIds.contains(event.getId());

            allRows.add(new HistoryRow(
                event.getId(),
                false,
                event.getTimestamp(),
                bossName,
                result,
                "--",
                source,
                event.getNote(),
                reversible));
        }

        allRows.sort(Comparator.comparing(
            HistoryRow::getTimestamp,
            Comparator.nullsLast(Comparator.reverseOrder())));

        String selectedBoss = (String) bossFilter.getSelectedItem();
        String selectedResult = (String) resultFilter.getSelectedItem();

        visibleRows.clear();
        model.setRowCount(0);

        for (HistoryRow row : allRows)
        {
            if (selectedBoss != null
                && !ALL_BOSSES.equals(selectedBoss)
                && !selectedBoss.equals(row.bossName))
            {
                continue;
            }
            if (selectedResult != null
                && !ALL_RESULTS.equals(selectedResult)
                && !matchesResult(row, selectedResult))
            {
                continue;
            }

            visibleRows.add(row);
            model.addRow(new Object[]
            {
                row.timestamp == null ? "--" : TIME_FORMAT.format(row.timestamp),
                row.bossName,
                row.result,
                row.duration,
                row.source,
                row.details
            });
        }
    }

    private void undoSelected()
    {
        int selectedViewRow = table.getSelectedRow();
        if (selectedViewRow < 0)
        {
            JOptionPane.showMessageDialog(
                this,
                "Select a reversible encounter or count record first.",
                "Undo History",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int selectedModelRow = table.convertRowIndexToModel(selectedViewRow);
        if (selectedModelRow < 0 || selectedModelRow >= visibleRows.size())
        {
            return;
        }

        HistoryRow row = visibleRows.get(selectedModelRow);
        if (!row.reversible)
        {
            JOptionPane.showMessageDialog(
                this,
                "That history entry cannot be undone or has already been corrected.",
                "Undo History",
                JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(
            this,
            "Undo this " + row.result + " entry for " + row.bossName + "?\n"
                + "The original record will remain in history and a correction will be appended.",
            "Undo History",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (answer != JOptionPane.YES_OPTION)
        {
            return;
        }

        BossDeathTrackerStore.UndoResult result = row.encounter
            ? store.voidEncounter(row.id)
            : store.undoEvent(row.id);

        JOptionPane.showMessageDialog(
            this,
            result.getDescription(),
            "Undo History",
            result.isSuccess()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE);

        if (result.isSuccess() && onDataChanged != null)
        {
            onDataChanged.run();
        }

        refresh();
    }

    private static boolean matchesResult(HistoryRow row, String filter)
    {
        if (filter.equals(row.result))
        {
            return true;
        }
        if ("VOIDED".equals(filter))
        {
            return row.result.startsWith("VOIDED");
        }
        if ("CORRECTION".equals(filter))
        {
            return row.result.startsWith("CORRECTION");
        }
        if ("MANUAL".equals(filter) || "IMPORTED".equals(filter))
        {
            return filter.equals(row.source);
        }
        return false;
    }

    private static String joinDetails(String first, String second)
    {
        String left = first == null ? "" : first.trim();
        String right = second == null ? "" : second.trim();

        if (left.isEmpty())
        {
            return right;
        }
        if (right.isEmpty())
        {
            return left;
        }
        return left + " — " + right;
    }

    private static String formatDuration(long millis)
    {
        if (millis <= 0L)
        {
            return "--";
        }

        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        return hours > 0L
            ? String.format("%d:%02d:%02d", hours, minutes, seconds)
            : String.format("%d:%02d", minutes, seconds);
    }

    private static final class HistoryRow
    {
        private final String id;
        private final boolean encounter;
        private final Instant timestamp;
        private final String bossName;
        private final String result;
        private final String duration;
        private final String source;
        private final String details;
        private final boolean reversible;

        private HistoryRow(
            String id,
            boolean encounter,
            Instant timestamp,
            String bossName,
            String result,
            String duration,
            String source,
            String details,
            boolean reversible)
        {
            this.id = id;
            this.encounter = encounter;
            this.timestamp = timestamp;
            this.bossName = bossName == null ? "" : bossName;
            this.result = result == null ? "" : result;
            this.duration = duration == null ? "--" : duration;
            this.source = source == null ? "" : source;
            this.details = details == null ? "" : details;
            this.reversible = reversible;
        }

        private Instant getTimestamp()
        {
            return timestamp;
        }
    }
}
