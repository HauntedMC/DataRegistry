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
    SESSIONS("sessions"),
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
