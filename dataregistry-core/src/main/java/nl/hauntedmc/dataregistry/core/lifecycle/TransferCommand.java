package nl.hauntedmc.dataregistry.core.lifecycle;

import nl.hauntedmc.dataregistry.api.session.SessionFence;

import java.time.Instant;

/**
 * Immutable command describing a player backend-server transfer lifecycle write.
 *
 * @param eventId    idempotency key for this platform lifecycle event.
 * @param playerUuid canonical player UUID string.
 * @param username   username observed by the platform.
 * @param serverName backend server name observed after the transfer.
 * @param occurredAt timestamp assigned when the platform event was observed.
 */
public record TransferCommand(
        String eventId,
        String playerUuid,
        String username,
        String serverName,
        Instant occurredAt,
        SessionFence sessionFence
) {

    public TransferCommand {
        eventId = LoginCommand.normalizeEventId(eventId);
        playerUuid = LoginCommand.requireUuid(playerUuid);
        username = LoginCommand.requireText(username, "username");
        serverName = LoginCommand.requireText(serverName, "serverName");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
        java.util.Objects.requireNonNull(sessionFence, "sessionFence");
    }

    public static TransferCommand create(String playerUuid, String username, String serverName,
                                         SessionFence sessionFence) {
        return new TransferCommand(
                LoginCommand.newEventId("transfer", playerUuid),
                playerUuid,
                username,
                serverName,
                Instant.now(),
                sessionFence
        );
    }
}
