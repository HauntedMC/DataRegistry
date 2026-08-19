package nl.hauntedmc.dataregistry.api.player;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Typed lookup key for resolving persisted DataRegistry player identities.
 */
public record PlayerLookup(Type type, Long playerId, UUID uuid, String text) {

    public enum Type {
        PLAYER_ID,
        UUID,
        USERNAME,
        IDENTIFIER
    }

    public PlayerLookup {
        Objects.requireNonNull(type, "type must not be null");
        switch (type) {
            case PLAYER_ID -> {
                if (playerId == null || playerId <= 0L) {
                    throw new IllegalArgumentException("playerId must be a positive database id.");
                }
                uuid = null;
                text = null;
            }
            case UUID -> {
                Objects.requireNonNull(uuid, "uuid must not be null");
                playerId = null;
                text = uuid.toString();
            }
            case USERNAME, IDENTIFIER -> {
                String normalized = normalizeText(text);
                if (normalized == null) {
                    throw new IllegalArgumentException(type.name().toLowerCase(Locale.ROOT) + " must not be blank.");
                }
                playerId = null;
                uuid = null;
                text = normalized;
            }
        }
    }

    public static PlayerLookup playerId(long playerId) {
        return new PlayerLookup(Type.PLAYER_ID, playerId, null, null);
    }

    public static PlayerLookup uuid(UUID uuid) {
        return new PlayerLookup(Type.UUID, null, uuid, null);
    }

    public static PlayerLookup uuid(String uuid) {
        return new PlayerLookup(Type.UUID, null, UUID.fromString(uuid.trim()), null);
    }

    public static Optional<PlayerLookup> uuidIfValid(String uuid) {
        String normalized = normalizeText(uuid);
        if (normalized == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(uuid(normalized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static PlayerLookup username(String username) {
        return new PlayerLookup(Type.USERNAME, null, null, username);
    }

    public static PlayerLookup identifier(String identifier) {
        return new PlayerLookup(Type.IDENTIFIER, null, null, identifier);
    }

    private static String normalizeText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
