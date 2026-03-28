package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataprovider.database.DatabaseType;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable runtime settings for DataRegistry.
 */
public final class DataRegistrySettings {

    private static final String DEFAULT_PLAYER_CONNECTION_ID = "player_data_rw";
    private static final String DEFAULT_SERVICE_CONNECTION_ID = "player_data_rw";
    private static final int DEFAULT_SERVICE_HEARTBEAT_INTERVAL_SECONDS = 30;
    private static final int DEFAULT_SERVICE_PROBE_INTERVAL_SECONDS = 15;
    private static final int DEFAULT_SERVICE_PROBE_TIMEOUT_MILLIS = 1500;
    private static final int DEFAULT_SERVICE_PROBE_RETENTION_HOURS = 168;
    private static final String DEFAULT_ORM_SCHEMA_MODE = "validate";
    private static final int DEFAULT_BUKKIT_JOIN_DELAY_TICKS = 4;
    private static final String DEFAULT_BUKKIT_SERVICE_NAME = "auto";
    private static final String DEFAULT_VELOCITY_SERVICE_NAME = "auto";
    private static final int DEFAULT_USERNAME_MAX_LENGTH = 32;
    private static final int DEFAULT_SERVER_NAME_MAX_LENGTH = 64;
    private static final int DEFAULT_VIRTUAL_HOST_MAX_LENGTH = 255;
    private static final int DEFAULT_IP_ADDRESS_MAX_LENGTH = 45;
    private static final Set<String> ALLOWED_SCHEMA_MODES =
            Set.of("validate", "update", "create", "create-drop", "none");

    private final DatabaseType databaseType;
    private final String playerDatabaseConnectionId;
    private final String serviceDatabaseConnectionId;
    private final String ormSchemaMode;
    private final boolean persistIpAddress;
    private final boolean persistVirtualHost;
    private final int bukkitJoinDelayTicks;
    private final String bukkitServiceName;
    private final String velocityServiceName;
    private final int usernameMaxLength;
    private final int serverNameMaxLength;
    private final int virtualHostMaxLength;
    private final int ipAddressMaxLength;
    private final int serviceHeartbeatIntervalSeconds;
    private final int serviceProbeIntervalSeconds;
    private final int serviceProbeTimeoutMillis;
    private final int serviceProbeRetentionHours;
    private final Set<DataRegistryFeature> enabledFeatures;

