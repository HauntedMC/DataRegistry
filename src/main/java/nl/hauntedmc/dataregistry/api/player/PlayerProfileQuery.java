package nl.hauntedmc.dataregistry.api.player;

import java.time.Instant;
import java.util.Objects;

/**
 * Read options for a DataRegistry-owned player profile projection.
 */
public record PlayerProfileQuery(int nameHistoryLimit, Instant asOf) {

    private static final int DEFAULT_NAME_HISTORY_LIMIT = 20;

    public PlayerProfileQuery {
        if (nameHistoryLimit < 0) {
            throw new IllegalArgumentException("nameHistoryLimit must be zero or greater.");
        }
        asOf = Objects.requireNonNullElseGet(asOf, Instant::now);
    }

    public static PlayerProfileQuery defaults() {
        return new PlayerProfileQuery(DEFAULT_NAME_HISTORY_LIMIT, Instant.now());
    }

    public static PlayerProfileQuery withNameHistoryLimit(int nameHistoryLimit) {
        return new PlayerProfileQuery(nameHistoryLimit, Instant.now());
    }
}
