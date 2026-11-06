package com.bossdeathtracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProfileStorageKeyTest
{
    @Test
    void blankProfileUsesLegacyStorage()
    {
        assertEquals("legacy", ProfileStorageKey.from(null));
        assertEquals("legacy", ProfileStorageKey.from("   "));
    }

    @Test
    void profileKeysAreStableAndNonIdentifying()
    {
        String first = ProfileStorageKey.from("account-profile-key-1");
        String repeated = ProfileStorageKey.from("account-profile-key-1");
        String second = ProfileStorageKey.from("account-profile-key-2");

        assertEquals(first, repeated);
        assertNotEquals(first, second);
        assertTrue(first.startsWith("profile-"));
        assertFalse(first.contains("account-profile-key-1"));
        assertTrue(first.matches("profile-[0-9a-f]{24}"));
    }
}
