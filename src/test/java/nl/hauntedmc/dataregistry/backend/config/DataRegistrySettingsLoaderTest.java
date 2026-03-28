package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
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
                        "profiles", Map.of(
                                "players", Map.of("connection-id", "players-rw"),
                                "services", Map.of("connection-id", "services-rw")
                        )
                ),
                "orm", Map.of(
                        "schema-mode", "update"
                ),
                "privacy", Map.of(
                        "persist-ip-address", true,
                        "persist-virtual-host", true
                ),
                "features", Map.of(
                        "online-status", true,
                        "connection-info", false,
                        "sessions", true,
                        "name-history", true,
                        "service-registry", true
                ),
                "service-registry", Map.of(
                        "heartbeat-interval-seconds", 45
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
        assertEquals("players-rw", settings.playerDatabaseConnectionId());
        assertEquals("services-rw", settings.serviceDatabaseConnectionId());
        assertEquals("update", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertTrue(settings.persistVirtualHost());
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertEquals(45, settings.serviceHeartbeatIntervalSeconds());
        assertEquals(12, settings.bukkitJoinDelayTicks());
        assertEquals(24, settings.usernameMaxLength());
        assertEquals(48, settings.serverNameMaxLength());
        assertEquals(180, settings.virtualHostMaxLength());
        assertEquals(39, settings.ipAddressMaxLength());
        assertFalse(logger.warnedWithThrowable);
    }

    @Test
    void parseFallsBackPerSettingWhenValidationFails() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        Map<String, Object> config = Map.of(
                "database", Map.of(
                        "type", "mysql",
                        "profiles", Map.of(
                                "services", Map.of("connection-id", "services-rw")
                        )
                ),
                "orm", Map.of(
                        "schema-mode", "update"
                ),
                "privacy", Map.of(
                        "persist-ip-address", true
                ),
                "features", Map.of(
                        "sessions", false,
                        "service-registry", true
                ),
                "service-registry", Map.of(
                        "heartbeat-interval-seconds", 500
                ),
                "validation", Map.of(
                        "username", Map.of("max-length", 999),
                        "server", Map.of("max-length", 48)
                )
        );

        DataRegistrySettings settings = loader.parse(config, logger);
        DataRegistrySettings defaults = DataRegistrySettings.defaults();

        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("update", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertEquals(defaults.persistVirtualHost(), settings.persistVirtualHost());
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertEquals(
                defaults.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS),
                settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)
        );
        assertEquals(defaults.serviceHeartbeatIntervalSeconds(), settings.serviceHeartbeatIntervalSeconds());
        assertEquals(defaults.playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
        assertEquals("services-rw", settings.serviceDatabaseConnectionId());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertEquals(defaults.usernameMaxLength(), settings.usernameMaxLength());
        assertEquals(48, settings.serverNameMaxLength());
        assertEquals(defaults.virtualHostMaxLength(), settings.virtualHostMaxLength());
        assertEquals(defaults.ipAddressMaxLength(), settings.ipAddressMaxLength());
        assertTrue(logger.warnMessages.size() >= 2);
        assertFalse(logger.warnedWithThrowable);
    }

    @Test
    void parseUsesDefaultsWhenTypesAreInvalid() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        Map<String, Object> config = Map.of(
                "database", Map.of(
                        "type", "not-a-real-db",
                        "profiles", Map.of(
                                "players", Map.of("connection-id", 12),
                                "services", Map.of("connection-id", "bad id")
                        )
                ),
                "orm", Map.of("schema-mode", ""),
                "privacy", Map.of(
                        "persist-ip-address", "yes",
                        "persist-virtual-host", "no"
                ),
                "features", Map.of(
                        "online-status", "on",
                        "service-registry", "on"
                ),
                "service-registry", Map.of(
                        "heartbeat-interval-seconds", "x"
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
        assertEquals(defaults.playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
        assertEquals(defaults.ormSchemaMode(), settings.ormSchemaMode());
        assertEquals(defaults.persistIpAddress(), settings.persistIpAddress());
        assertEquals(defaults.persistVirtualHost(), settings.persistVirtualHost());
        assertEquals(
                defaults.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS),
                settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)
        );
        assertEquals(
                defaults.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY),
                settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)
        );
        assertEquals(defaults.serviceHeartbeatIntervalSeconds(), settings.serviceHeartbeatIntervalSeconds());
        assertEquals(defaults.serviceDatabaseConnectionId(), settings.serviceDatabaseConnectionId());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertTrue(logger.warnMessages.size() >= 4);
    }

    @Test
    void loadCreatesConfigFromClasspathResourceWhenMissing() throws Exception {
        String fileContent = """
                database:
                  type: MYSQL
                  profiles:
                    players:
                      connection-id: players-main
                    services:
                      connection-id: services-main
                orm:
                  schema-mode: update
                features:
                  sessions: false
                service-registry:
                  heartbeat-interval-seconds: 40
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
        assertEquals("players-main", settings.playerDatabaseConnectionId());
        assertEquals("services-main", settings.serviceDatabaseConnectionId());
        assertEquals("update", settings.ormSchemaMode());
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertEquals(40, settings.serviceHeartbeatIntervalSeconds());
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
        assertEquals(DataRegistrySettings.defaults().playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
    }

    @Test
    void bundledConfigDisablesSensitivePersistenceByDefault() throws Exception {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String generatedContent = Files.readString(temporaryDirectory.resolve("config.yml"));

        assertFalse(settings.persistIpAddress());
        assertFalse(settings.persistVirtualHost());
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertTrue(generatedContent.contains("persist-ip-address: false"));
        assertTrue(generatedContent.contains("persist-virtual-host: false"));
        assertTrue(generatedContent.contains("online-status: true"));
        assertTrue(generatedContent.contains("connection-info: true"));
        assertTrue(generatedContent.contains("sessions: true"));
        assertTrue(generatedContent.contains("name-history: true"));
        assertTrue(generatedContent.contains("service-registry: true"));
    }

    @Test
    void loadWarnsAndUsesDefaultsWhenYamlRootIsNotAMap() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, "just-a-scalar-value");

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);

        assertEquals(DataRegistrySettings.defaults().playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
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
