package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.database.DatabaseType;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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
                "orm", Map.of("schema-mode", "validate"),
                "privacy", Map.of("persist-ip-address", true, "persist-virtual-host", true),
                "features", Map.of(
                        "online-status", true,
                        "connection-info", false,
                        "activity-summary", true,
                        "sessions", true,
                        "session-visits", true,
                        "playtime", true,
                        "language", false,
                        "nicknames", true,
                        "name-history", true,
                        "service-registry", true
                ),
                "playtime", Map.of(
                        "flush-interval-seconds", 45,
                        "resolve-unknown-servers-as-gamemode", false,
                        "ignored-gamemodes", List.of("queue", "limbo"),
                        "excluded-from-network-total-gamemodes", List.of("lobby"),
                        "server-gamemode-rules", List.of(
                                Map.of("match", "lobby-*", "gamemode", "lobby"),
                                Map.of("match", "skyblock-*", "gamemode", "skyblock")
                        )
                ),
                "service-registry", Map.of(
                        "heartbeat-interval-seconds", 45,
                        "probe-interval-seconds", 18,
                        "probe-timeout-millis", 2200,
                        "probe-retention-hours", 336,
                        "probe-purge-interval-hours", 24
                ),
                "retention", Map.of(
                        "lifecycle-outbox-days", 30,
                        "population-transition-days", 120,
                        "service-instance-days", 14,
                        "closed-session-history-days", 60,
                        "purge-batch-size", 750,
                        "player-history-purge-interval-hours", 6,
                        "service-instance-purge-interval-hours", 36
                ),
                "platform", Map.of(
                        "bukkit", Map.of(
                                "join-delay-ticks", 12,
                                "register-service-instance", true,
                                "service-name", "lobby-01"
                        ),
                        "velocity", Map.of("service-name", "proxy-eu")
                ),
                "query", Map.of(
                        "executor-threads", 4,
                        "timeout-millis", 2500,
                        "development-thread-checks", true
                ),
                "validation", Map.of(
                        "username", Map.of("max-length", 24),
                        "server", Map.of("max-length", 48),
                        "gamemode", Map.of("max-length", 32),
                        "virtual-host", Map.of("max-length", 180),
                        "ip", Map.of("max-length", 39)
                )
        );

        DataRegistrySettings settings = loader.parse(config, logger);

        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("players-rw", settings.playerDatabaseConnectionId());
        assertEquals("services-rw", settings.serviceDatabaseConnectionId());
        assertEquals("validate", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertTrue(settings.persistVirtualHost());
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE));
        assertEquals(45, settings.playtimeTrackingSettings().flushIntervalSeconds());
        assertFalse(settings.playtimeTrackingSettings().resolveUnknownServersAsGamemode());
        assertEquals(List.of("queue", "limbo"), List.copyOf(settings.playtimeTrackingSettings().ignoredGamemodes()));
        assertEquals(List.of("lobby"), List.copyOf(settings.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes()));
        assertEquals(2, settings.playtimeTrackingSettings().serverGamemodeRules().size());
        assertEquals(45, settings.serviceHeartbeatIntervalSeconds());
        assertEquals(18, settings.serviceProbeIntervalSeconds());
        assertEquals(2200, settings.serviceProbeTimeoutMillis());
        assertEquals(336, settings.serviceProbeRetentionHours());
        assertEquals(24, settings.serviceProbePurgeIntervalHours());
        assertEquals(30, settings.lifecycleOutboxRetentionDays());
        assertEquals(120, settings.populationTransitionRetentionDays());
        assertEquals(14, settings.serviceInstanceRetentionDays());
        assertEquals(60, settings.closedSessionHistoryRetentionDays());
        assertEquals(750, settings.retentionPurgeBatchSize());
        assertEquals(6, settings.playerHistoryPurgeIntervalHours());
        assertEquals(36, settings.serviceInstancePurgeIntervalHours());
        assertEquals(12, settings.bukkitJoinDelayTicks());
        assertTrue(settings.bukkitRegisterServiceInstance());
        assertEquals("lobby-01", settings.bukkitServiceName());
        assertEquals("proxy-eu", settings.velocityServiceName());
        assertEquals(4, settings.queryExecutorThreads());
        assertEquals(2500, settings.queryTimeoutMillis());
        assertTrue(settings.queryDevelopmentThreadChecks());
        assertEquals(24, settings.usernameMaxLength());
        assertEquals(48, settings.serverNameMaxLength());
        assertEquals(32, settings.playtimeTrackingSettings().gamemodeKeyMaxLength());
        assertEquals(180, settings.virtualHostMaxLength());
        assertEquals(39, settings.ipAddressMaxLength());
        assertFalse(logger.warnedWithThrowable);
    }

    @Test
    void parseUsesDefaultsPerMissingOrInvalidSetting() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        DataRegistrySettings defaults = DataRegistrySettings.defaults();

        DataRegistrySettings settings = loader.parse(Map.of(
                "database", Map.of(
                        "type", "not-a-real-db",
                        "profiles", Map.of(
                                "players", Map.of("connection-id", 12),
                                "services", Map.of("connection-id", "services-rw")
                        )
                ),
                "privacy", Map.of("persist-ip-address", true),
                "features", Map.of(
                        "sessions", false,
                        "population", false,
                        "playtime", false
                ),
                "service-registry", Map.of("heartbeat-interval-seconds", 500),
                "retention", Map.of("population-transition-days", "invalid"),
                "validation", Map.of("username", Map.of("max-length", 999))
        ), logger);

        assertEquals(defaults.databaseType(), settings.databaseType());
        assertEquals(defaults.playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
        assertEquals("services-rw", settings.serviceDatabaseConnectionId());
        assertTrue(settings.persistIpAddress());
        assertEquals(defaults.persistVirtualHost(), settings.persistVirtualHost());
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertEquals(defaults.serviceHeartbeatIntervalSeconds(), settings.serviceHeartbeatIntervalSeconds());
        assertEquals(defaults.populationTransitionRetentionDays(), settings.populationTransitionRetentionDays());
        assertEquals(defaults.usernameMaxLength(), settings.usernameMaxLength());
        assertTrue(logger.warnMessages.size() >= 4);
    }

    @Test
    void parseSkipsInvalidExternalPlayerDataConnectionEntries() {
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = new DataRegistrySettingsLoader().parse(Map.of(
                "player-deletion", Map.of("external-connections", List.of(
                        Map.of("connection-id", "feature-data", "player-id-columns", List.of()),
                        Map.of("connection-id", "bad id", "player-id-columns", List.of("player_id")),
                        Map.of("connection-id", "valid-data", "database-type", "not-a-db",
                                "player-id-columns", List.of("player_id")),
                        Map.of("connection-id", "valid-data", "player-id-columns", List.of("player_id")),
                        Map.of("connection-id", "VALID-DATA", "player-id-columns", List.of("owner_player_id"))
                ))
        ), logger);

        assertEquals(1, settings.externalPlayerDataConnections().size());
        assertEquals("valid-data", settings.externalPlayerDataConnections().getFirst().connectionId());
        assertEquals(DatabaseType.MYSQL, settings.externalPlayerDataConnections().getFirst().databaseType());
        assertTrue(logger.warnMessages.size() >= 4);
    }

    @Test
    void parseAutomaticallyRestoresPopulationStructuralDependencies() {
        DataRegistrySettings settings = new DataRegistrySettingsLoader().parse(Map.of(
                "features", Map.of(
                        "online-status", false,
                        "sessions", false,
                        "session-visits", false,
                        "population", true,
                        "playtime", false
                )
        ), new RecordingLogger());

        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
    }

    @Test
    void loadCopiesClasspathConfigExactlyOnFirstRun() throws Exception {
        String fileContent = """
                # keep packaged documentation exactly
                database:
                  type: MYSQL
                  profiles:
                    players:
                      connection-id: players-main
                    services:
                      connection-id: services-main
                    sessions:
                      connection-id: sessions-main
                sessions:
                  namespace: test-network
                  lease-ttl-seconds: 15
                  renewal-interval-seconds: 3
                  expiry-safety-margin-millis: 500
                  directory-freshness-seconds: 10
                  redis-outage-behavior: PRESERVE_UNTIL_EXPIRY
                orm:
                  schema-mode: validate
                platform:
                  velocity:
                    service-name: proxy-test-01
                features:
                  sessions: false
                  population: false
                  playtime: false
                """;
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(
                temporaryDirectory,
                new SingleResourceClassLoader(fileContent),
                logger
        );

        Path configFile = temporaryDirectory.resolve("config.yml");
        assertEquals(fileContent, Files.readString(configFile));
        assertEquals("players-main", settings.playerDatabaseConnectionId());
        assertEquals("services-main", settings.serviceDatabaseConnectionId());
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertTrue(logger.infoMessages.stream().anyMatch(message -> message.contains("Generated default DataRegistry config")));
    }

    @Test
    void loadFailsClearlyWhenPackagedConfigIsMissing() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> loader.load(temporaryDirectory, new SingleResourceClassLoader(null), new RecordingLogger())
        );

        assertTrue(exception.getMessage().contains("Failed to create DataRegistry config file"));
        assertTrue(exception.getCause().getMessage().contains("Missing bundled DataRegistry config resource"));
        assertFalse(Files.exists(temporaryDirectory.resolve("config.yml")));
    }

    @Test
    void shippedConfigIsAManualCutoverTemplateThatFailsUntilIdentitiesAreSupplied() throws Exception {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();
        ClassLoader classLoader = getClass().getClassLoader();
        String packaged;
        try (InputStream input = classLoader.getResourceAsStream("config.yml")) {
            packaged = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> loader.load(temporaryDirectory, classLoader, logger)
        );
        String generated = Files.readString(temporaryDirectory.resolve("config.yml"));

        assertEquals(packaged, generated);
        assertTrue(exception.getMessage().contains("sessions.namespace"));
        assertTrue(generated.contains("# Schema mode controls ORM DDL behavior:"));
        assertTrue(generated.contains("# Ordered first-match server mapping rules."));
        assertTrue(generated.contains("population: true"));
        assertTrue(generated.contains("population-transition-days: 90"));
    }

    @Test
    void loadDoesNotAddMissingSettingsOrRewriteLegacyConfiguration() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        String existing = """
                # custom operator comment must survive
                database:
                  type: MYSQL
                  profiles:
                    players:
                      connection-id: players-main
                old-or-plugin-specific-section:
                  keep-me: true
                features:
                  sessions: false
                  population: false
                  playtime: false
                """;
        Files.writeString(configFile, existing);
        RecordingLogger logger = new RecordingLogger();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DataRegistrySettingsLoader().load(
                        temporaryDirectory,
                        getClass().getClassLoader(),
                        logger
                )
        );

        String updated = Files.readString(configFile);
        assertEquals(existing, updated);
        assertTrue(updated.contains("# custom operator comment must survive"));
        assertTrue(updated.contains("connection-id: players-main"));
        assertTrue(updated.contains("old-or-plugin-specific-section:"));
        assertTrue(updated.contains("keep-me: true"));
        assertTrue(exception.getMessage().contains("database.profiles.sessions.connection-id"));
    }

    @Test
    void invalidYamlRootFailsWithoutTouchingFile() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        String existing = "just-a-scalar-value";
        Files.writeString(configFile, existing);
        RecordingLogger logger = new RecordingLogger();

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new DataRegistrySettingsLoader().load(
                        temporaryDirectory,
                        getClass().getClassLoader(),
                        logger
                )
        );

        assertEquals(existing, Files.readString(configFile));
        assertTrue(exception.getMessage().contains("database.profiles.sessions.connection-id"));
        assertTrue(logger.warnMessages.stream().anyMatch(message -> message.contains("Invalid root YAML node")));
    }

    @Test
    void configuredPlaytimeCollectionsRemainStableAcrossLoads() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        String existing = """
                # preserve formatting too
                database:
                  profiles:
                    sessions:
                      connection-id: sessions-main
                sessions:
                  namespace: test-network
                  lease-ttl-seconds: 15
                  renewal-interval-seconds: 3
                  expiry-safety-margin-millis: 500
                  directory-freshness-seconds: 10
                  redis-outage-behavior: PRESERVE_UNTIL_EXPIRY
                orm:
                  schema-mode: validate
                platform:
                  velocity:
                    service-name: proxy-test-01
                playtime:
                  ignored-gamemodes: [dev, demo, event, limbo]
                  excluded-from-network-total-gamemodes: [lobby, bouwserver]
                  server-gamemode-rules:
                    - match: "lobby-*"
                      gamemode: lobby
                    - match: "skyblock-?"
                      gamemode: skyblock
                """;
        Files.writeString(configFile, existing);
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings first = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String afterFirstLoad = Files.readString(configFile);
        DataRegistrySettings second = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);

        assertEquals(afterFirstLoad, Files.readString(configFile));
        assertTrue(afterFirstLoad.contains("# preserve formatting too"));
        assertEquals(first.playtimeTrackingSettings().ignoredGamemodes(), second.playtimeTrackingSettings().ignoredGamemodes());
        assertEquals(
                first.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes(),
                second.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes()
        );
        assertEquals(first.playtimeTrackingSettings().serverGamemodeRules(), second.playtimeTrackingSettings().serverGamemodeRules());
        assertEquals(2, first.playtimeTrackingSettings().serverGamemodeRules().size());
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
