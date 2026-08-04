package nl.hauntedmc.dataregistry.api.playtime;

import java.util.Locale;

/**
 * Public policy metadata for one logical playtime gamemode.
 */
public record PlaytimeGamemodeDefinition(
        String gamemodeKey,
        boolean tracked,
        boolean queryable,
        boolean countedTowardsNetworkTotal
) {

    private static final int MAX_GAMEMODE_KEY_LENGTH = 64;
    private static final String ALLOWED_GAMEMODE_KEY_REGEX = "[a-z0-9._:-]+";

    public PlaytimeGamemodeDefinition {
        gamemodeKey = normalizeRequiredGamemodeKey(gamemodeKey);
        if (!tracked) {
            queryable = false;
            countedTowardsNetworkTotal = false;
        } else if (!queryable) {
            countedTowardsNetworkTotal = false;
        }
    }

    static String normalizeGamemodeKeyOrNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()
                || normalized.length() > MAX_GAMEMODE_KEY_LENGTH
                || !normalized.matches(ALLOWED_GAMEMODE_KEY_REGEX)) {
            return null;
        }
        return normalized;
    }

    private static String normalizeRequiredGamemodeKey(String value) {
        String normalized = normalizeGamemodeKeyOrNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException("gamemodeKey must be a valid normalized playtime key.");
        }
        return normalized;
    }
}
