package nl.hauntedmc.dataregistry.api.player;

import java.util.List;
import java.util.Objects;

/**
 * Privacy-safe account-data disclosure model for player-facing account surfaces.
 * It describes categories and policy only; it deliberately contains no IP address, client,
 * session identifier, message body, or other player value.
 */
public record PlayerAccountDataSummary(List<Category> categories) {
    public PlayerAccountDataSummary {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public record Category(String id, String purpose, String scope, String retention) {
        public Category {
            id = text(id, "id", 64);
            purpose = text(purpose, "purpose", 240);
            scope = text(scope, "scope", 120);
            retention = text(retention, "retention", 160);
        }

        private static String text(String value, String field, int maximum) {
            String result = Objects.requireNonNull(value, field).trim();
            if (result.isEmpty() || result.length() > maximum || result.indexOf('\n') >= 0 || result.indexOf('\r') >= 0) {
                throw new IllegalArgumentException(field + " must be one line of at most " + maximum + " characters");
            }
            return result;
        }
    }
}
