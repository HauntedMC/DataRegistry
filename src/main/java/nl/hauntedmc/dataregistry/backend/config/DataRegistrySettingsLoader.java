package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.nio.file.Path;
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

        DataRegistryConfigReconciler.Result reconciliation = DataRegistryConfigReconciler.reconcile(
                rawRoot,
                DataRegistryConfigSchema.defaultsTree(DataRegistrySettings.defaults())
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
}
