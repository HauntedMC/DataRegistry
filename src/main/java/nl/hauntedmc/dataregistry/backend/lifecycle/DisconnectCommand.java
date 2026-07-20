package nl.hauntedmc.dataregistry.backend.lifecycle;

import java.time.Instant;

/**
 * Immutable command describing a player disconnect lifecycle write.
 *
 * @param eventId    idempotency key for this platform lifecycle event.
 * @param playerUuid canonical player UUID string.
 * @param username   username observed by the platform.
 * @param occurredAt timestamp assigned when the platform event was observed.
 */
public record DisconnectCommand(
        String eventId,
        String playerUuid,
        String username,
        Instant occurredAt
) {

    public DisconnectCommand {
        eventId = LoginCommand.normalizeEventId(eventId);
        playerUuid = LoginCommand.requireUuid(playerUuid);
        username = LoginCommand.requireText(username, "username");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static DisconnectCommand create(String playerUuid, String username) {
        return new DisconnectCommand(
                LoginCommand.newEventId("disconnect", playerUuid),
                playerUuid,
                username,
                Instant.now()
        );
    }
}
