package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Loads DataRegistry settings from {@code config.yml}.
 */
public final class DataRegistrySettingsLoader {

    static final String FILE_NAME = "config.yml";
    private static final String DEFAULT_CONFIG = """
            # DataRegistry runtime settings
            # Do not store raw personal connection metadata unless explicitly needed.

            database:
              type: MYSQL
              connection-id: player_data_rw
              profiles:
                players:
                  connection-id: player_data_rw
                services:
                  connection-id: player_data_rw

            orm:
              schema-mode: validate

            privacy:
              persist-ip-address: false
              persist-virtual-host: false

            features:
              # Toggle built-in data domains.
              # Disabled domains are not registered in ORM and will not receive writes.
              online-status: true
              connection-info: true
              sessions: true
              name-history: true
              service-registry: true

            service-registry:
              heartbeat-interval-seconds: 30

            platform:
              bukkit:
                join-delay-ticks: 4

            validation:
              username:
                max-length: 32
              server:
                max-length: 64
              virtual-host:
                max-length: 255
              ip:
                max-length: 45
            """;

    private static final String DATABASE_TYPE_KEY = "database.type";
    private static final String DATABASE_CONNECTION_ID_KEY = "database.connection-id";
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

    /**
     * Loads runtime settings from {@code config.yml}, generating it from classpath resources on first run.
     */
    public DataRegistrySettings load(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = dataDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                writeDefaultConfig(configPath, resourceLoader);
                logger.info("Generated default DataRegistry config at " + configPath);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create DataRegistry config file at " + configPath, exception);
        }

        Map<?, ?> configRoot = readConfig(configPath, logger);
        return parse(configRoot, logger);
    }

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
        String baseConnectionId = validateWithBuilder(
                DATABASE_CONNECTION_ID_KEY,
                parseString(
                        configRoot,
                        DATABASE_CONNECTION_ID_KEY,
                        defaults.databaseConnectionId(),
                        logger
                ),
                defaults.databaseConnectionId(),
                logger,
                DataRegistrySettings.Builder::databaseConnectionId
        );
        builder.databaseConnectionId(baseConnectionId);
        builder.playerDatabaseConnectionId(validateWithBuilder(
                PLAYER_DATABASE_CONNECTION_ID_KEY,
                parseString(
                        configRoot,
                        PLAYER_DATABASE_CONNECTION_ID_KEY,
                        baseConnectionId,
                        logger
                ),
                baseConnectionId,
                logger,
                DataRegistrySettings.Builder::playerDatabaseConnectionId
        ));
        builder.serviceDatabaseConnectionId(validateWithBuilder(
                SERVICE_DATABASE_CONNECTION_ID_KEY,
                parseString(
                        configRoot,
                        SERVICE_DATABASE_CONNECTION_ID_KEY,
                        baseConnectionId,
                        logger
                ),
                baseConnectionId,
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

    private static Map<?, ?> readConfig(Path configPath, ILoggerAdapter logger) {
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            Object loaded = createSafeYaml().load(reader);
            if (loaded == null) {
                return Map.of();
            }
            if (loaded instanceof Map<?, ?> loadedMap) {
                return loadedMap;
            }
            logger.warn("Invalid root YAML node in " + FILE_NAME + ". Expected a map; using defaults.");
            return Map.of();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load DataRegistry config file at " + configPath, exception);
        }
    }

    private static Yaml createSafeYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(2_000_000);
        return new Yaml(new SafeConstructor(options));
    }

    private static void writeDefaultConfig(Path configPath, ClassLoader resourceLoader) throws IOException {
        try (InputStream input = resourceLoader.getResourceAsStream(FILE_NAME)) {
            if (input != null) {
                Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        try (Writer writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
            writer.write(DEFAULT_CONFIG);
        }
    }
}
