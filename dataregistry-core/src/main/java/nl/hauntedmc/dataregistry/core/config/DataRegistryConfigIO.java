package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.representer.Representer;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Reads {@code config.yml} safely and keeps it aligned with the packaged defaults. */
final class DataRegistryConfigIO {

    static final String FILE_NAME = "config.yml";
    static final String BACKUP_FILE_NAME = "config.yml.bak";
    private static final String SERVER_GAMEMODE_RULES_PATH = "playtime.server-gamemode-rules";
    private static final Set<String> SERVER_GAMEMODE_RULE_KEYS = Set.of("match", "gamemode");

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

    static void addMissingDefaults(Path configPath, ClassLoader resourceLoader, ILoggerAdapter logger) {
        Objects.requireNonNull(configPath, "configPath must not be null");
        Objects.requireNonNull(resourceLoader, "resourceLoader must not be null");
        Objects.requireNonNull(logger, "logger must not be null");

        try {
            String packagedConfig = readPackagedConfig(resourceLoader);
            Yaml yaml = createCommentPreservingYaml();
            Node existingRoot;
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                existingRoot = yaml.compose(reader);
            }
            Node defaultRoot = yaml.compose(new StringReader(packagedConfig));

            if (existingRoot == null) {
                Path backupPath = backupConfig(configPath);
                writeAtomically(configPath, packagedConfig);
                logger.info("Backed up previous DataRegistry config to " + backupPath);
                logger.info("Updated DataRegistry config with missing default settings.");
                return;
            }
            if (!(existingRoot instanceof MappingNode existingMap)) {
                return;
            }
            if (!(defaultRoot instanceof MappingNode defaultMap)) {
                throw new IOException("Bundled DataRegistry config root must be a map");
            }

            List<String> unknownPaths = new ArrayList<>();
            List<String> incompatibleStructurePaths = new ArrayList<>();
            collectConfigIssues(existingMap, defaultMap, "", unknownPaths, incompatibleStructurePaths);
            if (!unknownPaths.isEmpty()) {
                unknownPaths.sort(String::compareTo);
                logger.warn("Unknown DataRegistry config settings are ignored: " + String.join(", ", unknownPaths));
            }
            if (!incompatibleStructurePaths.isEmpty()) {
                incompatibleStructurePaths.sort(String::compareTo);
                logger.warn("DataRegistry config settings have incompatible YAML structure and may use defaults: "
                        + String.join(", ", incompatibleStructurePaths));
            }

            List<String> addedPaths = new ArrayList<>();
            if (!mergeMissingDefaults(existingMap, defaultMap, "", addedPaths)) {
                return;
            }

            StringWriter writer = new StringWriter();
            yaml.serialize(existingRoot, writer);
            Path backupPath = backupConfig(configPath);
            writeAtomically(configPath, writer.toString());
            logger.info("Backed up previous DataRegistry config to " + backupPath);
            String settingLabel = addedPaths.size() == 1 ? "setting" : "settings";
            logger.info("Updated DataRegistry config with " + addedPaths.size() + " missing default " + settingLabel
                    + ": " + String.join(", ", addedPaths));
        } catch (YAMLException exception) {
            throw new IllegalStateException("Failed to parse DataRegistry config file at " + configPath, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to update DataRegistry config file at " + configPath, exception);
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
        } catch (YAMLException exception) {
            throw new IllegalStateException("Failed to parse DataRegistry config file at " + configPath, exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load DataRegistry config file at " + configPath, exception);
        }
    }

    private static void collectConfigIssues(
            MappingNode existing,
            MappingNode defaults,
            String path,
            List<String> unknownPaths,
            List<String> incompatibleStructurePaths
    ) {
        for (NodeTuple existingEntry : existing.getValue()) {
            if (!(existingEntry.getKeyNode() instanceof ScalarNode scalar)) {
                continue;
            }
            String key = scalar.getValue();
            String entryPath = path.isEmpty() ? key : path + "." + key;
            NodeTuple defaultEntry = findEntry(defaults, key);
            if (defaultEntry == null) {
                unknownPaths.add(entryPath);
                continue;
            }

            Node existingValue = existingEntry.getValueNode();
            Node defaultValue = defaultEntry.getValueNode();
            if (defaultValue instanceof MappingNode defaultMap) {
                if (existingValue instanceof MappingNode existingMap) {
                    collectConfigIssues(
                            existingMap,
                            defaultMap,
                            entryPath,
                            unknownPaths,
                            incompatibleStructurePaths
                    );
                } else {
                    incompatibleStructurePaths.add(entryPath);
                }
                continue;
            }
            if (defaultValue instanceof SequenceNode) {
                if (existingValue instanceof SequenceNode existingSequence) {
                    if (SERVER_GAMEMODE_RULES_PATH.equals(entryPath)) {
                        collectUnknownServerGamemodeRuleSettings(existingSequence, entryPath, unknownPaths);
                    }
                } else {
                    incompatibleStructurePaths.add(entryPath);
                }
                continue;
            }
            if (defaultValue instanceof ScalarNode && !(existingValue instanceof ScalarNode)) {
                incompatibleStructurePaths.add(entryPath);
            }
        }
    }

    private static void collectUnknownServerGamemodeRuleSettings(
            SequenceNode rules,
            String path,
            List<String> unknownPaths
    ) {
        for (int index = 0; index < rules.getValue().size(); index++) {
            Node ruleNode = rules.getValue().get(index);
            if (!(ruleNode instanceof MappingNode ruleMap)) {
                continue;
            }
            for (NodeTuple ruleEntry : ruleMap.getValue()) {
                if (!(ruleEntry.getKeyNode() instanceof ScalarNode scalar)) {
                    continue;
                }
                String key = scalar.getValue();
                if (!SERVER_GAMEMODE_RULE_KEYS.contains(key)) {
                    unknownPaths.add(path + "[" + index + "]." + key);
                }
            }
        }
    }

    private static boolean mergeMissingDefaults(
            MappingNode existing,
            MappingNode defaults,
            String path,
            List<String> addedPaths
    ) {
        boolean changed = false;
        for (NodeTuple defaultEntry : defaults.getValue()) {
            String key = scalarKey(defaultEntry.getKeyNode());
            String entryPath = path.isEmpty() ? key : path + "." + key;
            NodeTuple existingEntry = findEntry(existing, key);
            if (existingEntry == null) {
                NodeTuple compatibleEntry = compatibleDefaultEntry(existing, defaultEntry, path, key);
                existing.getValue().add(compatibleEntry);
                collectSettingPaths(compatibleEntry.getValueNode(), entryPath, addedPaths);
                changed = true;
                continue;
            }
            if (existingEntry.getValueNode() instanceof MappingNode existingMap
                    && defaultEntry.getValueNode() instanceof MappingNode defaultMap) {
                changed |= mergeMissingDefaults(existingMap, defaultMap, entryPath, addedPaths);
            }
        }
        return changed;
    }

    private static void collectSettingPaths(Node node, String path, List<String> paths) {
        if (node instanceof MappingNode mapping && !mapping.getValue().isEmpty()) {
            for (NodeTuple entry : mapping.getValue()) {
                String childPath = path + "." + scalarKey(entry.getKeyNode());
                collectSettingPaths(entry.getValueNode(), childPath, paths);
            }
            return;
        }
        paths.add(path);
    }

    private static NodeTuple compatibleDefaultEntry(
            MappingNode existing,
            NodeTuple defaultEntry,
            String path,
            String key
    ) {
        if (!"features".equals(path)) {
            return defaultEntry;
        }

        Node disabledPrerequisite = switch (key) {
            case "session-visits", "playtime" -> explicitlyFalseValue(existing, "sessions");
            case "population" -> explicitlyFalseValue(existing, "online-status", "sessions", "session-visits");
            default -> null;
        };
        if (disabledPrerequisite == null) {
            return defaultEntry;
        }
        return new NodeTuple(defaultEntry.getKeyNode(), disabledPrerequisite);
    }

    private static Node explicitlyFalseValue(MappingNode mapping, String... keys) {
        for (String key : keys) {
            NodeTuple entry = findEntry(mapping, key);
            if (entry != null && isExplicitlyFalse(entry.getValueNode())) {
                return entry.getValueNode();
            }
        }
        return null;
    }

    private static boolean isExplicitlyFalse(Node node) {
        return node instanceof ScalarNode scalar && "false".equalsIgnoreCase(scalar.getValue().trim());
    }

    private static NodeTuple findEntry(MappingNode mapping, String key) {
        for (NodeTuple entry : mapping.getValue()) {
            if (entry.getKeyNode() instanceof ScalarNode scalar && key.equals(scalar.getValue())) {
                return entry;
            }
        }
        return null;
    }

    private static String scalarKey(Node keyNode) {
        if (keyNode instanceof ScalarNode scalar) {
            return scalar.getValue();
        }
        throw new IllegalStateException("Bundled DataRegistry config contains a non-scalar key");
    }

    private static Path backupConfig(Path configPath) throws IOException {
        Path backupPath = configPath.resolveSibling(BACKUP_FILE_NAME);
        Files.copy(configPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
        return backupPath;
    }

    private static void writeAtomically(Path configPath, String content) throws IOException {
        Path directory = configPath.toAbsolutePath().getParent();
        if (directory == null) {
            throw new IOException("Config path has no parent directory: " + configPath);
        }

        Path temporaryPath = Files.createTempFile(directory, ".config.yml-", ".tmp");
        try {
            Files.writeString(temporaryPath, content, StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporaryPath,
                        configPath,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporaryPath, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryPath);
        }
    }

    private static Yaml createSafeYaml() {
        return new Yaml(new SafeConstructor(createLoaderOptions()));
    }

    private static Yaml createCommentPreservingYaml() {
        LoaderOptions loaderOptions = createLoaderOptions();
        loaderOptions.setProcessComments(true);
        DumperOptions dumperOptions = new DumperOptions();
        dumperOptions.setProcessComments(true);
        dumperOptions.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        dumperOptions.setIndent(2);
        return new Yaml(
                new SafeConstructor(loaderOptions),
                new Representer(dumperOptions),
                dumperOptions,
                loaderOptions
        );
    }

    private static LoaderOptions createLoaderOptions() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        options.setMaxAliasesForCollections(20);
        options.setNestingDepthLimit(50);
        options.setCodePointLimit(2_000_000);
        return options;
    }

    private static void writeInitialConfig(Path configPath, ClassLoader resourceLoader) throws IOException {
        writeAtomically(configPath, readPackagedConfig(resourceLoader));
    }

    private static String readPackagedConfig(ClassLoader resourceLoader) throws IOException {
        try (InputStream input = resourceLoader.getResourceAsStream(FILE_NAME)) {
            if (input == null) {
                throw new IOException("Missing bundled DataRegistry config resource: " + FILE_NAME);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
