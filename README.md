# Boss KD Tracker

Boss KD Tracker is a RuneLite plugin for tracking boss kills, deaths, K/D ratios, attempts, records, and boss encounters.

## Features

- Automatic boss death attribution during recognized encounters
- Automatic boss kill detection with player-death race protection
- Historical kill-count synchronization from RuneLite profile data
- Manual `+ Death` and `+ Kill` corrections
- `!KD <boss>` chat command with shorthand and contextual name resolution
- Current, Nemesis, and Records summary views
- Searchable boss catalog with built-in NPC IDs and multi-form boss support
- Manual boss creation for new or unsupported bosses
- Quest boss support
- Local event history

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
