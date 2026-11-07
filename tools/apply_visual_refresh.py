"""One-time guarded UI wiring for the visual release; removed before committing."""
from pathlib import Path
import re

root = Path('src/main/java/com/bossdeathtracker')
def once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('Expected exactly one anchor: ' + old[:100])
    return text.replace(old, new, 1)

def region(text, start, end, replacement):
    a = text.index(start)
    b = text.index(end, a + len(start))
    return text[:a] + replacement + '\n\n' + text[b:]

p = root / 'BossDeathTrackerPanel.java'
s = p.read_text(encoding='utf-8')
s = once(s, 'import java.net.URL;\n', '')
s = region(s, '    private static ImageIcon loadIcon(String name)',
           '    private static final DateTimeFormatter EVENT_TIME_FORMAT', '')
names = {'sword':'SWORDS','skull':'SKULL','trophy':'TROPHY','chart':'CHART',
         'search':'SEARCH','details':'DETAILS','check':'CHECK','sync':'SYNC','plus':'PLUS','book':'BOOK'}
for name, symbol in names.items():
    file = 'swords' if name == 'sword' else name
    s = once(s, f'private final ImageIcon {name}Icon = loadIcon("{file}.png");',
             f'private final ImageIcon {name}Icon = BossKdIcons.icon(BossKdIcons.Symbol.{symbol}, 16);')
s = once(s, '        body.add(buildHeader());\n        body.add(Box.createVerticalStrut(8));\n', '')
s = once(s, '        add(tabs, BorderLayout.CENTER);',
            '        add(buildHeader(), BorderLayout.NORTH);\n        add(tabs, BorderLayout.CENTER);')
s = region(s, '    private JPanel buildHeader()', '    private JPanel buildSummary()',
    '    private JPanel buildHeader()\n    {\n        return BossKdVisuals.createHeader();\n    }')
s = once(s, '        JTabbedPane tabs = new JTabbedPane();',
            '        JTabbedPane tabs = new JTabbedPane();\n        tabs.setFont(tabs.getFont().deriveFont(10.5f));')
for title, component, symbol in [('Bosses','bossesTab','SWORDS'),('History','historyPanel','HISTORY'),('Data','dataPanel','DATA')]:
    s = once(s, f'tabs.addTab("{title}", {component});',
             f'tabs.addTab("{title}", BossKdIcons.icon(BossKdIcons.Symbol.{symbol}, 14), {component});')
for old, new in [
    ('buildInsightRow(detailsIcon, "Attempts",', 'buildInsightRow(BossKdIcons.icon(BossKdIcons.Symbol.TARGET, 16), "Attempts",'),
    ('buildInsightRow(trophyIcon, "Win rate",', 'buildInsightRow(checkIcon, "Win rate",'),
    ('buildInsightRow(trophyIcon, "Personal best",', 'buildInsightRow(BossKdIcons.icon(BossKdIcons.Symbol.TIMER, 16), "Personal best",'),
    ('buildInsightRow(chartIcon, "Average kill",', 'buildInsightRow(BossKdIcons.icon(BossKdIcons.Symbol.TIMER, 16), "Average kill",'),
    ('buildInsightRow(swordIcon, "Current streak",', 'buildInsightRow(BossKdIcons.icon(BossKdIcons.Symbol.FLAME, 16), "Current streak",')]:
    s = once(s, old, new)
s = once(s, 'modeButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));',
            'modeButtons.setMaximumSize(new Dimension(Integer.MAX_VALUE, 49));')
s = once(s, '        button.setIconTextGap(4);\n        button.setMinimumSize(new Dimension(0, 31));\n        button.setPreferredSize(new Dimension(0, 31));\n        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31));',
    '        button.setIconTextGap(2);\n        button.setVerticalTextPosition(SwingConstants.BOTTOM);\n        button.setHorizontalTextPosition(SwingConstants.CENTER);\n        button.setFont(button.getFont().deriveFont(10f));\n        button.setMinimumSize(new Dimension(0, 47));\n        button.setPreferredSize(new Dimension(0, 47));\n        button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 47));')
