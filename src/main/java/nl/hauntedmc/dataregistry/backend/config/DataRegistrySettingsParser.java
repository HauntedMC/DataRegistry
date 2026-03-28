package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.database.DatabaseType;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Parses runtime settings from a reconciled config map with per-key validation fallbacks.
 */
final class DataRegistrySettingsParser {

    private static final String DATABASE_TYPE_KEY = "database.type";
    private static final String PLAYER_DATABASE_CONNECTION_ID_KEY = "database.profiles.players.connection-id";
    private static final String SERVICE_DATABASE_CONNECTION_ID_KEY = "database.profiles.services.connection-id";
    private static final String ORM_SCHEMA_MODE_KEY = "orm.schema-mode";
    private static final String PRIVACY_PERSIST_IP_KEY = "privacy.persist-ip-address";
    private static final String PRIVACY_PERSIST_VHOST_KEY = "privacy.persist-virtual-host";
    private static final String FEATURE_ONLINE_STATUS_KEY = "features.online-status";
    private static final String FEATURE_CONNECTION_INFO_KEY = "features.connection-info";
    private static final String FEATURE_SESSIONS_KEY = "features.sessions";
    private static final String FEATURE_NAME_HISTORY_KEY = "features.name-history";
    private static final String FEATURE_SERVICE_REGISTRY_KEY = "features.service-registry";
    private static final String SERVICE_HEARTBEAT_INTERVAL_SECONDS_KEY = "service-registry.heartbeat-interval-seconds";
    private static final String BUKKIT_JOIN_DELAY_TICKS_KEY = "platform.bukkit.join-delay-ticks";
    private static final String USERNAME_MAX_LENGTH_KEY = "validation.username.max-length";
    private static final String SERVER_NAME_MAX_LENGTH_KEY = "validation.server.max-length";
    private static final String VIRTUAL_HOST_MAX_LENGTH_KEY = "validation.virtual-host.max-length";
    private static final String IP_ADDRESS_MAX_LENGTH_KEY = "validation.ip.max-length";

    DataRegistrySettings parse(Map<?, ?> configRoot, ILoggerAdapter logger) {
        Objects.requireNonNull(configRoot, "configRoot must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        DataRegistrySettings defaults = DataRegistrySettings.defaults();
        DataRegistrySettings.Builder builder = DataRegistrySettings.builder();

        builder.databaseType(validateWithBuilder(
                DATABASE_TYPE_KEY,
                parseEnum(
                        configRoot,
                        DATABASE_TYPE_KEY,
                        DatabaseType.class,
                        defaults.databaseType(),
                        logger
                ),
                defaults.databaseType(),
                logger,
                DataRegistrySettings.Builder::databaseType
        ));
        builder.playerDatabaseConnectionId(validateWithBuilder(
                PLAYER_DATABASE_CONNECTION_ID_KEY,
                parseString(
                        configRoot,
                        PLAYER_DATABASE_CONNECTION_ID_KEY,
                        defaults.playerDatabaseConnectionId(),
                        logger
                ),
                defaults.playerDatabaseConnectionId(),
                logger,
                DataRegistrySettings.Builder::playerDatabaseConnectionId
        ));
        builder.serviceDatabaseConnectionId(validateWithBuilder(
                SERVICE_DATABASE_CONNECTION_ID_KEY,
                parseString(
                        configRoot,
                        SERVICE_DATABASE_CONNECTION_ID_KEY,
                        defaults.serviceDatabaseConnectionId(),
                        logger
                ),
                defaults.serviceDatabaseConnectionId(),
                logger,
                DataRegistrySettings.Builder::serviceDatabaseConnectionId
        ));
        builder.ormSchemaMode(validateWithBuilder(
                ORM_SCHEMA_MODE_KEY,
                parseString(
                        configRoot,
                        ORM_SCHEMA_MODE_KEY,
                        defaults.ormSchemaMode(),
                        logger
                ),
                defaults.ormSchemaMode(),
                logger,
                DataRegistrySettings.Builder::ormSchemaMode
        ));
        builder.persistIpAddress(parseBoolean(
                configRoot,
                PRIVACY_PERSIST_IP_KEY,
                defaults.persistIpAddress(),
                logger
        ));
        builder.persistVirtualHost(parseBoolean(
                configRoot,
                PRIVACY_PERSIST_VHOST_KEY,
                defaults.persistVirtualHost(),
                logger
        ));
        builder.enabledFeatures(parseEnabledFeatures(configRoot, defaults, logger));
        builder.serviceHeartbeatIntervalSeconds(validateWithBuilder(
                SERVICE_HEARTBEAT_INTERVAL_SECONDS_KEY,
                parseInteger(
                        configRoot,
                        SERVICE_HEARTBEAT_INTERVAL_SECONDS_KEY,
                        defaults.serviceHeartbeatIntervalSeconds(),
                        logger
                ),
                defaults.serviceHeartbeatIntervalSeconds(),
                logger,
                DataRegistrySettings.Builder::serviceHeartbeatIntervalSeconds
        ));
        builder.bukkitJoinDelayTicks(validateWithBuilder(
                BUKKIT_JOIN_DELAY_TICKS_KEY,
                parseInteger(
                        configRoot,
                        BUKKIT_JOIN_DELAY_TICKS_KEY,
                        defaults.bukkitJoinDelayTicks(),
                        logger
                ),
                defaults.bukkitJoinDelayTicks(),
                logger,
                DataRegistrySettings.Builder::bukkitJoinDelayTicks
        ));
        builder.usernameMaxLength(validateWithBuilder(
                USERNAME_MAX_LENGTH_KEY,
                parseInteger(
                        configRoot,
                        USERNAME_MAX_LENGTH_KEY,
                        defaults.usernameMaxLength(),
                        logger
                ),
                defaults.usernameMaxLength(),
                logger,
                DataRegistrySettings.Builder::usernameMaxLength
        ));
        builder.serverNameMaxLength(validateWithBuilder(
                SERVER_NAME_MAX_LENGTH_KEY,
                parseInteger(
                        configRoot,
                        SERVER_NAME_MAX_LENGTH_KEY,
                        defaults.serverNameMaxLength(),
                        logger
                ),
                defaults.serverNameMaxLength(),
                logger,
                DataRegistrySettings.Builder::serverNameMaxLength
        ));
        builder.virtualHostMaxLength(validateWithBuilder(
                VIRTUAL_HOST_MAX_LENGTH_KEY,
                parseInteger(
                        configRoot,
                        VIRTUAL_HOST_MAX_LENGTH_KEY,
                        defaults.virtualHostMaxLength(),
                        logger
                ),
                defaults.virtualHostMaxLength(),
                logger,
                DataRegistrySettings.Builder::virtualHostMaxLength
        ));
        builder.ipAddressMaxLength(validateWithBuilder(
                IP_ADDRESS_MAX_LENGTH_KEY,
                parseInteger(
                        configRoot,
                        IP_ADDRESS_MAX_LENGTH_KEY,
                        defaults.ipAddressMaxLength(),
                        logger
                ),
                defaults.ipAddressMaxLength(),
                logger,
                DataRegistrySettings.Builder::ipAddressMaxLength
        ));

        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            logger.warn("Unexpected DataRegistry settings validation failure; falling back to defaults.", exception);
            return defaults;
        }
    }

