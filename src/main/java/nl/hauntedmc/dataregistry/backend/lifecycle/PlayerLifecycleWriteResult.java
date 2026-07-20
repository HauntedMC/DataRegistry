package nl.hauntedmc.dataregistry.backend.lifecycle;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.Optional;

/**
 * Typed result returned by {@link PlayerLifecycleWriter} after a lifecycle command is handled.
 *
 * @param status   command persistence outcome.
 * @param eventId  idempotency key supplied by the command.
 * @param identity player identity snapshot when available.
 * @param failure  failure that prevented a durable write.
 */
public record PlayerLifecycleWriteResult(
        PlayerLifecycleWriteStatus status,
        String eventId,
        PlayerIdentity identity,
        Throwable failure
) {

    public static PlayerLifecycleWriteResult success(String eventId, PlayerIdentity identity) {
        return new PlayerLifecycleWriteResult(PlayerLifecycleWriteStatus.SUCCESS, eventId, identity, null);
    }

    public static PlayerLifecycleWriteResult duplicate(String eventId, PlayerIdentity identity) {
        return new PlayerLifecycleWriteResult(PlayerLifecycleWriteStatus.DUPLICATE, eventId, identity, null);
    }

    public static PlayerLifecycleWriteResult failure(
            String eventId,
            PlayerLifecycleWriteStatus status,
            Throwable failure
    ) {
        if (status == PlayerLifecycleWriteStatus.SUCCESS || status == PlayerLifecycleWriteStatus.DUPLICATE) {
            throw new IllegalArgumentException("Failure result requires a failure status.");
        }
        return new PlayerLifecycleWriteResult(status, eventId, null, failure);
    }

    /**
     * Returns whether the command was durably applied or had already been applied.
     */
    public boolean succeeded() {
        return status == PlayerLifecycleWriteStatus.SUCCESS || status == PlayerLifecycleWriteStatus.DUPLICATE;
    }

    /**
     * Returns the identity snapshot when the lifecycle command resolved a player row.
     */
    public Optional<PlayerIdentity> identityOptional() {
        return Optional.ofNullable(identity);
    }
}
