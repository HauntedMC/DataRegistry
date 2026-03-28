package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Objects;

/**
 * Reads/writes {@code config.yml} with safe YAML parsing constraints.
 */
final class DataRegistryConfigIO {

    static final String FILE_NAME = "config.yml";

    private DataRegistryConfigIO() {
    }

    static Path ensureConfigFile(Path dataDirectory, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(dataDirectory, "dataDirectory must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        Path configPath = dataDirectory.resolve(FILE_NAME);
        try {
            Files.createDirectories(dataDirectory);
            if (Files.notExists(configPath)) {
                writeInitialConfig(configPath, resourceLoader);
                logger.info("Generated default DataRegistry config at " + configPath);
            }
            return configPath;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create DataRegistry config file at " + configPath, exception);
        }
    }

    static Map<?, ?> readConfig(Path configPath, ILoggerAdapter logger) {
        Objects.requireNonNull(configPath, "configPath must not be null");
        Objects.requireNonNull(logger, "logger must not be null");
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

    static boolean writeIfChanged(Path configPath, String content) {
        Objects.requireNonNull(configPath, "configPath must not be null");
        Objects.requireNonNull(content, "content must not be null");
        try {
            String current = Files.readString(configPath, StandardCharsets.UTF_8);
            if (current.equals(content)) {
                return false;
            }
            Files.writeString(configPath, content, StandardCharsets.UTF_8);
            return true;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to write DataRegistry config file at " + configPath, exception);
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

    private static void writeInitialConfig(Path configPath, ClassLoader resourceLoader) throws IOException {
        try (InputStream input = resourceLoader.getResourceAsStream(FILE_NAME)) {
            if (input != null) {
                Files.copy(input, configPath, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }
        Files.writeString(configPath, DataRegistryConfigSchema.defaultTemplate(), StandardCharsets.UTF_8);
    }
}
