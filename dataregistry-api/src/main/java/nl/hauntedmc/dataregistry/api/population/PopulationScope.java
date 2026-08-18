package nl.hauntedmc.dataregistry.api.population;

import java.util.Locale;
import java.util.Objects;

/** Canonical network or logical-gamemode population scope. */
public record PopulationScope(PopulationScopeType type, String key) {

    public static final String NETWORK_KEY = "network";

    public PopulationScope {
        Objects.requireNonNull(type, "type must not be null");
        key = normalizeKey(type, key);
    }

    public static PopulationScope network() {
        return new PopulationScope(PopulationScopeType.NETWORK, NETWORK_KEY);
    }

    public static PopulationScope gamemode(String gamemodeKey) {
        return new PopulationScope(PopulationScopeType.GAMEMODE, gamemodeKey);
    }

    public String storageKey() {
        return type == PopulationScopeType.NETWORK ? NETWORK_KEY : "gamemode:" + key;
    }

    private static String normalizeKey(PopulationScopeType type, String value) {
        if (type == PopulationScopeType.NETWORK) {
            if (value != null && !value.isBlank() && !NETWORK_KEY.equalsIgnoreCase(value.trim())) {
                throw new IllegalArgumentException("Network population scope key must be 'network'.");
            }
            return NETWORK_KEY;
        }
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Gamemode population scope key must not be blank.");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 64 || !normalized.matches("[a-z0-9._:-]+")) {
            throw new IllegalArgumentException("Gamemode population scope key is invalid: " + normalized);
        }
        return normalized;
    }
}
