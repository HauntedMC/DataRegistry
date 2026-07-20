package nl.hauntedmc.dataregistry.core.persistence.entity;

/**
 * Durable player lifecycle event types emitted by DataRegistry's outbox.
 */
public enum PlayerLifecycleOutboxEventType {
    LOGIN,
    TRANSFER,
    DISCONNECT
}
