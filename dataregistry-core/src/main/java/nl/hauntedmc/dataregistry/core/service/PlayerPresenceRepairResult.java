package nl.hauntedmc.dataregistry.core.service;

/**
 * Summary of an administrator-triggered live-presence refresh.
 *
 * @param onlineStatusesRefreshed durable online-status rows refreshed from live proxy connections.
 * @param livePlayersMissing       live proxy players that did not yet have a durable player row.
 */
public record PlayerPresenceRepairResult(
        int onlineStatusesRefreshed,
        int livePlayersMissing
) {

    public PlayerPresenceRepairResult {
        if (onlineStatusesRefreshed < 0 || livePlayersMissing < 0) {
            throw new IllegalArgumentException("Presence repair counts must not be negative.");
        }
    }
}
