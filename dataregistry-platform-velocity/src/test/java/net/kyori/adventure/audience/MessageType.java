package net.kyori.adventure.audience;

/**
 * Test-scope compatibility shim for Bukkit API signatures that still reference
 * the legacy Adventure message type removed from newer Adventure releases.
 */
public enum MessageType {
    CHAT,
    SYSTEM
}
