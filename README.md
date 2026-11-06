# Boss KD Tracker

Boss KD Tracker is a RuneLite plugin for tracking boss kills, deaths, K/D ratios, attempts, records, and boss encounters.

## Features

- Automatic boss death attribution during recognized encounters
- Multi-signal boss kill detection with player-death race protection
- Persisted encounter attempts with explicit outcomes
- Win rate, current/best streak, personal best, and average kill duration
- Current-session attempts, K/D, win rate, PB, average, and streaks
- Conservative double-KO recognition when KC confirms the kill
- Historical kill-count synchronization from RuneLite profile data
- Manual corrections and reversible history
- `!KD <boss>` chat command with shorthand and contextual name resolution
- Current, Session, Nemesis, and Records summary views
- Favorites, category filtering, and performance sorting
- Searchable boss catalog with built-in NPC IDs and multi-form boss support
- Validated profile import/export and automatic recovery backups
- Non-destructive official boss-definition synchronization
- Manual boss creation for new or unsupported bosses
- Quest boss support

## 0.11.0 interface and data changes

- The side panel is organized into Bosses, History, and Data tabs.
- Bosses can be favorited, filtered by category, and sorted by name, kills, deaths, attempts, win rate, or active/recent status.
- History uses a filterable table for encounter outcomes and manual/imported count records.
- A selected encounter or count record can be undone without deleting history: the plugin appends correction records and marks linked encounter statistics as voided.
- The active character profile can be exported to JSON or imported from a validated JSON backup.
- Imports reject unknown boss references, duplicate boss IDs, malformed data, and schemas created by a newer plugin version.
- Official definition synchronization adds missing bosses and merges new official NPC IDs while preserving custom IDs, notes, favorites, history, and verification state.
- Built-in definitions are protected from deletion; custom bosses remain fully editable and removable.
- Duplicate NPC ID conflicts are reported after definition synchronization.

## 0.10.0 encounter and statistics changes

- Every observed fight is stored as an encounter attempt with start time, end time, duration, outcome, NPC form, and completion signal.
- Outcomes include kill, death, double KO, abandoned attempt, and disconnect.
- NPC death, local-player death, and RuneLite KC changes are resolved together after a short event-order window.
- A player death plus an authoritative KC increase is recorded as a double KO; a boss-shaped despawn without KC confirmation remains a death.
- The side panel displays attempts, win rate, personal best, average kill time, and streaks.
- A Session view summarizes activity since the current login session began.
- Imported historical KC remains separate from observed attempt statistics; the plugin does not invent historical attempts or kill times.

## 0.9.1 reliability changes

- Data is isolated by RuneLite profile instead of sharing one file between every character.
- The old `tracker-data.json` file is migrated once into the first active character profile.
- Profile directory names use a SHA-256-derived identifier; the original RuneLite profile key is never written to disk.
- Three rotating backups are retained for every profile.
- Corrupt primary data automatically falls back to the newest readable backup.
- Writes are coalesced, atomic when supported, and flushed during shutdown.
- Duplicate automatic kill/death events within five seconds are suppressed.
- Java 11 compilation and JUnit regression tests run through GitHub Actions.

Profile data is stored under:

```text
~/.runelite/boss-death-tracker/profiles/profile-<hashed-id>/tracker-data.json
```

## Death tracking

Historical deaths are not guessed. Boss KD Tracker records deaths that it observes while running, and players can manually add older deaths when they know the correct value.

## Kill synchronization

Boss KD Tracker can import historical boss kill counts already known to RuneLite. Synchronization is idempotent: it only adds a missing difference and never lowers a higher total already recorded by the tracker.

## Development

This project targets Java 11 and the current RuneLite release.

```text
gradle clean test
gradle run
```

The development launcher is `com.bossdeathtracker.BossDeathTrackerPluginTest`.

## License

BSD 2-Clause License.
