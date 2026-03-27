package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataprovider.database.DatabaseType;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable runtime settings for DataRegistry.
 */
public final class DataRegistrySettings {

    private static final String DEFAULT_CONNECTION_ID = "player_data_rw";
    private static final String DEFAULT_ORM_SCHEMA_MODE = "validate";
    private static final int DEFAULT_BUKKIT_JOIN_DELAY_TICKS = 4;
    private static final int DEFAULT_USERNAME_MAX_LENGTH = 32;
    private static final int DEFAULT_SERVER_NAME_MAX_LENGTH = 64;
    private static final int DEFAULT_VIRTUAL_HOST_MAX_LENGTH = 255;
    private static final int DEFAULT_IP_ADDRESS_MAX_LENGTH = 45;
    private static final Set<String> ALLOWED_SCHEMA_MODES =
            Set.of("validate", "update", "create", "create-drop", "none");

    private final DatabaseType databaseType;
    private final String databaseConnectionId;
    private final String ormSchemaMode;
    private final boolean persistIpAddress;
    private final boolean persistVirtualHost;
    private final int bukkitJoinDelayTicks;
    private final int usernameMaxLength;
    private final int serverNameMaxLength;
    private final int virtualHostMaxLength;
    private final int ipAddressMaxLength;

    private DataRegistrySettings(Builder builder) {
        this.databaseType = Objects.requireNonNull(builder.databaseType, "databaseType must not be null");
        this.databaseConnectionId = normalizeConnectionId(builder.databaseConnectionId);
        this.ormSchemaMode = normalizeSchemaMode(builder.ormSchemaMode);
        this.persistIpAddress = builder.persistIpAddress;
        this.persistVirtualHost = builder.persistVirtualHost;
        this.bukkitJoinDelayTicks = validateRange(
                builder.bukkitJoinDelayTicks,
                "bukkitJoinDelayTicks",
                0,
                200
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

    public String databaseConnectionId() {
        return databaseConnectionId;
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

    private static int validateRange(int value, String fieldName, int minInclusive, int maxInclusive) {
        if (value < minInclusive || value > maxInclusive) {
            throw new IllegalArgumentException(
                    fieldName + " must be between " + minInclusive + " and " + maxInclusive + "."
            );
        }
        return value;
    }

    private static String normalizeConnectionId(String value) {
        if (value == null) {
            throw new IllegalArgumentException("databaseConnectionId must not be null");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("databaseConnectionId must not be blank");
        }
        if (!normalized.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException(
                    "databaseConnectionId must match [A-Za-z0-9._-]{1,64}"
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

    public static final class Builder {
        private DatabaseType databaseType = DatabaseType.MYSQL;
        private String databaseConnectionId = DEFAULT_CONNECTION_ID;
        private String ormSchemaMode = DEFAULT_ORM_SCHEMA_MODE;
        private boolean persistIpAddress;
        private boolean persistVirtualHost;
        private int bukkitJoinDelayTicks = DEFAULT_BUKKIT_JOIN_DELAY_TICKS;
        private int usernameMaxLength = DEFAULT_USERNAME_MAX_LENGTH;
        private int serverNameMaxLength = DEFAULT_SERVER_NAME_MAX_LENGTH;
        private int virtualHostMaxLength = DEFAULT_VIRTUAL_HOST_MAX_LENGTH;
        private int ipAddressMaxLength = DEFAULT_IP_ADDRESS_MAX_LENGTH;

        private Builder() {
        }

        public Builder databaseType(DatabaseType value) {
            this.databaseType = Objects.requireNonNull(value, "databaseType must not be null");
            return this;
        }

        public Builder databaseConnectionId(String value) {
            this.databaseConnectionId = Objects.requireNonNull(value, "databaseConnectionId must not be null");
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

        public DataRegistrySettings build() {
            return new DataRegistrySettings(this);
        }
    }
}
