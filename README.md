# Boss KD Tracker

Boss KD Tracker is a RuneLite plugin for tracking boss kills, deaths, K/D ratios, nemeses, records, and boss encounters.

## Features

- Automatic boss death attribution during recognized encounters
- Automatic boss kill detection
- Historical kill-count synchronization from RuneLite profile data
- Manual `+ Death` and `+ Kill` corrections
- Local-only `!KD <boss>` chat command with boss-name shortcuts
- Current, Nemesis, and Records summary views
- Searchable boss catalog with built-in NPC IDs and multi-form boss support
- Manual boss creation for new or unsupported bosses
- Quest boss support
- Local per-profile statistics and event history

## Death tracking

Historical deaths are not guessed. Boss KD Tracker records deaths that it observes while running, and players can manually add older deaths when they know the correct value.

## Kill synchronization

Boss KD Tracker can import historical boss kill counts already known to RuneLite. Synchronization is idempotent: it only adds a missing difference and never lowers a higher total already recorded by the tracker.

## Local !KD command

Type `!KD <boss>` (for example, `!kd whisp`) to display your tracked statistics:

```text
Whisperer - Kills: 100 | Deaths: 5 | K/D: 20.00
```

The result is visible only on your own client. RuneLite's `ChatCommandManager`
consumes the command, and `ChatMessageManager` queues the formatted local result.
Neither the command nor the result is sent to other players. The plugin does not
insert text into the chatbox, resume sending the command, or call chat-send scripts.
There is no sharing server, network upload, or sharing setting.

Boss names and values use RuneLite's highlight colors. Shortcuts such as `whisp`,
`bandos`, and `vork` are supported. Ambiguous names use the active encounter or a
recent matching encounter (about 60 seconds); otherwise the command lists matches.
Zero deaths displays `Perfect` when there are kills, or `0.00` when both are zero.

## Development

This project targets Java 11 and the current RuneLite release. Run the Gradle `run` task to launch a RuneLite development client with the plugin loaded.

The test launcher is `com.bossdeathtracker.BossDeathTrackerPluginTest`.

## License

BSD 2-Clause License.
