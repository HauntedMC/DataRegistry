package nl.hauntedmc.dataregistry.api;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Toggleable built-in DataRegistry data domains.
 */
public enum DataRegistryFeature {
    ONLINE_STATUS("online-status"),
    CONNECTION_INFO("connection-info"),
    ACTIVITY_SUMMARY("activity-summary"),
    SESSIONS("sessions"),
    SESSION_VISITS("session-visits"),
    PLAYTIME("playtime"),
    LANGUAGE("language"),
    NICKNAMES("nicknames"),
    NAME_HISTORY("name-history"),
    SERVICE_REGISTRY("service-registry");

    private final String configKey;

    DataRegistryFeature(String configKey) {
        this.configKey = Objects.requireNonNull(configKey, "configKey must not be null");
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<DataRegistryFeature> fromConfigKey(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (DataRegistryFeature feature : values()) {
            if (feature.configKey.equals(normalized)) {
                return Optional.of(feature);
            }
        }
        return Optional.empty();
    }
}
