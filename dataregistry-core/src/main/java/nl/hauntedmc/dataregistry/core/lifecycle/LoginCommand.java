package nl.hauntedmc.dataregistry.core.lifecycle;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable command describing a single player login lifecycle write.
 *
 * @param eventId     idempotency key for this platform lifecycle event.
 * @param playerUuid  canonical player UUID string.
 * @param username    username observed by the platform.
 * @param ipAddress   remote IP address, or {@code null} when unavailable or not retained.
 * @param virtualHost virtual host, or {@code null} when unavailable or not retained.
 * @param occurredAt  timestamp assigned when the platform event was observed.
 */
public record LoginCommand(
        String eventId,
        String playerUuid,
        String username,
        String ipAddress,
        String virtualHost,
        Instant occurredAt
) {

    public LoginCommand {
        eventId = normalizeEventId(eventId);
        playerUuid = requireUuid(playerUuid);
        username = requireText(username, "username");
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static LoginCommand create(String playerUuid, String username, String ipAddress, String virtualHost) {
        return new LoginCommand(newEventId("login", playerUuid), playerUuid, username, ipAddress, virtualHost, Instant.now());
    }

    static String newEventId(String eventType, String playerUuid) {
        return eventType + ":" + requireUuid(playerUuid) + ":" + UUID.randomUUID();
    }

    static String normalizeEventId(String eventId) {
        String normalized = requireText(eventId, "eventId");
        if (normalized.length() > 96) {
            throw new IllegalArgumentException("eventId must be 96 characters or fewer.");
        }
        return normalized;
    }

    static String requireUuid(String playerUuid) {
        String normalized = requireText(playerUuid, "playerUuid");
        return UUID.fromString(normalized).toString();
    }

    static String requireText(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return normalized;
    }
}
