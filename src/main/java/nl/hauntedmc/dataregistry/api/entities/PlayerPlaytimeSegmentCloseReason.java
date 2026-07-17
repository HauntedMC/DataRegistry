package nl.hauntedmc.dataregistry.api.entities;

/**
 * Reason an active playtime segment stopped accruing.
 */
public enum PlayerPlaytimeSegmentCloseReason {
    SERVER_SWITCH,
    STOP_TRACKING,
    DISCONNECT,
    RECOVERY
}
