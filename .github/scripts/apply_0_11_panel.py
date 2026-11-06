from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} not found")
    return text.replace(old, new, 1)


path = Path("src/main/java/com/bossdeathtracker/BossDeathTrackerPanel.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    "import javax.swing.JButton;\nimport javax.swing.ImageIcon;",
    "import javax.swing.JButton;\nimport javax.swing.JCheckBox;\nimport javax.swing.ImageIcon;",
    "JCheckBox import",
)
source = replace_once(
    source,
    "import javax.swing.SwingConstants;\nimport javax.swing.SwingUtilities;",
    "import javax.swing.SwingConstants;\nimport javax.swing.SwingUtilities;\nimport javax.swing.JTabbedPane;",
    "JTabbedPane import",
)

header_enum = """    private enum HeaderMode
    {
        CURRENT,
        SESSION,
        NEMESIS,
        RECORDS
    }
"""
sort_enum = header_enum + """
    private enum SortMode
    {
        RECENT("Recent / Active"),
        NAME("Name"),
        KILLS("Kills"),
        DEATHS("Deaths"),
        ATTEMPTS("Attempts"),
        WIN_RATE("Win rate");

        private final String displayName;

        SortMode(String displayName)
        {
            this.displayName = displayName;
        }

        @Override
        public String toString()
        {
            return displayName;
        }
    }
"""
source = replace_once(source, header_enum, sort_enum, "SortMode enum")

field_anchor = """    private final JTextField searchField = new JTextField();
    private final JPanel bossList = new JPanel();
    private String activeBossId;
"""
field_replacement = """    private final JTextField searchField = new JTextField();
    private final JCheckBox favoritesOnly = new JCheckBox("Favorites only");
    private final JComboBox<String> categoryFilter = new JComboBox<>();
    private final JComboBox<SortMode> sortMode =
        new JComboBox<>(SortMode.values());
    private final JPanel bossList = new JPanel();
    private BossHistoryPanel historyPanel;
    private BossDataPanel dataPanel;
    private boolean updatingFilterOptions;
    private String activeBossId;
"""
source = replace_once(source, field_anchor, field_replacement, "filter fields")

source = replace_once(
    source,
    """        add(body, BorderLayout.NORTH);
        add(bossScrollPane, BorderLayout.CENTER);

        JButton addBossButton = new JButton("Add Boss", plusIcon);
""",
    """        JButton addBossButton = new JButton("Add Boss", plusIcon);
""",
    "direct body add",
)
source = replace_once(
    source,
    """        footer.add(syncKillsButton, BorderLayout.NORTH);
        footer.add(footerButtons, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        refresh();
""",
    """        footer.add(syncKillsButton, BorderLayout.NORTH);
        footer.add(footerButtons, BorderLayout.CENTER);

        JPanel bossesTab = new JPanel(new BorderLayout());
        bossesTab.setBackground(ColorScheme.DARK_GRAY_COLOR);
        bossesTab.add(body, BorderLayout.NORTH);
        bossesTab.add(bossScrollPane, BorderLayout.CENTER);
        bossesTab.add(footer, BorderLayout.SOUTH);

        historyPanel = new BossHistoryPanel(store, this::refresh);
        dataPanel = new BossDataPanel(store, this::refresh);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Bosses", bossesTab);
        tabs.addTab("History", historyPanel);
        tabs.addTab("Data", dataPanel);
        tabs.addChangeListener(event ->
        {
            if (tabs.getSelectedComponent() == historyPanel)
            {
                historyPanel.refresh();
            }
            else if (tabs.getSelectedComponent() == dataPanel)
            {
                dataPanel.refresh();
            }
        });

        add(tabs, BorderLayout.CENTER);
        refresh();
""",
    "tabbed layout",
)

