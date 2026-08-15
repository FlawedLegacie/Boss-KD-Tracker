# Boss Death Tracker

## Version 0.4.0

Boss Death Tracker now supports both automatic death attribution and automatic
kill detection.

### Automatic death

When the local player dies, the death is assigned to the currently active
recognized boss encounter and written as an `AUTOMATIC` ledger event.

### Automatic kill

When RuneLite fires `ActorDeath` for an NPC:

1. The NPC ID must be a known Boss Death Tracker ID.
2. An active encounter must already exist.
3. The dead NPC must resolve to the exact same boss profile as the active
   encounter.
4. The encounter must still be inside the stale-attribution timeout.
5. A `KILL +1 [AUTOMATIC]` ledger entry is written.
6. Encounter state is cleared, requiring a fresh interaction before another
   kill or death can be recorded.

This is deliberately stricter than simply counting every recognized NPC death.

### Settings

- Automatic deaths: enabled by default
- Automatic kills: enabled by default
- Confirm corrections: enabled by default

### Current features

- RuneLite sidebar panel
- Built-in boss/NPC ID registry
- Multi-ID boss phase support
- Automatic death attribution
- Automatic kill detection
- Manual +Death / +Kill
- Corrective -Death / -Kill
- Event source ledger
- Boss event history
- Search by name/category/NPC ID
- Manual custom boss creation
- Custom NPC ID entry
- Manual / Partial / Synced / Auto / Disabled states
- Local JSON persistence under `.runelite/boss-death-tracker/`

### Development launch

The included Gradle `run` task launches RuneLite with:

- `--developer-mode`
- `--debug`
- JVM assertions (`-ea`)

The test launcher is:

`com.bossdeathtracker.BossDeathTrackerPluginTest`


## Windows quick launch

No global Gradle installation is required.

From the extracted project root, either double-click:

`Run-BossDeathTracker.cmd`

or run:

```powershell
.\Run-BossDeathTracker.ps1
```

The launcher:

1. Verifies `java.exe` is available.
2. Downloads Gradle 8.10.2 from the official Gradle distribution service into
   `.gradle-bootstrap`.
3. Extracts it locally.
4. Runs the project's `run` task with stack traces enabled.
5. Leaves your machine's global Gradle installation untouched.

The first run requires internet access to download Gradle and RuneLite
dependencies.


## 0.4.2 compile fix

Restores the BossDeathTrackerStore methods required by the NPC-ID encounter engine:

- findBossByNpcId(int npcId)
- seedBuiltInDefinitions()
- seedBuiltInDefinitionsLocked()

This addresses the six `cannot find symbol` errors observed during the 0.4.1 compile test.


## 0.5.0 Quest Bosses

Adds the first dedicated Quest Bosses catalog:

- Elvarg — Dragon Slayer I
- Jungle Demon — Monkey Madness I
- Count Draynor — Vampyre Slayer
- Delrith — Demon Slayer
- Glough — Monkey Madness II
- Galvek — Dragon Slayer II
- Ranis Drakan — A Taste of Hope
- Fragment of Seren — Song of the Elves
- The Jormungand — The Fremennik Exiles
- Vanstrom Klause — Sins of the Father
- Balance Elemental — While Guthix Sleeps
- Lord Lowerniel Drakan — The Blood Moon Rises

Quest bosses use the same event ledger and automatic encounter engine as other
bosses. The new Drakan definition includes the current RuneLite NPC forms
associated with the 2026 quest content.

Built-in definitions are seeded into an existing tracker data file without
deleting existing statistics.