    private static <T> T validateWithBuilder(
            String key,
            T candidateValue,
            T defaultValue,
            ILoggerAdapter logger,
            BiConsumer<DataRegistrySettings.Builder, T> settingApplier
    ) {
        try {
            DataRegistrySettings.Builder validationBuilder = DataRegistrySettings.builder();
            settingApplier.accept(validationBuilder, candidateValue);
            validationBuilder.build();
            return candidateValue;
        } catch (IllegalArgumentException exception) {
            logger.warn(
                    "Invalid value for key '" + key + "': '" + candidateValue + "'. Using default '" + defaultValue + "'."
            );
            return defaultValue;
        }
    }

    private static int parseInteger(Map<?, ?> configRoot, String key, int defaultValue, ILoggerAdapter logger) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException exception) {
            logger.warn("Invalid integer for key '" + key + "': '" + value + "'. Using default " + defaultValue);
            return defaultValue;
        }
    }

    private static boolean parseBoolean(Map<?, ?> configRoot, String key, boolean defaultValue, ILoggerAdapter logger) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String normalized = value.toString().trim().toLowerCase(Locale.ROOT);
        if ("true".equals(normalized)) {
            return true;
        }
        if ("false".equals(normalized)) {
            return false;
        }
        logger.warn("Invalid boolean for key '" + key + "': '" + value + "'. Using default " + defaultValue);
        return defaultValue;
    }

    private static <E extends Enum<E>> E parseEnum(
            Map<?, ?> configRoot,
            String key,
            Class<E> enumType,
            E defaultValue,
            ILoggerAdapter logger
    ) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(enumType, value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            logger.warn("Invalid enum value for key '" + key + "': '" + value + "'. Using default " + defaultValue);
            return defaultValue;
        }
    }

    private static String parseString(Map<?, ?> configRoot, String key, String defaultValue, ILoggerAdapter logger) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof String result)) {
            logger.warn("Invalid string for key '" + key + "': '" + value + "'. Using default '" + defaultValue + "'.");
            return defaultValue;
        }
        if (result.isBlank()) {
            logger.warn("Blank value for key '" + key + "'. Using default '" + defaultValue + "'.");
            return defaultValue;
        }
        return result;
    }

    private static EnumSet<DataRegistryFeature> parseEnabledFeatures(
            Map<?, ?> configRoot,
            DataRegistrySettings defaults,
            ILoggerAdapter logger
    ) {
        EnumSet<DataRegistryFeature> enabledFeatures = EnumSet.noneOf(DataRegistryFeature.class);
        if (parseBoolean(
                configRoot,
                FEATURE_ONLINE_STATUS_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.ONLINE_STATUS);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_CONNECTION_INFO_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.CONNECTION_INFO);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_SESSIONS_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.SESSIONS),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.SESSIONS);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_NAME_HISTORY_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.NAME_HISTORY);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_SERVICE_REGISTRY_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.SERVICE_REGISTRY);
        }
        return enabledFeatures;
    }

    private static Object getValue(Map<?, ?> configRoot, String dottedKey) {
        Object current = configRoot;
        String[] segments = dottedKey.split("\\.");
        for (String segment : segments) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(segment);
            if (current == null) {
                return null;
            }
        }
        return current;
    }
}
