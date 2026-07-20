package nl.hauntedmc.dataregistry.core.persistence.entity;

/**
 * Reason an active playtime segment stopped accruing.
 */
public enum PlayerPlaytimeSegmentCloseReason {
    SERVER_SWITCH,
    STOP_TRACKING,
    DISCONNECT,
    RECOVERY
}
