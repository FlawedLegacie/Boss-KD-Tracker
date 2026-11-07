package com.bossdeathtracker;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.runelite.client.ui.ColorScheme;

/**
 * Profile maintenance, import/export, and built-in-definition tools.
 */
final class BossDataPanel extends JPanel
{
    private final BossDeathTrackerStore store;
    private final Runnable onDataChanged;
    private final JLabel profileValue = new JLabel();
    private final JLabel schemaValue = new JLabel();
    private final JLabel bossesValue = new JLabel();
    private final JLabel eventsValue = new JLabel();
    private final JLabel attemptsValue = new JLabel();
    private final JTextArea guidance = new JTextArea();

    BossDataPanel(
        BossDeathTrackerStore store,
        Runnable onDataChanged)
    {
        super(new BorderLayout(6, 6));
        this.store = store;
        this.onDataChanged = onDataChanged;

        setBackground(ColorScheme.DARK_GRAY_COLOR);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBackground(ColorScheme.DARK_GRAY_COLOR);

        content.add(buildSummary());
        content.add(Box.createVerticalStrut(8));
        content.add(buildActions());
        content.add(Box.createVerticalStrut(8));

        guidance.setEditable(false);
        guidance.setLineWrap(true);
        guidance.setWrapStyleWord(true);
        guidance.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        guidance.setForeground(Color.LIGHT_GRAY);
        guidance.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        guidance.setText(
            "Exports contain the active character's complete Boss KD Tracker profile, "
                + "including bosses, count events, encounter attempts, favorites, and notes.\n\n"
                + "Imports are validated before replacing the active profile. The normal "
                + "rotating backup system preserves the previous profile data.\n\n"
                + "Synchronize Official Definitions adds new built-in bosses and merges "
                + "new official NPC IDs without deleting custom IDs, notes, favorites, "
                + "history, or verification state.");

        JScrollPane guidanceScroll = new JScrollPane(guidance);
        guidanceScroll.setBorder(BorderFactory.createTitledBorder("Data Safety"));
        guidanceScroll.setPreferredSize(new Dimension(0, 180));
        content.add(guidanceScroll);

        add(content, BorderLayout.NORTH);
        refresh();
    }

