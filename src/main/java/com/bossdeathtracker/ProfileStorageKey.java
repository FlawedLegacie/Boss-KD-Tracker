package com.bossdeathtracker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Converts RuneLite's profile key into a filesystem-safe, non-identifying
 * directory name. The original profile key is never written to disk.
 */
final class ProfileStorageKey
{
    private static final int HEX_LENGTH = 24;

    private ProfileStorageKey()
    {
    }

    static String from(String profileKey)
    {
        if (profileKey == null || profileKey.trim().isEmpty())
        {
            return "legacy";
        }

        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                profileKey.trim().getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash)
            {
                hex.append(String.format("%02x", value & 0xff));
            }

            return "profile-" + hex.substring(0, HEX_LENGTH);
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }
}
