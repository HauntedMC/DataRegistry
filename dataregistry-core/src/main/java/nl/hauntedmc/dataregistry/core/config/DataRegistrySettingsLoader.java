package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Loads and validates the operator-supplied DataRegistry runtime settings. */
public final class DataRegistrySettingsLoader {

    private final DataRegistrySettingsParser parser = new DataRegistrySettingsParser();

    /** Loads runtime settings, failing when a required clean-break setting is absent. */
    public DataRegistrySettings load(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = DataRegistryConfigIO.ensureConfigFile(dataDirectory, resourceLoader, logger);
        Map<?, ?> config = DataRegistryConfigIO.readConfig(configPath, logger);
        validateCleanBreakConfiguration(config);
        return parse(config, logger);
    }

    DataRegistrySettings parse(Map<?, ?> configRoot, ILoggerAdapter logger) {
        return parser.parse(configRoot, logger);
    }

    private static void validateCleanBreakConfiguration(Map<?, ?> root) {
        requireText(root, "database.profiles.sessions.connection-id");
        requireText(root, "sessions.namespace");
        requireValue(root, "sessions.lease-ttl-seconds");
        requireValue(root, "sessions.renewal-interval-seconds");
        requireValue(root, "sessions.expiry-safety-margin-millis");
        requireValue(root, "sessions.directory-freshness-seconds");
        requireText(root, "sessions.redis-outage-behavior");
        String schema = requireText(root, "orm.schema-mode");
        if (!"validate".equalsIgnoreCase(schema)) {
            throw new IllegalArgumentException("orm.schema-mode must be 'validate'");
        }
        String proxyId = requireText(root, "platform.velocity.service-name");
        if ("auto".equalsIgnoreCase(proxyId) || "proxy-1".equalsIgnoreCase(proxyId)) {
            throw new IllegalArgumentException(
                    "platform.velocity.service-name must be an explicit stable unique proxy instance ID");
        }
    }

    private static String requireText(Map<?, ?> root, String path) {
        Object value = requireValue(root, path);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException("Missing or blank required setting '" + path + "'");
        }
        return text.trim();
    }

    private static Object requireValue(Map<?, ?> root, String path) {
        Object current = root;
        for (String part : path.split("\\.")) {
            if (!(current instanceof Map<?, ?> map) || !map.containsKey(part)) {
                throw new IllegalArgumentException("Missing required setting '" + path + "'");
            }
            current = map.get(part);
        }
        if (current == null) throw new IllegalArgumentException("Missing required setting '" + path + "'");
        return current;
    }
}
