package nl.hauntedmc.dataregistry.core.config;

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
                        "service-instance-days", 14,
                        "closed-session-history-days", 60
                ),
                "platform", Map.of(
                        "bukkit", Map.of(
                                "join-delay-ticks", 12,
                                "register-service-instance", true,
                                "service-name", "lobby-01"
                        ),
                        "velocity", Map.of(
                                "service-name", "proxy-eu"
                        )
                ),
                "query", Map.of(
                        "executor-threads", 4,
                        "timeout-millis", 2500,
                        "development-thread-checks", false
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
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.NICKNAMES));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertEquals(45, settings.playtimeTrackingSettings().flushIntervalSeconds());
        assertFalse(settings.playtimeTrackingSettings().resolveUnknownServersAsGamemode());
        assertTrue(settings.playtimeTrackingSettings().ignoredGamemodes().contains("queue"));
        assertTrue(settings.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes().contains("lobby"));
        assertEquals(2, settings.playtimeTrackingSettings().serverGamemodeRules().size());
        assertEquals(45, settings.serviceHeartbeatIntervalSeconds());
        assertEquals(18, settings.serviceProbeIntervalSeconds());
        assertEquals(2200, settings.serviceProbeTimeoutMillis());
        assertEquals(336, settings.serviceProbeRetentionHours());
        assertEquals(24, settings.serviceProbePurgeIntervalHours());
        assertEquals(30, settings.lifecycleOutboxRetentionDays());
        assertEquals(14, settings.serviceInstanceRetentionDays());
        assertEquals(60, settings.closedSessionHistoryRetentionDays());
        assertEquals(12, settings.bukkitJoinDelayTicks());
        assertTrue(settings.bukkitRegisterServiceInstance());
        assertEquals("lobby-01", settings.bukkitServiceName());
        assertEquals("proxy-eu", settings.velocityServiceName());
        assertEquals(4, settings.queryExecutorThreads());
        assertEquals(2500, settings.queryTimeoutMillis());
        assertFalse(settings.queryDevelopmentThreadChecks());
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
                        "population", false,
                        "playtime", false,
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
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertEquals(
                defaults.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS),
                settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)
        );
        assertEquals(defaults.serviceHeartbeatIntervalSeconds(), settings.serviceHeartbeatIntervalSeconds());
        assertEquals(defaults.serviceProbeIntervalSeconds(), settings.serviceProbeIntervalSeconds());
        assertEquals(defaults.serviceProbeTimeoutMillis(), settings.serviceProbeTimeoutMillis());
        assertEquals(defaults.serviceProbeRetentionHours(), settings.serviceProbeRetentionHours());
        assertEquals(defaults.serviceProbePurgeIntervalHours(), settings.serviceProbePurgeIntervalHours());
        assertEquals(defaults.lifecycleOutboxRetentionDays(), settings.lifecycleOutboxRetentionDays());
        assertEquals(defaults.serviceInstanceRetentionDays(), settings.serviceInstanceRetentionDays());
        assertEquals(defaults.closedSessionHistoryRetentionDays(), settings.closedSessionHistoryRetentionDays());
        assertEquals(defaults.playerDatabaseConnectionId(), settings.playerDatabaseConnectionId());
        assertEquals("services-rw", settings.serviceDatabaseConnectionId());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertEquals(defaults.bukkitRegisterServiceInstance(), settings.bukkitRegisterServiceInstance());
        assertEquals(defaults.bukkitServiceName(), settings.bukkitServiceName());
        assertEquals(defaults.velocityServiceName(), settings.velocityServiceName());
        assertEquals(defaults.usernameMaxLength(), settings.usernameMaxLength());
        assertEquals(48, settings.serverNameMaxLength());
        assertEquals(defaults.virtualHostMaxLength(), settings.virtualHostMaxLength());
        assertEquals(defaults.ipAddressMaxLength(), settings.ipAddressMaxLength());
        assertTrue(logger.warnMessages.size() >= 2);
        assertFalse(logger.warnedWithThrowable);
    }

    @Test
    void parseReadsLifecycleAndRetentionMaintenanceControls() {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.parse(Map.of(
                "retention", Map.of(
                        "purge-batch-size", 750,
                        "player-history-purge-interval-hours", 6,
                        "service-instance-purge-interval-hours", 36
                ),
                "lifecycle", Map.of(
                        "write-max-attempts", 5,
                        "retry-base-delay-millis", 40
                )
        ), logger);

        assertEquals(750, settings.retentionPurgeBatchSize());
        assertEquals(6, settings.playerHistoryPurgeIntervalHours());
        assertEquals(36, settings.serviceInstancePurgeIntervalHours());
        assertEquals(5, settings.lifecycleWriteMaxAttempts());
        assertEquals(40, settings.lifecycleRetryBaseDelayMillis());
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
                        "heartbeat-interval-seconds", "x",
                        "probe-interval-seconds", "x",
                        "probe-timeout-millis", "x",
                        "probe-retention-hours", "x",
                        "probe-purge-interval-hours", "x"
                ),
                "retention", Map.of(
                        "lifecycle-outbox-days", "x",
                        "service-instance-days", "x",
                        "closed-session-history-days", "x"
                ),
                "platform", Map.of(
                        "bukkit", Map.of(
                                "join-delay-ticks", "x",
                                "register-service-instance", "yes",
                                "service-name", 123
                        ),
                        "velocity", Map.of(
                                "service-name", 456
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
        assertEquals(defaults.serviceProbeIntervalSeconds(), settings.serviceProbeIntervalSeconds());
        assertEquals(defaults.serviceProbeTimeoutMillis(), settings.serviceProbeTimeoutMillis());
        assertEquals(defaults.serviceProbeRetentionHours(), settings.serviceProbeRetentionHours());
        assertEquals(defaults.serviceProbePurgeIntervalHours(), settings.serviceProbePurgeIntervalHours());
        assertEquals(defaults.lifecycleOutboxRetentionDays(), settings.lifecycleOutboxRetentionDays());
        assertEquals(defaults.serviceInstanceRetentionDays(), settings.serviceInstanceRetentionDays());
        assertEquals(defaults.closedSessionHistoryRetentionDays(), settings.closedSessionHistoryRetentionDays());
        assertEquals(defaults.serviceDatabaseConnectionId(), settings.serviceDatabaseConnectionId());
        assertEquals(defaults.bukkitJoinDelayTicks(), settings.bukkitJoinDelayTicks());
        assertEquals(defaults.bukkitRegisterServiceInstance(), settings.bukkitRegisterServiceInstance());
        assertEquals(defaults.bukkitServiceName(), settings.bukkitServiceName());
        assertEquals(defaults.velocityServiceName(), settings.velocityServiceName());
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
                  population: false
                  playtime: false
                service-registry:
                  heartbeat-interval-seconds: 40
                  probe-interval-seconds: 20
                  probe-timeout-millis: 1800
                  probe-retention-hours: 240
                  probe-purge-interval-hours: 12
                platform:
                  bukkit:
                    register-service-instance: true
                    service-name: lobby-01
                  velocity:
                    service-name: proxy-01
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
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertEquals(40, settings.serviceHeartbeatIntervalSeconds());
        assertEquals(20, settings.serviceProbeIntervalSeconds());
        assertEquals(1800, settings.serviceProbeTimeoutMillis());
        assertEquals(240, settings.serviceProbeRetentionHours());
        assertEquals("lobby-01", settings.bukkitServiceName());
        assertEquals("proxy-01", settings.velocityServiceName());
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
    void shippedConfigExposesOperationalLifecycleAndRetentionControls() throws Exception {
        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String generatedContent = Files.readString(temporaryDirectory.resolve("config.yml"));

        assertEquals(500, settings.retentionPurgeBatchSize());
        assertEquals(1, settings.playerHistoryPurgeIntervalHours());
        assertEquals(24, settings.serviceInstancePurgeIntervalHours());
        assertEquals(3, settings.lifecycleWriteMaxAttempts());
        assertEquals(25, settings.lifecycleRetryBaseDelayMillis());
        assertTrue(generatedContent.contains("purge-batch-size: 500"));
        assertTrue(generatedContent.contains("write-max-attempts: 3"));
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

    @Test
    void loadReconcilesConfigByAddingMissingKeysAndRemovingUnknownOnes() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                database:
                  type: MYSQL
                  profiles:
                    players:
                      connection-id: players-main
                orm:
                  schema-mode: update
                old-section:
                  should-be-removed: true
                features:
                  sessions: false
                  population: false
                  playtime: false
                """);

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings settings = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String reconciledConfig = Files.readString(configFile);

        assertEquals("players-main", settings.playerDatabaseConnectionId());
        assertEquals(DataRegistrySettings.defaults().serviceDatabaseConnectionId(), settings.serviceDatabaseConnectionId());
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        assertTrue(reconciledConfig.contains("services:"));
        assertTrue(reconciledConfig.contains("connection-id: player_data_rw"));
        assertTrue(reconciledConfig.contains("privacy:"));
        assertTrue(reconciledConfig.contains("validation:"));
        assertFalse(reconciledConfig.contains("old-section:"));
        assertTrue(logger.infoMessages.stream().anyMatch(msg -> msg.contains("Reconciled DataRegistry config schema")));
    }

    @Test
    void loadPreservesConfiguredPlaytimeListsAndRulesAcrossRestarts() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                playtime:
                  ignored-gamemodes: [dev, demo, event, limbo]
                  excluded-from-network-total-gamemodes: [lobby, bouwserver]
                  server-gamemode-rules:
                    - match: "lobby-*"
                      gamemode: lobby
                    - match: "skyblock-?"
                      gamemode: skyblock
                """);

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings firstLoad = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        DataRegistrySettings secondLoad = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String persistedConfig = Files.readString(configFile);

        assertEquals(
                List.of("dev", "demo", "event", "limbo"),
                List.copyOf(firstLoad.playtimeTrackingSettings().ignoredGamemodes())
        );
        assertEquals(
                List.of("lobby", "bouwserver"),
                List.copyOf(firstLoad.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes())
        );
        assertEquals(2, firstLoad.playtimeTrackingSettings().serverGamemodeRules().size());
        assertEquals("lobby-*", firstLoad.playtimeTrackingSettings().serverGamemodeRules().get(0).match());
        assertEquals("skyblock", firstLoad.playtimeTrackingSettings().serverGamemodeRules().get(1).gamemodeKey());
        assertEquals(
                firstLoad.playtimeTrackingSettings().ignoredGamemodes(),
                secondLoad.playtimeTrackingSettings().ignoredGamemodes()
        );
        assertEquals(
                firstLoad.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes(),
                secondLoad.playtimeTrackingSettings().excludedFromNetworkTotalGamemodes()
        );
        assertEquals(
                firstLoad.playtimeTrackingSettings().serverGamemodeRules(),
                secondLoad.playtimeTrackingSettings().serverGamemodeRules()
        );
        assertTrue(persistedConfig.contains("ignored-gamemodes: [dev, demo, event, limbo]"));
        assertTrue(persistedConfig.contains("excluded-from-network-total-gamemodes: [lobby, bouwserver]"));
        assertTrue(persistedConfig.contains("- match: \"lobby-*\""));
        assertTrue(persistedConfig.contains("- match: \"skyblock-?\""));
    }

    @Test
    void loadPreservesNonDefaultScalarSettingsAcrossRestarts() throws Exception {
        Path configFile = temporaryDirectory.resolve("config.yml");
        Files.writeString(configFile, """
                database:
                  type: MYSQL
                  profiles:
                    players:
                      connection-id: players-main
                    services:
                      connection-id: services-main
                orm:
                  schema-mode: validate
                privacy:
                  persist-ip-address: true
                  persist-virtual-host: true
                features:
                  online-status: false
                  connection-info: false
                  activity-summary: false
                  sessions: true
                  session-visits: true
                  population: false
                  playtime: true
                  language: false
                  nicknames: false
                  name-history: false
                  service-registry: false
                playtime:
                  flush-interval-seconds: 45
                  resolve-unknown-servers-as-gamemode: false
                service-registry:
                  heartbeat-interval-seconds: 50
                  probe-interval-seconds: 20
                  probe-timeout-millis: 2500
                  probe-retention-hours: 48
                  probe-purge-interval-hours: 36
                retention:
                  lifecycle-outbox-days: 90
                  service-instance-days: 60
                  closed-session-history-days: 30
                  purge-batch-size: 750
                  player-history-purge-interval-hours: 6
                  service-instance-purge-interval-hours: 12
                lifecycle:
                  write-max-attempts: 5
                  retry-base-delay-millis: 100
                platform:
                  bukkit:
                    join-delay-ticks: 12
                    register-service-instance: true
                    service-name: lobby-01
                  velocity:
                    service-name: proxy-eu
                query:
                  executor-threads: 4
                  timeout-millis: 2500
                  development-thread-checks: true
                validation:
                  username:
                    max-length: 24
                  server:
                    max-length: 48
                  gamemode:
                    max-length: 32
                  virtual-host:
                    max-length: 180
                  ip:
                    max-length: 39
                """);

        DataRegistrySettingsLoader loader = new DataRegistrySettingsLoader();
        RecordingLogger logger = new RecordingLogger();

        DataRegistrySettings firstLoad = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        DataRegistrySettings secondLoad = loader.load(temporaryDirectory, getClass().getClassLoader(), logger);
        String persistedConfig = Files.readString(configFile);

        assertEquals("players-main", secondLoad.playerDatabaseConnectionId());
        assertEquals("services-main", secondLoad.serviceDatabaseConnectionId());
        assertEquals("validate", secondLoad.ormSchemaMode());
        assertTrue(secondLoad.persistIpAddress());
        assertTrue(secondLoad.persistVirtualHost());
        assertFalse(secondLoad.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertFalse(secondLoad.isFeatureEnabled(DataRegistryFeature.POPULATION));
        assertFalse(secondLoad.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        assertEquals(45, secondLoad.playtimeTrackingSettings().flushIntervalSeconds());
        assertFalse(secondLoad.playtimeTrackingSettings().resolveUnknownServersAsGamemode());
        assertEquals(50, secondLoad.serviceHeartbeatIntervalSeconds());
        assertEquals(20, secondLoad.serviceProbeIntervalSeconds());
        assertEquals(2500, secondLoad.serviceProbeTimeoutMillis());
        assertEquals(48, secondLoad.serviceProbeRetentionHours());
        assertEquals(36, secondLoad.serviceProbePurgeIntervalHours());
        assertEquals(90, secondLoad.lifecycleOutboxRetentionDays());
        assertEquals(60, secondLoad.serviceInstanceRetentionDays());
        assertEquals(30, secondLoad.closedSessionHistoryRetentionDays());
        assertEquals(750, secondLoad.retentionPurgeBatchSize());
        assertEquals(6, secondLoad.playerHistoryPurgeIntervalHours());
        assertEquals(12, secondLoad.serviceInstancePurgeIntervalHours());
        assertEquals(5, secondLoad.lifecycleWriteMaxAttempts());
        assertEquals(100, secondLoad.lifecycleRetryBaseDelayMillis());
        assertEquals(12, secondLoad.bukkitJoinDelayTicks());
        assertTrue(secondLoad.bukkitRegisterServiceInstance());
        assertEquals("lobby-01", secondLoad.bukkitServiceName());
        assertEquals("proxy-eu", secondLoad.velocityServiceName());
        assertEquals(4, secondLoad.queryExecutorThreads());
        assertEquals(2500, secondLoad.queryTimeoutMillis());
        assertTrue(secondLoad.queryDevelopmentThreadChecks());
        assertEquals(24, secondLoad.usernameMaxLength());
        assertEquals(48, secondLoad.serverNameMaxLength());
        assertEquals(32, secondLoad.playtimeTrackingSettings().gamemodeKeyMaxLength());
        assertEquals(180, secondLoad.virtualHostMaxLength());
        assertEquals(39, secondLoad.ipAddressMaxLength());
        assertEquals(firstLoad.enabledFeatures(), secondLoad.enabledFeatures());
        assertTrue(persistedConfig.contains("connection-id: players-main"));
        assertTrue(persistedConfig.contains("schema-mode: validate"));
        assertTrue(persistedConfig.contains("timeout-millis: 2500"));
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
