package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Loads and parses DataRegistry runtime settings while keeping {@code config.yml} current. */
public final class DataRegistrySettingsLoader {

    private final DataRegistrySettingsParser parser = new DataRegistrySettingsParser();

    /** Loads runtime settings and adds any missing keys from the current packaged {@code config.yml}. */
    public DataRegistrySettings load(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = DataRegistryConfigIO.ensureConfigFile(dataDirectory, resourceLoader, logger);
        DataRegistryConfigIO.addMissingDefaults(configPath, resourceLoader, logger);
        return parse(DataRegistryConfigIO.readConfig(configPath, logger), logger);
    }

    DataRegistrySettings parse(Map<?, ?> configRoot, ILoggerAdapter logger) {
        return parser.parse(applyUpgradeDefaults(configRoot), logger);
    }

    private static Map<?, ?> applyUpgradeDefaults(Map<?, ?> configRoot) {
        Objects.requireNonNull(configRoot, "configRoot must not be null");
        Object featuresNode = configRoot.get("features");
        if (!(featuresNode instanceof Map<?, ?> features) || features.containsKey("population")) {
            return configRoot;
        }
        if (!isExplicitlyFalse(features.get("online-status"))
                && !isExplicitlyFalse(features.get("sessions"))
                && !isExplicitlyFalse(features.get("session-visits"))) {
            return configRoot;
        }

        Map<Object, Object> rootCopy = new LinkedHashMap<>();
        configRoot.forEach(rootCopy::put);
        Map<Object, Object> featuresCopy = new LinkedHashMap<>();
        features.forEach(featuresCopy::put);
        featuresCopy.put("population", false);
        rootCopy.put("features", featuresCopy);
        return rootCopy;
    }

    private static boolean isExplicitlyFalse(Object value) {
        if (value instanceof Boolean booleanValue) {
            return !booleanValue;
        }
        return value instanceof String stringValue && "false".equalsIgnoreCase(stringValue.trim());
    }
}
