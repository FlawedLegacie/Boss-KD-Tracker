package com.bossdeathtracker;

public enum SyncStatus
{
    MANUAL("Manual"),
    PARTIAL("Unverified"),
    SYNCED("Synced"),
    AUTO("Verified"),
    DISABLED("Disabled");

    private final String displayName;

    SyncStatus(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }
}
