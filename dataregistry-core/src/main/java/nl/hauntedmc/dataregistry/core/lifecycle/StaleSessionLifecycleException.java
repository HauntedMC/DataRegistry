package nl.hauntedmc.dataregistry.core.lifecycle;

/** Raised when a durable lifecycle command belongs to a session that has already been fenced. */
public final class StaleSessionLifecycleException extends RuntimeException {
    public StaleSessionLifecycleException(String operation, String playerUuid) {
        super("Rejected stale " + operation + " lifecycle command for " + playerUuid);
    }
}