refresh_start = source.index("    public void refresh()")
refresh_end = source.index("    private int bossDisplayPriority", refresh_start)
refresh_method = """    public void refresh()
    {
        if (!SwingUtilities.isEventDispatchThread())
        {
            SwingUtilities.invokeLater(this::refresh);
            return;
        }

        updateInsightSummary();
        updateFilterOptions();
        bossList.removeAll();

        List<BossProfile> bosses = store.searchBosses(searchField.getText());
        String selectedCategory = (String) categoryFilter.getSelectedItem();

        bosses.removeIf(boss ->
            (favoritesOnly.isSelected() && !boss.isFavorite())
                || (selectedCategory != null
                    && !"All categories".equals(selectedCategory)
                    && !selectedCategory.equals(boss.getCategory())));

        bosses.sort(this::compareBosses);

        if (bosses.isEmpty())
        {
            JLabel empty = new JLabel(
                "<html><center>No bosses match the current filters.</center></html>");
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

        if (historyPanel != null)
        {
            historyPanel.refresh();
        }
        if (dataPanel != null)
        {
            dataPanel.refresh();
        }
    }

    private void updateFilterOptions()
    {
        String selectedCategory = (String) categoryFilter.getSelectedItem();
        updatingFilterOptions = true;
        categoryFilter.removeAllItems();
        categoryFilter.addItem("All categories");

        List<String> categories = store.getBosses().stream()
            .map(BossProfile::getCategory)
            .filter(value -> value != null && !value.trim().isEmpty())
            .distinct()
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .collect(Collectors.toList());

        for (String category : categories)
        {
            categoryFilter.addItem(category);
        }

        if (selectedCategory != null)
        {
            categoryFilter.setSelectedItem(selectedCategory);
        }
        if (categoryFilter.getSelectedIndex() < 0)
        {
            categoryFilter.setSelectedIndex(0);
        }
        updatingFilterOptions = false;
    }

    private int compareBosses(BossProfile left, BossProfile right)
    {
        if (left.isFavorite() != right.isFavorite())
        {
            return left.isFavorite() ? -1 : 1;
        }

        SortMode selected = (SortMode) sortMode.getSelectedItem();
        if (selected == null)
        {
            selected = SortMode.RECENT;
        }

        switch (selected)
        {
            case KILLS:
                int killCompare = Integer.compare(
                    store.getKillCount(right.getId()),
                    store.getKillCount(left.getId()));
                if (killCompare != 0)
                {
                    return killCompare;
                }
                break;
            case DEATHS:
                int deathCompare = Integer.compare(
                    store.getDeathCount(right.getId()),
                    store.getDeathCount(left.getId()));
                if (deathCompare != 0)
                {
                    return deathCompare;
                }
                break;
            case ATTEMPTS:
                int attemptCompare = Integer.compare(
                    store.getBossStatistics(right.getId()).getAttempts(),
                    store.getBossStatistics(left.getId()).getAttempts());
                if (attemptCompare != 0)
                {
                    return attemptCompare;
                }
                break;
            case WIN_RATE:
                int winCompare = Double.compare(
                    store.getBossStatistics(right.getId()).getWinRate(),
                    store.getBossStatistics(left.getId()).getWinRate());
                if (winCompare != 0)
                {
                    return winCompare;
                }
                break;
            case RECENT:
                int leftPriority = bossDisplayPriority(left.getId());
                int rightPriority = bossDisplayPriority(right.getId());
                if (leftPriority != rightPriority)
                {
                    return Integer.compare(leftPriority, rightPriority);
                }
                break;
            case NAME:
            default:
                break;
        }

        return left.getDisplayName().compareToIgnoreCase(right.getDisplayName());
    }

"""
source = source[:refresh_start] + refresh_method + source[refresh_end:]

search_start = source.index("    private JPanel buildSearch()")
search_end = source.index("    private JPanel buildBossCard", search_start)
search_method = """    private JPanel buildSearch()
    {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(ColorScheme.DARK_GRAY_COLOR);
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 72));

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
        searchBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));

        searchField.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 7));
        searchBox.add(searchGlyph, BorderLayout.WEST);
        searchBox.add(searchField, BorderLayout.CENTER);

        JPanel filters = new JPanel(new GridLayout(1, 3, 4, 0));
        filters.setOpaque(false);
        filters.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        favoritesOnly.setOpaque(false);
        favoritesOnly.setForeground(Color.LIGHT_GRAY);
        filters.add(favoritesOnly);
        filters.add(categoryFilter);
        filters.add(sortMode);

        panel.add(searchBox);
        panel.add(Box.createVerticalStrut(5));
        panel.add(filters);

        searchField.getDocument().addDocumentListener(new DocumentListener()
        {
            @Override
            public void insertUpdate(DocumentEvent event)
            {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent event)
            {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent event)
            {
                refresh();
            }
        });

        favoritesOnly.addActionListener(event -> refresh());
        categoryFilter.addActionListener(event ->
        {
            if (!updatingFilterOptions)
            {
                refresh();
            }
        });
        sortMode.addActionListener(event -> refresh());

        return panel;
    }

"""
source = source[:search_start] + search_method + source[search_end:]

