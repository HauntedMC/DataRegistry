package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Orchestrates config file lifecycle: creation, schema reconciliation, parsing, and canonical rewrite.
 */
public final class DataRegistrySettingsLoader {

    private final DataRegistrySettingsParser parser = new DataRegistrySettingsParser();

    /**
     * Loads runtime settings from {@code config.yml}, generating it on first run.
     */
    public DataRegistrySettings load(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = DataRegistryConfigIO.ensureConfigFile(dataDirectory, resourceLoader, logger);
        Map<?, ?> rawRoot = DataRegistryConfigIO.readConfig(configPath, logger);
        Map<String, Object> schemaRoot = buildSchemaDefaults(rawRoot);

        DataRegistryConfigReconciler.Result reconciliation = DataRegistryConfigReconciler.reconcile(
                rawRoot,
                schemaRoot
        );
        if (reconciliation.changed()) {
            logger.info(
                    "Validated config schema at " + configPath + " (" +
                            "added=" + reconciliation.addedKeys() + ", " +
                            "removed=" + reconciliation.removedKeys() + ", " +
                            "replaced=" + reconciliation.replacedValues() + ")."
            );
        }

        DataRegistrySettings settings = parser.parse(reconciliation.configRoot(), logger);
        String canonicalContent = DataRegistryConfigSchema.renderCanonicalConfig(settings);
        if (DataRegistryConfigIO.writeIfChanged(configPath, canonicalContent)) {
            logger.info(
                    "Reconciled DataRegistry config schema at " + configPath +
                            " (added missing keys, removed unknown keys)."
            );
        }
        return settings;
    }

    DataRegistrySettings parse(Map<?, ?> configRoot, ILoggerAdapter logger) {
        return parser.parse(configRoot, logger);
    }

    private static Map<String, Object> buildSchemaDefaults(Map<?, ?> rawRoot) {
        Map<String, Object> schemaRoot = new LinkedHashMap<>(
                DataRegistryConfigSchema.defaultsTree(DataRegistrySettings.defaults())
        );
        Boolean sessionsEnabled = resolveBoolean(rawRoot, "features", "sessions");
        if (Boolean.FALSE.equals(sessionsEnabled)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> features = (Map<String, Object>) schemaRoot.get("features");
            if (features != null) {
                features.put("playtime", false);
                features.put("session-visits", false);
            }
        }
        return schemaRoot;
    }

    @SuppressWarnings("unchecked")
    private static Boolean resolveBoolean(Map<?, ?> rawRoot, String sectionKey, String valueKey) {
        Object section = rawRoot.get(sectionKey);
        if (!(section instanceof Map<?, ?> sectionMap)) {
            return null;
        }
        Object value = ((Map<?, ?>) sectionMap).get(valueKey);
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }
}
