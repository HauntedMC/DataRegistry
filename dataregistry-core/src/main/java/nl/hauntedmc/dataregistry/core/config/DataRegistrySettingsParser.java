package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.database.DatabaseType;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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
    private static final String FEATURE_ACTIVITY_SUMMARY_KEY = "features.activity-summary";
    private static final String FEATURE_SESSIONS_KEY = "features.sessions";
    private static final String FEATURE_SESSION_VISITS_KEY = "features.session-visits";
    private static final String FEATURE_PLAYTIME_KEY = "features.playtime";
    private static final String FEATURE_LANGUAGE_KEY = "features.language";
    private static final String FEATURE_NICKNAMES_KEY = "features.nicknames";
    private static final String FEATURE_NAME_HISTORY_KEY = "features.name-history";
    private static final String FEATURE_SERVICE_REGISTRY_KEY = "features.service-registry";
    private static final String SERVICE_HEARTBEAT_INTERVAL_SECONDS_KEY = "service-registry.heartbeat-interval-seconds";
    private static final String SERVICE_PROBE_INTERVAL_SECONDS_KEY = "service-registry.probe-interval-seconds";
    private static final String SERVICE_PROBE_TIMEOUT_MILLIS_KEY = "service-registry.probe-timeout-millis";
    private static final String SERVICE_PROBE_RETENTION_HOURS_KEY = "service-registry.probe-retention-hours";
    private static final String SERVICE_PROBE_PURGE_INTERVAL_HOURS_KEY = "service-registry.probe-purge-interval-hours";
    private static final String BUKKIT_JOIN_DELAY_TICKS_KEY = "platform.bukkit.join-delay-ticks";
    private static final String BUKKIT_REGISTER_SERVICE_INSTANCE_KEY = "platform.bukkit.register-service-instance";
    private static final String BUKKIT_SERVICE_NAME_KEY = "platform.bukkit.service-name";
    private static final String VELOCITY_SERVICE_NAME_KEY = "platform.velocity.service-name";
    private static final String QUERY_EXECUTOR_THREADS_KEY = "query.executor-threads";
    private static final String QUERY_TIMEOUT_MILLIS_KEY = "query.timeout-millis";
    private static final String QUERY_DEVELOPMENT_THREAD_CHECKS_KEY = "query.development-thread-checks";
    private static final String USERNAME_MAX_LENGTH_KEY = "validation.username.max-length";
    private static final String SERVER_NAME_MAX_LENGTH_KEY = "validation.server.max-length";
    private static final String GAMEMODE_MAX_LENGTH_KEY = "validation.gamemode.max-length";
    private static final String VIRTUAL_HOST_MAX_LENGTH_KEY = "validation.virtual-host.max-length";
    private static final String IP_ADDRESS_MAX_LENGTH_KEY = "validation.ip.max-length";
    private static final String PLAYTIME_FLUSH_INTERVAL_SECONDS_KEY = "playtime.flush-interval-seconds";
    private static final String PLAYTIME_RESOLVE_UNKNOWN_SERVERS_KEY =
            "playtime.resolve-unknown-servers-as-gamemode";
    private static final String PLAYTIME_IGNORED_GAMEMODES_KEY = "playtime.ignored-gamemodes";
    private static final String PLAYTIME_EXCLUDED_GAMEMODES_KEY =
            "playtime.excluded-from-network-total-gamemodes";
    private static final String PLAYTIME_SERVER_GAMEMODE_RULES_KEY = "playtime.server-gamemode-rules";

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
        builder.serviceProbeIntervalSeconds(validateWithBuilder(
                SERVICE_PROBE_INTERVAL_SECONDS_KEY,
                parseInteger(
                        configRoot,
                        SERVICE_PROBE_INTERVAL_SECONDS_KEY,
                        defaults.serviceProbeIntervalSeconds(),
                        logger
                ),
                defaults.serviceProbeIntervalSeconds(),
                logger,
                DataRegistrySettings.Builder::serviceProbeIntervalSeconds
        ));
        builder.serviceProbeTimeoutMillis(validateWithBuilder(
                SERVICE_PROBE_TIMEOUT_MILLIS_KEY,
                parseInteger(
                        configRoot,
                        SERVICE_PROBE_TIMEOUT_MILLIS_KEY,
                        defaults.serviceProbeTimeoutMillis(),
                        logger
                ),
                defaults.serviceProbeTimeoutMillis(),
                logger,
                DataRegistrySettings.Builder::serviceProbeTimeoutMillis
        ));
        builder.serviceProbeRetentionHours(validateWithBuilder(
                SERVICE_PROBE_RETENTION_HOURS_KEY,
                parseInteger(
                        configRoot,
                        SERVICE_PROBE_RETENTION_HOURS_KEY,
                        defaults.serviceProbeRetentionHours(),
                        logger
                ),
                defaults.serviceProbeRetentionHours(),
                logger,
                DataRegistrySettings.Builder::serviceProbeRetentionHours
        ));
        builder.serviceProbePurgeIntervalHours(validateWithBuilder(
                SERVICE_PROBE_PURGE_INTERVAL_HOURS_KEY,
                parseInteger(
                        configRoot,
                        SERVICE_PROBE_PURGE_INTERVAL_HOURS_KEY,
                        defaults.serviceProbePurgeIntervalHours(),
                        logger
                ),
                defaults.serviceProbePurgeIntervalHours(),
                logger,
                DataRegistrySettings.Builder::serviceProbePurgeIntervalHours
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
        builder.bukkitRegisterServiceInstance(parseBoolean(
                configRoot,
                BUKKIT_REGISTER_SERVICE_INSTANCE_KEY,
                defaults.bukkitRegisterServiceInstance(),
                logger
        ));
        builder.bukkitServiceName(validateWithBuilder(
                BUKKIT_SERVICE_NAME_KEY,
                parseString(
                        configRoot,
                        BUKKIT_SERVICE_NAME_KEY,
                        defaults.bukkitServiceName(),
                        logger
                ),
                defaults.bukkitServiceName(),
                logger,
                DataRegistrySettings.Builder::bukkitServiceName
        ));
        builder.velocityServiceName(validateWithBuilder(
                VELOCITY_SERVICE_NAME_KEY,
                parseString(
                        configRoot,
                        VELOCITY_SERVICE_NAME_KEY,
                        defaults.velocityServiceName(),
                        logger
                ),
                defaults.velocityServiceName(),
                logger,
                DataRegistrySettings.Builder::velocityServiceName
        ));
        builder.queryExecutorThreads(validateWithBuilder(
                QUERY_EXECUTOR_THREADS_KEY,
                parseInteger(
                        configRoot,
                        QUERY_EXECUTOR_THREADS_KEY,
                        defaults.queryExecutorThreads(),
                        logger
                ),
                defaults.queryExecutorThreads(),
                logger,
                DataRegistrySettings.Builder::queryExecutorThreads
        ));
        builder.queryTimeoutMillis(validateWithBuilder(
                QUERY_TIMEOUT_MILLIS_KEY,
                parseInteger(
                        configRoot,
                        QUERY_TIMEOUT_MILLIS_KEY,
                        defaults.queryTimeoutMillis(),
                        logger
                ),
                defaults.queryTimeoutMillis(),
                logger,
                DataRegistrySettings.Builder::queryTimeoutMillis
        ));
        builder.queryDevelopmentThreadChecks(parseBoolean(
                configRoot,
                QUERY_DEVELOPMENT_THREAD_CHECKS_KEY,
                defaults.queryDevelopmentThreadChecks(),
                logger
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
        PlaytimeTrackingSettings defaultPlaytimeSettings = defaults.playtimeTrackingSettings();
        builder.playtimeTrackingSettings(validateWithBuilder(
                "playtime",
                parsePlaytimeSettings(
                        configRoot,
                        defaultPlaytimeSettings,
                        logger
                ),
                defaultPlaytimeSettings,
                logger,
                DataRegistrySettings.Builder::playtimeTrackingSettings
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
                FEATURE_ACTIVITY_SUMMARY_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.ACTIVITY_SUMMARY);
        }
        boolean sessionsEnabled = parseBoolean(
                configRoot,
                FEATURE_SESSIONS_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.SESSIONS),
                logger
        );
        if (sessionsEnabled) {
            enabledFeatures.add(DataRegistryFeature.SESSIONS);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_SESSION_VISITS_KEY,
                sessionsEnabled && defaults.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.SESSION_VISITS);
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
                FEATURE_LANGUAGE_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.LANGUAGE),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.LANGUAGE);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_NICKNAMES_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.NICKNAMES),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.NICKNAMES);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_SERVICE_REGISTRY_KEY,
                defaults.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.SERVICE_REGISTRY);
        }
        if (parseBoolean(
                configRoot,
                FEATURE_PLAYTIME_KEY,
                sessionsEnabled && defaults.isFeatureEnabled(DataRegistryFeature.PLAYTIME),
                logger
        )) {
            enabledFeatures.add(DataRegistryFeature.PLAYTIME);
        }
        if (!enabledFeatures.contains(DataRegistryFeature.SESSIONS)
                && (enabledFeatures.contains(DataRegistryFeature.PLAYTIME)
                || enabledFeatures.contains(DataRegistryFeature.SESSION_VISITS))) {
            logger.warn("Features 'playtime' and 'session-visits' require 'sessions'. Enabling 'sessions' automatically.");
            enabledFeatures.add(DataRegistryFeature.SESSIONS);
        }
        return enabledFeatures;
    }

    private static PlaytimeTrackingSettings parsePlaytimeSettings(
            Map<?, ?> configRoot,
            PlaytimeTrackingSettings defaults,
            ILoggerAdapter logger
    ) {
        PlaytimeTrackingSettings.Builder builder = PlaytimeTrackingSettings.builder();
        int gamemodeKeyMaxLength = validatePlaytimeWithBuilder(
                GAMEMODE_MAX_LENGTH_KEY,
                parseInteger(
                        configRoot,
                        GAMEMODE_MAX_LENGTH_KEY,
                        defaults.gamemodeKeyMaxLength(),
                        logger
                ),
                defaults.gamemodeKeyMaxLength(),
                logger,
                PlaytimeTrackingSettings.Builder::gamemodeKeyMaxLength
        );
        builder.flushIntervalSeconds(validatePlaytimeWithBuilder(
                PLAYTIME_FLUSH_INTERVAL_SECONDS_KEY,
                parseInteger(
                        configRoot,
                        PLAYTIME_FLUSH_INTERVAL_SECONDS_KEY,
                        defaults.flushIntervalSeconds(),
                        logger
                ),
                defaults.flushIntervalSeconds(),
                logger,
                PlaytimeTrackingSettings.Builder::flushIntervalSeconds
        ));
        builder.resolveUnknownServersAsGamemode(parseBoolean(
                configRoot,
                PLAYTIME_RESOLVE_UNKNOWN_SERVERS_KEY,
                defaults.resolveUnknownServersAsGamemode(),
                logger
        ));
        builder.gamemodeKeyMaxLength(gamemodeKeyMaxLength);
        builder.ignoredGamemodes(parseGamemodeKeySet(
                configRoot,
                PLAYTIME_IGNORED_GAMEMODES_KEY,
                gamemodeKeyMaxLength,
                defaults.ignoredGamemodes(),
                logger
        ));
        builder.excludedFromNetworkTotalGamemodes(parseGamemodeKeySet(
                configRoot,
                PLAYTIME_EXCLUDED_GAMEMODES_KEY,
                gamemodeKeyMaxLength,
                defaults.excludedFromNetworkTotalGamemodes(),
                logger
        ));
        builder.serverGamemodeRules(parseServerGamemodeRules(
                configRoot,
                PLAYTIME_SERVER_GAMEMODE_RULES_KEY,
                gamemodeKeyMaxLength,
                defaults.serverGamemodeRules(),
                logger
        ));
        try {
            return builder.build();
        } catch (IllegalArgumentException exception) {
            logger.warn("Unexpected playtime settings validation failure; falling back to defaults.", exception);
            return defaults;
        }
    }

    private static <T> T validatePlaytimeWithBuilder(
            String key,
            T candidateValue,
            T defaultValue,
            ILoggerAdapter logger,
            BiConsumer<PlaytimeTrackingSettings.Builder, T> settingApplier
    ) {
        try {
            PlaytimeTrackingSettings.Builder validationBuilder = PlaytimeTrackingSettings.builder();
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

    private static Set<String> parseGamemodeKeySet(
            Map<?, ?> configRoot,
            String key,
            int maxLength,
            Set<String> defaultValue,
            ILoggerAdapter logger
    ) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof List<?> listValue)) {
            logger.warn("Invalid list for key '" + key + "'. Using default '" + defaultValue + "'.");
            return defaultValue;
        }
        LinkedHashSet<String> parsed = new LinkedHashSet<>();
        for (Object item : listValue) {
            String rawValue = item == null ? null : item.toString();
            String normalized = PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(rawValue, maxLength);
            if (normalized == null) {
                logger.warn(
                        "Invalid gamemode key for key '" + key + "': '" + rawValue + "'. Skipping entry."
                );
                continue;
            }
            parsed.add(normalized);
        }
        return parsed;
    }

    private static List<PlaytimeTrackingSettings.ServerGamemodeRule> parseServerGamemodeRules(
            Map<?, ?> configRoot,
            String key,
            int maxLength,
            List<PlaytimeTrackingSettings.ServerGamemodeRule> defaultValue,
            ILoggerAdapter logger
    ) {
        Object value = getValue(configRoot, key);
        if (value == null) {
            return defaultValue;
        }
        if (!(value instanceof List<?> listValue)) {
            logger.warn("Invalid list for key '" + key + "'. Using default '" + defaultValue + "'.");
            return defaultValue;
        }
        List<PlaytimeTrackingSettings.ServerGamemodeRule> parsed = new ArrayList<>();
        for (Object item : listValue) {
            if (!(item instanceof Map<?, ?> ruleMap)) {
                logger.warn("Invalid rule entry for key '" + key + "'. Skipping entry.");
                continue;
            }
            Object matchValue = ruleMap.get("match");
            Object gamemodeValue = ruleMap.get("gamemode");
            String normalizedGamemode = PlaytimeTrackingSettings.normalizeGamemodeKeyOrNull(
                    gamemodeValue == null ? null : gamemodeValue.toString(),
                    maxLength
            );
            if (matchValue == null || normalizedGamemode == null) {
                logger.warn("Playtime rule for key '" + key + "' requires both match and gamemode. Skipping entry.");
                continue;
            }
            try {
                parsed.add(new PlaytimeTrackingSettings.ServerGamemodeRule(
                        matchValue.toString(),
                        normalizedGamemode
                ));
            } catch (IllegalArgumentException exception) {
                logger.warn(
                        "Invalid playtime rule for key '" + key + "': '" + item + "'. Skipping entry."
                );
            }
        }
        return parsed;
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