    void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        profileValue.setText(store.getActiveStorageKey());
        schemaValue.setText(Integer.toString(store.getSchemaVersion()));
        bossesValue.setText(Integer.toString(store.getBosses().size()));
        eventsValue.setText(Integer.toString(store.getAllEvents().size()));
        attemptsValue.setText(Integer.toString(store.getAllAttempts().size()));
    }

    private JPanel buildSummary()
    {
        JPanel panel = new JPanel(new GridLayout(5, 2, 6, 5));
        panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Active Profile"),
            BorderFactory.createEmptyBorder(6, 6, 6, 6)));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        addMetric(panel, "Storage key", profileValue);
        addMetric(panel, "Schema version", schemaValue);
        addMetric(panel, "Boss profiles", bossesValue);
        addMetric(panel, "Count events", eventsValue);
        addMetric(panel, "Encounter attempts", attemptsValue);
        return panel;
    }

    private JPanel buildActions()
    {
        JPanel panel = new JPanel(new GridLayout(4, 1, 0, 5));
        panel.setOpaque(false);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));

        JButton exportButton = new JButton("Export Active Profile...", BossKdIcons.icon(BossKdIcons.Symbol.EXPORT, 16));
        exportButton.addActionListener(event -> exportProfile());

        JButton importButton = new JButton("Import Profile...", BossKdIcons.icon(BossKdIcons.Symbol.IMPORT, 16));
        importButton.addActionListener(event -> importProfile());

        JButton synchronizeButton =
            new JButton("Sync Official Definitions", BossKdIcons.icon(BossKdIcons.Symbol.SYNC, 16));
        synchronizeButton.addActionListener(event -> synchronizeDefinitions());

        JButton undoButton = new JButton("Undo Last Count Record", BossKdIcons.icon(BossKdIcons.Symbol.UNDO, 16));
        undoButton.setToolTipText(
            "Append a correction for the newest reversible kill/death record");
        undoButton.addActionListener(event -> undoLastRecord());

        exportButton.setFont(exportButton.getFont().deriveFont(10.5f));
        exportButton.setMargin(new java.awt.Insets(3, 4, 3, 4));
        panel.add(exportButton);
        importButton.setFont(importButton.getFont().deriveFont(10.5f));
        importButton.setMargin(new java.awt.Insets(3, 4, 3, 4));
        panel.add(importButton);
        synchronizeButton.setFont(synchronizeButton.getFont().deriveFont(10.5f));
        synchronizeButton.setMargin(new java.awt.Insets(3, 4, 3, 4));
        panel.add(synchronizeButton);
        undoButton.setFont(undoButton.getFont().deriveFont(10.5f));
        undoButton.setMargin(new java.awt.Insets(3, 4, 3, 4));
        panel.add(undoButton);
        return panel;
    }

    private void exportProfile()
    {
        JFileChooser chooser = createJsonChooser();
        chooser.setSelectedFile(new File("boss-kd-tracker-profile.json"));

        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        File selected = ensureJsonExtension(chooser.getSelectedFile());

        if (selected.exists())
        {
            int overwrite = JOptionPane.showConfirmDialog(
                this,
                "Replace the existing file?\n" + selected,
                "Export Profile",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

            if (overwrite != JOptionPane.YES_OPTION)
            {
                return;
            }
        }

        try
        {
            store.exportCurrentProfile(selected.toPath());
            JOptionPane.showMessageDialog(
                this,
                "Profile exported successfully to:\n" + selected,
                "Export Profile",
                JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException | RuntimeException ex)
        {
            showError("Unable to export profile", ex);
        }
    }

    private void importProfile()
    {
        JFileChooser chooser = createJsonChooser();

        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION)
        {
            return;
        }

        Path source = chooser.getSelectedFile().toPath();
        int answer = JOptionPane.showConfirmDialog(
            this,
            "Import this profile into the active character?\n"
                + source
                + "\n\nThe current data will remain available through rotating backups.",
            "Import Profile",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);

        if (answer != JOptionPane.YES_OPTION)
        {
            return;
        }

        try
        {
            BossDeathTrackerStore.ImportResult result =
                store.importCurrentProfile(source);

            if (onDataChanged != null)
            {
                onDataChanged.run();
            }
            refresh();

            JOptionPane.showMessageDialog(
                this,
                "Import complete.\n\nBosses: " + result.getBosses()
                    + "\nCount events: " + result.getEvents()
                    + "\nEncounter attempts: " + result.getAttempts(),
                "Import Profile",
                JOptionPane.INFORMATION_MESSAGE);
        }
        catch (IOException | RuntimeException ex)
        {
            showError("Unable to import profile", ex);
        }
    }

    private void synchronizeDefinitions()
    {
        BossDeathTrackerStore.DefinitionSyncResult result =
            store.synchronizeDefinitions();

        if (onDataChanged != null)
        {
            onDataChanged.run();
        }
        refresh();

        String message =
            "Official definition synchronization complete.\n\n"
                + "Bosses added: " + result.getAdded()
                + "\nBosses updated: " + result.getUpdated()
                + "\nAlready current: " + result.getUnchanged()
                + "\nDuplicate NPC ID conflicts: " + result.getConflicts();

        if (result.getConflicts() > 0)
        {
            message +=
                "\n\nConflicts usually come from custom bosses sharing an official NPC ID. "
                    + "Review custom definitions in Boss Details.";
        }

        JOptionPane.showMessageDialog(
            this,
            message,
            "Synchronize Definitions",
            result.getConflicts() > 0
                ? JOptionPane.WARNING_MESSAGE
                : JOptionPane.INFORMATION_MESSAGE);
    }

    private void undoLastRecord()
    {
        BossDeathTrackerStore.UndoResult result = store.undoLastEvent(null);

        if (result.isSuccess() && onDataChanged != null)
        {
            onDataChanged.run();
        }
        refresh();

        JOptionPane.showMessageDialog(
            this,
            result.getDescription()
                + (result.getBossName().isEmpty()
                    ? ""
                    : "\nBoss: " + result.getBossName()),
            "Undo Last Count Record",
            result.isSuccess()
                ? JOptionPane.INFORMATION_MESSAGE
                : JOptionPane.WARNING_MESSAGE);
    }

    private static void addMetric(
        JPanel panel,
        String labelText,
        JLabel value)
    {
        JLabel label = new JLabel(labelText);
        label.setForeground(Color.LIGHT_GRAY);
        value.setForeground(Color.WHITE);
        value.setAlignmentX(Component.RIGHT_ALIGNMENT);
        panel.add(label);
        panel.add(value);
    }

    private static JFileChooser createJsonChooser()
    {
        JFileChooser chooser = new JFileChooser();
        chooser.setFileFilter(new FileNameExtensionFilter(
            "JSON files (*.json)",
            "json"));
        return chooser;
    }

    private static File ensureJsonExtension(File file)
    {
        if (file.getName().toLowerCase().endsWith(".json"))
        {
            return file;
        }

        return new File(file.getParentFile(), file.getName() + ".json");
    }

    private void showError(String title, Exception ex)
    {
        JOptionPane.showMessageDialog(
            this,
            title + ":\n" + ex.getMessage(),
            "Boss KD Tracker",
            JOptionPane.ERROR_MESSAGE);
    }
}
