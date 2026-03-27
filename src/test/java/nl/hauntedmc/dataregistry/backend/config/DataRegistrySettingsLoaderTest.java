package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistrySettingsLoaderTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void parseReadsValidProperties() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        Map<String, Object> config = Map.of(
                "database", Map.of(
                        "type", "mysql",
                        "connection-id", "players-main"
                ),
                "orm", Map.of(
                        "schema-mode", "update"
                ),
                "privacy", Map.of(
                        "persist-ip-address", true,
                        "persist-virtual-host", true
                ),
                "platform", Map.of(
                        "bukkit", Map.of(
                                "join-delay-ticks", 12
                        )
                ),
                "validation", Map.of(
                        "username", Map.of("max-length", 24),
                        "server", Map.of("max-length", 48),
                        "virtual-host", Map.of("max-length", 180),
                        "ip", Map.of("max-length", 39)
                )
        );

        DataRegistrySettings settings = loader.parse(config, logger);

        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("players-main", settings.databaseConnectionId());
        assertEquals("update", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertTrue(settings.persistVirtualHost());
        assertEquals(12, settings.bukkitJoinDelayTicks());
        assertEquals(24, settings.usernameMaxLength());
        assertEquals(48, settings.serverNameMaxLength());
        assertEquals(180, settings.virtualHostMaxLength());
        assertEquals(39, settings.ipAddressMaxLength());
        assertFalse(logger.warnedWithThrowable);
    }

    @Test
    void parseFallsBackToDefaultsWhenValidationFails() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        Map<String, Object> config = Map.of(
                "database", Map.of(
                        "connection-id", "invalid id with spaces"
                ),
                "validation", Map.of(
                        "username", Map.of("max-length", 999)
                )
        );

        DataRegistrySettings settings = loader.parse(config, logger);
        DataRegistrySettings defaults = DataRegistrySettings.defaults();

        assertEquals(defaults.databaseType(), settings.databaseType());
        assertEquals(defaults.databaseConnectionId(), settings.databaseConnectionId());
        assertEquals(defaults.ormSchemaMode(), settings.ormSchemaMode());
        assertEquals(defaults.persistIpAddress(), settings.persistIpAddress());
        assertEquals(defaults.persistVirtualHost(), settings.persistVirtualHost());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertEquals(defaults.usernameMaxLength(), settings.usernameMaxLength());
        assertEquals(defaults.serverNameMaxLength(), settings.serverNameMaxLength());
        assertEquals(defaults.virtualHostMaxLength(), settings.virtualHostMaxLength());
        assertEquals(defaults.ipAddressMaxLength(), settings.ipAddressMaxLength());
        assertTrue(logger.warnedWithThrowable);
    }

    @Test
    void parseUsesDefaultsWhenTypesAreInvalid() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        Map<String, Object> config = Map.of(
                "database", Map.of(
                        "type", "not-a-real-db",
                        "connection-id", 12
                ),
                "orm", Map.of("schema-mode", ""),
                "privacy", Map.of(
                        "persist-ip-address", "yes",
                        "persist-virtual-host", "no"
                ),
                "platform", Map.of(
                        "bukkit", Map.of(
                                "join-delay-ticks", "x"
                        )
                )
        );

        DataRegistrySettings settings = loader.parse(config, logger);
        DataRegistrySettings defaults = DataRegistrySettings.defaults();

        assertEquals(defaults.databaseType(), settings.databaseType());
        assertEquals(defaults.databaseConnectionId(), settings.databaseConnectionId());
        assertEquals(defaults.ormSchemaMode(), settings.ormSchemaMode());
        assertEquals(defaults.persistIpAddress(), settings.persistIpAddress());
        assertEquals(defaults.persistVirtualHost(), settings.persistVirtualHost());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertTrue(logger.warnMessages.size() >= 4);
    }

    @Test
    void loadCreatesConfigFromClasspathResourceWhenMissing() throws Exception {
        String fileContent = """
                database:
                  type: MYSQL
                  connection-id: players-main
                orm:
                  schema-mode: update
                validation:
                  username:
                    max-length: 24
                """;

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        ClassLoader resourceLoader = new SingleResourceClassLoader(fileContent);

        DataRegistrySettings settings = loader.load(temporaryDirectory, resourceLoader, logger);

        Path configFile = temporaryDirectory.resolve("config.yml");
        assertTrue(Files.exists(configFile));
        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("players-main", settings.databaseConnectionId());
        assertEquals("update", settings.ormSchemaMode());
        assertEquals(24, settings.usernameMaxLength());
        assertTrue(logger.infoMessages.stream().anyMatch(msg -> msg.contains("Generated default DataRegistry config")));
    }

    @Test
    void loadFallsBackToEmbeddedDefaultWhenResourceIsMissing() throws Exception {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        ClassLoader emptyLoader = new SingleResourceClassLoader(null);

        DataRegistrySettings settings = loader.load(temporaryDirectory, emptyLoader, logger);
        Path configFile = temporaryDirectory.resolve("config.yml");
        String generatedContent = Files.readString(configFile);

        assertNotNull(settings);
        assertTrue(generatedContent.contains("# DataRegistry runtime settings"));
        assertEquals(DataRegistrySettings.defaults().databaseConnectionId(), settings.databaseConnectionId());
    }

    @Test
    void loadWarnsAndUsesDefaultsWhenYamlRootIsNotAMap() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, "just-a-scalar-value");

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);

        assertEquals(DataRegistrySettings.defaults().databaseConnectionId(), settings.databaseConnectionId());
        assertTrue(logger.warnMessages.stream().anyMatch(msg -> msg.contains("Invalid root YAML node")));
    }

    private static final class SingleResourceClassLoader extends ClassLoader {
        private final String configContent;

        private SingleResourceClassLoader(String configContent) {
            this.configContent = configContent;
        }

        @Override
        public InputStream getResourceAsStream(String name) {
            if (!"config.yml".equals(name) || configContent == null) {
                return null;
            }
            return new ByteArrayInputStream(configContent.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class RecordingLogger implements ILoggerAdapter {
        private final List<String> infoMessages = new ArrayList<>();
        private final List<String> warnMessages = new ArrayList<>();
        private boolean warnedWithThrowable;

        @Override
        public void info(String message) {
            infoMessages.add(message);
        }

        @Override
        public void warn(String message) {
            warnMessages.add(message);
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void info(String message, Throwable throwable) {
            infoMessages.add(message);
        }

        @Override
        public void warn(String message, Throwable throwable) {
            warnMessages.add(message);
            warnedWithThrowable = true;
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}