source = replace_once(
    source,
    """        JLabel name = new JLabel(boss.getDisplayName());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        name.setAlignmentX(Component.LEFT_ALIGNMENT);
""",
    """        JPanel nameRow = new JPanel(new BorderLayout(4, 0));
        nameRow.setOpaque(false);
        nameRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        nameRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 25));

        JLabel name = new JLabel(boss.getDisplayName());
        name.setForeground(Color.WHITE);
        name.setFont(name.getFont().deriveFont(Font.BOLD));

        JButton favoriteButton = new JButton(boss.isFavorite() ? "★" : "☆");
        favoriteButton.setToolTipText(
            boss.isFavorite() ? "Remove from favorites" : "Add to favorites");
        favoriteButton.setMargin(new Insets(0, 4, 0, 4));
        favoriteButton.addActionListener(event ->
        {
            store.setFavorite(boss.getId(), !boss.isFavorite());
            refresh();
        });

        nameRow.add(name, BorderLayout.CENTER);
        nameRow.add(favoriteButton, BorderLayout.EAST);
""",
    "favorite name row",
)
source = replace_once(
    source,
    """        card.add(name);
        card.add(Box.createVerticalStrut(3));
""",
    """        card.add(nameRow);
        card.add(Box.createVerticalStrut(3));
""",
    "card name row",
)

source = replace_once(
    source,
    """        JPanel correctionRow = new JPanel(new GridLayout(1, 2, 4, 0));
        JButton minusDeath = new JButton("- Death");
        JButton minusKill = new JButton("- Kill");
""",
    """        JPanel correctionRow = new JPanel(new GridLayout(1, 3, 4, 0));
        JButton minusDeath = new JButton("- Death");
        JButton minusKill = new JButton("- Kill");
        JButton undoLast = new JButton("Undo Last");
""",
    "details undo button",
)
source = replace_once(
    source,
    """        correctionRow.add(minusDeath);
        correctionRow.add(minusKill);
        correctionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
""",
    """        undoLast.addActionListener(event ->
        {
            BossDeathTrackerStore.UndoResult undoResult =
                store.undoLastEvent(boss.getId());
            JOptionPane.showMessageDialog(
                this,
                undoResult.getDescription(),
                "Undo Last Record",
                undoResult.isSuccess()
                    ? JOptionPane.INFORMATION_MESSAGE
                    : JOptionPane.WARNING_MESSAGE);
            if (undoResult.isSuccess())
            {
                refresh();
            }
        });

        correctionRow.add(minusDeath);
        correctionRow.add(minusKill);
        correctionRow.add(undoLast);
        correctionRow.setAlignmentX(Component.LEFT_ALIGNMENT);
""",
    "details undo action",
)

# Built-in definitions are protected from deletion.
source = replace_once(
    source,
    """        JButton delete = new JButton("Delete Boss");
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
""",
    """        if (boss.isCustomBoss())
        {
            JButton delete = new JButton("Delete Custom Boss");
            delete.setAlignmentX(Component.LEFT_ALIGNMENT);
            delete.addActionListener(event ->
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
        }
        else
        {
            JLabel protectedDefinition = new JLabel(
                "Built-in definition — use the Data tab to synchronize updates.");
            protectedDefinition.setForeground(Color.LIGHT_GRAY);
            protectedDefinition.setAlignmentX(Component.LEFT_ALIGNMENT);
            panel.add(protectedDefinition);
        }
""",
    "protect built-in delete UI",
)

path.write_text(source, encoding="utf-8")
