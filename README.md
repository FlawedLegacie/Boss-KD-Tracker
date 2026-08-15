# Boss KD Tracker

Boss KD Tracker is a RuneLite plugin for tracking boss kills, deaths, K/D ratios, nemeses, records, and boss encounters.

## Features

- Automatic boss death attribution during recognized encounters
- Automatic boss kill detection
- Historical kill-count synchronization from RuneLite profile data
- Manual `+ Death` and `+ Kill` corrections
- `!KD <boss>` chat command
- Current, Nemesis, and Records summary views
- Searchable boss catalog with built-in NPC IDs and multi-form boss support
- Manual boss creation for new or unsupported bosses
- Quest boss support
- Local per-profile statistics and event history

## Death tracking

Historical deaths are not guessed. Boss KD Tracker records deaths that it observes while running, and players can manually add older deaths when they know the correct value.

## Kill synchronization

Boss KD Tracker can import historical boss kill counts already known to RuneLite. Synchronization is idempotent: it only adds a missing difference and never lowers a higher total already recorded by the tracker.

## Development

This project targets Java 11 and the current RuneLite release. Run the Gradle `run` task to launch a RuneLite development client with the plugin loaded.

The test launcher is `com.bossdeathtracker.BossDeathTrackerPluginTest`.

## License

BSD 2-Clause License.