    private DataRegistrySettings(Builder builder) {
        this.databaseType = Objects.requireNonNull(builder.databaseType, "databaseType must not be null");
        this.playerDatabaseConnectionId = normalizeConnectionId(
                builder.playerDatabaseConnectionId,
                "playerDatabaseConnectionId"
        );
        this.serviceDatabaseConnectionId = normalizeConnectionId(
                builder.serviceDatabaseConnectionId,
                "serviceDatabaseConnectionId"
        );
        this.ormSchemaMode = normalizeSchemaMode(builder.ormSchemaMode);
        this.persistIpAddress = builder.persistIpAddress;
        this.persistVirtualHost = builder.persistVirtualHost;
        this.bukkitJoinDelayTicks = validateRange(
                builder.bukkitJoinDelayTicks,
                "bukkitJoinDelayTicks",
                0,
                200
        );
        this.bukkitServiceName = normalizeServiceName(
                builder.bukkitServiceName,
                "bukkitServiceName"
        );
        this.velocityServiceName = normalizeServiceName(
                builder.velocityServiceName,
                "velocityServiceName"
        );
        this.usernameMaxLength = validateRange(
                builder.usernameMaxLength,
                "usernameMaxLength",
                1,
                DEFAULT_USERNAME_MAX_LENGTH
        );
        this.serverNameMaxLength = validateRange(
                builder.serverNameMaxLength,
                "serverNameMaxLength",
                1,
                DEFAULT_SERVER_NAME_MAX_LENGTH
        );
        this.virtualHostMaxLength = validateRange(
                builder.virtualHostMaxLength,
                "virtualHostMaxLength",
                1,
                255
        );
        this.ipAddressMaxLength = validateRange(
                builder.ipAddressMaxLength,
                "ipAddressMaxLength",
                7,
                45
        );
        this.serviceHeartbeatIntervalSeconds = validateRange(
                builder.serviceHeartbeatIntervalSeconds,
                "serviceHeartbeatIntervalSeconds",
                5,
                300
        );
        this.serviceProbeIntervalSeconds = validateRange(
                builder.serviceProbeIntervalSeconds,
                "serviceProbeIntervalSeconds",
                5,
                300
        );
        this.serviceProbeTimeoutMillis = validateRange(
                builder.serviceProbeTimeoutMillis,
                "serviceProbeTimeoutMillis",
                200,
                10000
        );
        this.serviceProbeRetentionHours = validateRange(
                builder.serviceProbeRetentionHours,
                "serviceProbeRetentionHours",
                1,
                2160
        );
        this.enabledFeatures = Collections.unmodifiableSet(EnumSet.copyOf(builder.enabledFeatures));
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DataRegistrySettings defaults() {
        return builder().build();
    }

    public DatabaseType databaseType() {
        return databaseType;
    }

    public String playerDatabaseConnectionId() {
        return playerDatabaseConnectionId;
    }

    public String serviceDatabaseConnectionId() {
        return serviceDatabaseConnectionId;
    }

    public String ormSchemaMode() {
        return ormSchemaMode;
    }

    public boolean persistIpAddress() {
        return persistIpAddress;
    }

    public boolean persistVirtualHost() {
        return persistVirtualHost;
    }

    public int bukkitJoinDelayTicks() {
        return bukkitJoinDelayTicks;
    }

    public String bukkitServiceName() {
        return bukkitServiceName;
    }

    public boolean isBukkitServiceNameAuto() {
        return "auto".equalsIgnoreCase(bukkitServiceName);
    }

    public String velocityServiceName() {
        return velocityServiceName;
    }

    public boolean isVelocityServiceNameAuto() {
        return "auto".equalsIgnoreCase(velocityServiceName);
    }

    public int usernameMaxLength() {
        return usernameMaxLength;
    }

    public int serverNameMaxLength() {
        return serverNameMaxLength;
    }

    public int virtualHostMaxLength() {
        return virtualHostMaxLength;
    }

    public int ipAddressMaxLength() {
        return ipAddressMaxLength;
    }

    public int serviceHeartbeatIntervalSeconds() {
        return serviceHeartbeatIntervalSeconds;
    }

    public int serviceProbeIntervalSeconds() {
        return serviceProbeIntervalSeconds;
    }

    public int serviceProbeTimeoutMillis() {
        return serviceProbeTimeoutMillis;
    }

    public int serviceProbeRetentionHours() {
        return serviceProbeRetentionHours;
    }

    public Set<DataRegistryFeature> enabledFeatures() {
        return enabledFeatures;
    }

    public boolean isFeatureEnabled(DataRegistryFeature feature) {
        return enabledFeatures.contains(Objects.requireNonNull(feature, "feature must not be null"));
    }

    private static int validateRange(int value, String fieldName, int minInclusive, int maxInclusive) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minInclusive + " and " + maxInclusive + "."
            );
        }
        return value;
    }

    private static String normalizeConnectionId(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!normalized.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(
                    fieldName + " must match [A-Za-z0-9._-]{1,64}"
            );
        }
        return normalized;
    }

    private static String normalizeSchemaMode(String value) {
        if (value == null) {
            throw new IllegalArgumentException("ormSchemaMode must not be null");
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_SCHEMA_MODES.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Unsupported ormSchemaMode '" + value + "'. Allowed values: " + ALLOWED_SCHEMA_MODES
            );
        }
        return normalized;
    }

    private static String normalizeServiceName(String value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        if (!"auto".equalsIgnoreCase(normalized) && normalized.length() > DEFAULT_SERVER_NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " must be 'auto' or at most " + DEFAULT_SERVER_NAME_MAX_LENGTH + " characters."
            );
        }
        return normalized;
    }

    public static final class Builder {
        private DatabaseType databaseType = DatabaseType.MYSQL;
        private String playerDatabaseConnectionId = DEFAULT_PLAYER_CONNECTION_ID;
        private String serviceDatabaseConnectionId = DEFAULT_SERVICE_CONNECTION_ID;
        private String ormSchemaMode = DEFAULT_ORM_SCHEMA_MODE;
        private boolean persistIpAddress;
        private boolean persistVirtualHost;
        private int bukkitJoinDelayTicks = DEFAULT_BUKKIT_JOIN_DELAY_TICKS;
        private String bukkitServiceName = DEFAULT_BUKKIT_SERVICE_NAME;
        private String velocityServiceName = DEFAULT_VELOCITY_SERVICE_NAME;
        private int usernameMaxLength = DEFAULT_USERNAME_MAX_LENGTH;
        private int serverNameMaxLength = DEFAULT_SERVER_NAME_MAX_LENGTH;
        private int virtualHostMaxLength = DEFAULT_VIRTUAL_HOST_MAX_LENGTH;
        private int ipAddressMaxLength = DEFAULT_IP_ADDRESS_MAX_LENGTH;
        private int serviceHeartbeatIntervalSeconds = DEFAULT_SERVICE_HEARTBEAT_INTERVAL_SECONDS;
        private int serviceProbeIntervalSeconds = DEFAULT_SERVICE_PROBE_INTERVAL_SECONDS;
        private int serviceProbeTimeoutMillis = DEFAULT_SERVICE_PROBE_TIMEOUT_MILLIS;
        private int serviceProbeRetentionHours = DEFAULT_SERVICE_PROBE_RETENTION_HOURS;
        private EnumSet<DataRegistryFeature> enabledFeatures = EnumSet.allOf(DataRegistryFeature.class);

        private Builder() {
        }

        public Builder databaseType(DatabaseType value) {
            this.databaseType = Objects.requireNonNull(value, "databaseType must not be null");
            return this;
        }

        public Builder playerDatabaseConnectionId(String value) {
            this.playerDatabaseConnectionId = Objects.requireNonNull(value, "playerDatabaseConnectionId must not be null");
            return this;
        }

        public Builder serviceDatabaseConnectionId(String value) {
            this.serviceDatabaseConnectionId =
                    Objects.requireNonNull(value, "serviceDatabaseConnectionId must not be null");
            return this;
        }

        public Builder ormSchemaMode(String value) {
            this.ormSchemaMode = Objects.requireNonNull(value, "ormSchemaMode must not be null");
            return this;
        }

        public Builder persistIpAddress(boolean value) {
            this.persistIpAddress = value;
            return this;
        }

        public Builder persistVirtualHost(boolean value) {
            this.persistVirtualHost = value;
            return this;
        }

        public Builder bukkitJoinDelayTicks(int value) {
            this.bukkitJoinDelayTicks = value;
            return this;
        }

        public Builder bukkitServiceName(String value) {
            this.bukkitServiceName = Objects.requireNonNull(value, "bukkitServiceName must not be null");
            return this;
        }

        public Builder velocityServiceName(String value) {
            this.velocityServiceName = Objects.requireNonNull(value, "velocityServiceName must not be null");
            return this;
        }

        public Builder usernameMaxLength(int value) {
            this.usernameMaxLength = value;
            return this;
        }

        public Builder serverNameMaxLength(int value) {
            this.serverNameMaxLength = value;
            return this;
        }

        public Builder virtualHostMaxLength(int value) {
            this.virtualHostMaxLength = value;
            return this;
        }

        public Builder ipAddressMaxLength(int value) {
            this.ipAddressMaxLength = value;
            return this;
        }

        public Builder serviceHeartbeatIntervalSeconds(int value) {
            this.serviceHeartbeatIntervalSeconds = value;
            return this;
        }

        public Builder serviceProbeIntervalSeconds(int value) {
            this.serviceProbeIntervalSeconds = value;
            return this;
        }

        public Builder serviceProbeTimeoutMillis(int value) {
            this.serviceProbeTimeoutMillis = value;
            return this;
        }

        public Builder serviceProbeRetentionHours(int value) {
            this.serviceProbeRetentionHours = value;
            return this;
        }

        public Builder enabledFeatures(Set<DataRegistryFeature> values) {
            Objects.requireNonNull(values, "enabledFeatures must not be null");
            this.enabledFeatures = values.isEmpty()
                    ? EnumSet.noneOf(DataRegistryFeature.class)
                    : EnumSet.copyOf(values);
            return this;
        }

        public Builder enableFeature(DataRegistryFeature feature) {
            this.enabledFeatures.add(Objects.requireNonNull(feature, "feature must not be null"));
            return this;
        }

        public Builder disableFeature(DataRegistryFeature feature) {
            this.enabledFeatures.remove(Objects.requireNonNull(feature, "feature must not be null"));
            return this;
        }

        public DataRegistrySettings build() {
            return new DataRegistrySettings(this);
        }
    }
}
