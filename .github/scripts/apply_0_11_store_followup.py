from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"{label} not found")
    return text.replace(old, new, 1)


path = Path("src/main/java/com/bossdeathtracker/BossDeathTrackerStore.java")
source = path.read_text(encoding="utf-8")

source = replace_once(
    source,
    """        data.getEvents().add(TrackerEvent.createUndo(
            event,
            "Undo history event " + event.getId()));

        if (event.getEncounterId() != null)
        {
            EncounterAttempt attempt = findAttemptLocked(event.getEncounterId());
            if (attempt != null)
            {
                attempt.voidAttempt("Count record undone from history");
            }
        }

        BossProfile boss = findBoss(event.getBossId()).orElse(null);
""",
    """        if (event.getEncounterId() != null)
        {
            return voidEncounter(event.getEncounterId());
        }

        data.getEvents().add(TrackerEvent.createUndo(
            event,
            "Undo history event " + event.getId()));

        BossProfile boss = findBoss(event.getBossId()).orElse(null);
""",
    "encounter undo delegation",
)

source = replace_once(
    source,
    """        if (imported == null)
        {
            throw new IOException("The selected file contained no tracker data.");
        }

        imported.migrateToCurrent();
""",
    """        if (imported == null)
        {
            throw new IOException("The selected file contained no tracker data.");
        }
        if (imported.getSchemaVersion() > TrackerData.CURRENT_SCHEMA_VERSION)
        {
            throw new IOException(
                "This profile was created by a newer Boss KD Tracker version.");
        }

        imported.migrateToCurrent();
""",
    "future schema validation",
)

path.write_text(source, encoding="utf-8")
