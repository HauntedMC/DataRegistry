package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataprovider.database.DatabaseType;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An explicitly opted-in DataProvider connection whose player-owned rows are removed with a canonical player.
 *
 * <p>Tables are discovered at deletion time from the configured player-id columns. This deliberately scopes the
 * broad discovery to connections an operator has named in {@code config.yml}.</p>
 */
public record ExternalPlayerDataConnectionSettings(
        DatabaseType databaseType,
        String connectionId,
        Set<String> playerIdColumns
) {

    private static final String CONNECTION_ID_PATTERN = "[A-Za-z0-9._-]{1,64}";
    private static final String IDENTIFIER_PATTERN = "[A-Za-z_][A-Za-z0-9_]{0,63}";

    public ExternalPlayerDataConnectionSettings {
        databaseType = Objects.requireNonNull(databaseType, "databaseType must not be null");
        connectionId = normalizeConnectionId(connectionId);
        playerIdColumns = normalizePlayerIdColumns(playerIdColumns);
    }

    private static String normalizeConnectionId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("connectionId must not be null");
        }
        String normalized = value.trim();
        if (!normalized.matches(CONNECTION_ID_PATTERN)) {
            throw new IllegalArgumentException("connectionId must match " + CONNECTION_ID_PATTERN);
        }
        return normalized;
    }

    private static Set<String> normalizePlayerIdColumns(Set<String> values) {
        Objects.requireNonNull(values, "playerIdColumns must not be null");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || !value.matches(IDENTIFIER_PATTERN)) {
                throw new IllegalArgumentException("playerIdColumns must contain SQL identifier names only.");
            }
            String key = value.toLowerCase(Locale.ROOT);
            if (!normalized.add(key)) {
                throw new IllegalArgumentException("playerIdColumns must not contain duplicate names.");
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("playerIdColumns must not be empty.");
        }
        return Set.copyOf(normalized);
    }
}
