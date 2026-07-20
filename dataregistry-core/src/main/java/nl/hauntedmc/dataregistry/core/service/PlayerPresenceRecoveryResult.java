package nl.hauntedmc.dataregistry.core.service;

/**
 * Summary of stale player-presence state reconciled during startup.
 *
 * @param playtimeSegmentsClosed open playtime segment rows closed with the recovery reason.
 * @param sessionsClosed        open session rows closed at their last durable activity timestamp.
 * @param sessionVisitsClosed   open session-visit rows closed at their recovered session timestamp.
 * @param onlineStatusesCleared online-status rows changed from online to offline.
 * @param activitySummariesUpdated activity summary rows updated with recovered logout information.
 * @param connectionInfosUpdated connection info rows updated with recovered disconnect information.
 */
public record PlayerPresenceRecoveryResult(
        int playtimeSegmentsClosed,
        int sessionsClosed,
        int sessionVisitsClosed,
        int onlineStatusesCleared,
        int activitySummariesUpdated,
        int connectionInfosUpdated
) {

    public static PlayerPresenceRecoveryResult empty() {
        return new PlayerPresenceRecoveryResult(0, 0, 0, 0, 0, 0);
    }

    public PlayerPresenceRecoveryResult {
        requireNonNegative(playtimeSegmentsClosed, "playtimeSegmentsClosed");
        requireNonNegative(sessionsClosed, "sessionsClosed");
        requireNonNegative(sessionVisitsClosed, "sessionVisitsClosed");
        requireNonNegative(onlineStatusesCleared, "onlineStatusesCleared");
        requireNonNegative(activitySummariesUpdated, "activitySummariesUpdated");
        requireNonNegative(connectionInfosUpdated, "connectionInfosUpdated");
    }

    /**
     * Returns whether startup found and repaired stale state from an unclean previous shutdown.
     */
    public boolean recoveredAnyState() {
        return playtimeSegmentsClosed > 0
                || sessionsClosed > 0
                || sessionVisitsClosed > 0
                || onlineStatusesCleared > 0
                || activitySummariesUpdated > 0
                || connectionInfosUpdated > 0;
    }

    private static void requireNonNegative(int value, String fieldName) {
        if (value < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative.");
        }
    }
}
