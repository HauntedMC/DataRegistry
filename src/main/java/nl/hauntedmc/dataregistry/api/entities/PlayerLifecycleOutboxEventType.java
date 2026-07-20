package nl.hauntedmc.dataregistry.api.entities;

/**
 * Durable player lifecycle event types emitted by DataRegistry's outbox.
 */
public enum PlayerLifecycleOutboxEventType {
    LOGIN,
    TRANSFER,
    DISCONNECT
}