s = once(s, '            button.setIcon(chartIcon);',
            '            button.setIcon(BossKdIcons.icon(BossKdIcons.Symbol.TIMER, 16));')
s = once(s, '        JButton favoriteButton = new JButton(boss.isFavorite() ? "★" : "☆");',
    '        JButton favoriteButton = new JButton(BossKdIcons.icon(\n'
    '            boss.isFavorite() ? BossKdIcons.Symbol.FAVORITE : BossKdIcons.Symbol.FAVORITE_OFF, 16));\n'
    '        favoriteButton.getAccessibleContext().setAccessibleName(\n'
    '            boss.isFavorite() ? "Remove from favorites" : "Add to favorites");')
p.write_text(s, encoding='utf-8')

p = root / 'BossDeathTrackerPlugin.java'
s = p.read_text(encoding='utf-8')
for imp in ['import java.awt.Color;\n', 'import java.awt.Graphics2D;\n', 'import java.awt.RenderingHints;\n']:
    s = once(s, imp, '')
s = once(s, '.tooltip("Boss Death Tracker")', '.tooltip("Boss KD Tracker " + BossKdVisuals.badgeText())')
s, count = re.subn(r'(?s)    private static BufferedImage createIcon\(\)\n    \{.*?\n    \}\n\}',
    '    private static BufferedImage createIcon()\n    {\n        return BossKdIcons.image(BossKdIcons.Symbol.EMBLEM, 16);\n    }\n}', s)
assert count == 1, 'Missing toolbar icon method'
p.write_text(s, encoding='utf-8')

p = root / 'BossDataPanel.java'
s = p.read_text(encoding='utf-8')
for text, symbol in [('Export Active Profile...', 'EXPORT'), ('Import Profile...', 'IMPORT'), ('Synchronize Official Definitions', 'SYNC'), ('Undo Last Count Record', 'UNDO')]:
    replacement_text = 'Sync Official Definitions' if symbol == 'SYNC' else text
    s = once(s, f'new JButton("{text}")', f'new JButton("{replacement_text}", BossKdIcons.icon(BossKdIcons.Symbol.{symbol}, 16))')
for name in ['exportButton', 'importButton', 'synchronizeButton', 'undoButton']:
    s = once(s, f'        panel.add({name});',
        f'        {name}.setFont({name}.getFont().deriveFont(10.5f));\n'
        f'        {name}.setMargin(new java.awt.Insets(3, 4, 3, 4));\n'
        f'        panel.add({name});')
p.write_text(s, encoding='utf-8')

p = Path('README.md')
s = p.read_text(encoding='utf-8')
section = '''## 0.11.1 visual refresh

- Original crowned-skull shield replaces the plain toolbar skull.
- Twenty-one cached, multi-resolution symbols replace the older PNG/glyph visuals.
- Crossed swords, skull, trophy, stopwatch, target, flame, history, data, favorite,
  sync, import/export, and undo controls now have distinct silhouettes.
- The persistent header reads `v0.11.1 / ICONS 01` on Bosses, History, and Data.
- The toolbar tooltip also shows the running build identity. This is embedded in
  the plugin, not inferred from the profile schema or historical data.
- Summary buttons stack their icon above the label so four controls fit the sidebar.
- Kill/death logic, chat formatting, and stored character data are unchanged.
- Build the new code or install the corresponding Plugin Hub update to see it.
  An old feature/test launcher remains old even if GitHub main has been updated.

'''
s = once(s, '## 0.11.0 interface and data changes', section + '## 0.11.0 interface and data changes')
p.write_text(s, encoding='utf-8')
assert "version = '0.11.1'" in Path('build.gradle').read_text()
assert 'version=0.11.1' in Path('runelite-plugin.properties').read_text()
assert Path('src/main/resources/bosskd/build.properties').read_text().startswith('version=0.11.1')
print('Applied visual-only UI changes; version metadata agrees.')
