package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/** Loads and parses DataRegistry runtime settings without rewriting existing operator configuration. */
public final class DataRegistrySettingsLoader {

    private final DataRegistrySettingsParser parser = new DataRegistrySettingsParser();

    /** Loads runtime settings from {@code config.yml}, generating the packaged template on first run. */
    public DataRegistrySettings load(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = DataRegistryConfigIO.ensureConfigFile(dataDirectory, resourceLoader, logger);
        return parser.parse(DataRegistryConfigIO.readConfig(configPath, logger), logger);
    }

    DataRegistrySettings parse(Map<?, ?> configRoot, ILoggerAdapter logger) {
        return parser.parse(configRoot, logger);
    }
}
