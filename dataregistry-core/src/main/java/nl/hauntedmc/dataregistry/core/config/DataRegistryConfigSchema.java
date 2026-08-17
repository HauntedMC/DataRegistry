package nl.hauntedmc.dataregistry.core.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Defines the expected config tree shape and canonical output rendering. */
final class DataRegistryConfigSchema {

    private DataRegistryConfigSchema() {
    }

    static Map<String, Object> defaultsTree(DataRegistrySettings defaults) {
        Objects.requireNonNull(defaults, "defaults must not be null");
        Map<String, Object> root = new LinkedHashMap<>();

        Map<String, Object> database = new LinkedHashMap<>();
        database.put("type", defaults.databaseType().name());
        Map<String, Object> profiles = new LinkedHashMap<>();
        Map<String, Object> players = new LinkedHashMap<>();
        players.put("connection-id", defaults.playerDatabaseConnectionId());
        Map<String, Object> services = new LinkedHashMap<>();
        services.put("connection-id", defaults.serviceDatabaseConnectionId());
        profiles.put("players", players);
        profiles.put("services", services);
        database.put("profiles", profiles);
        root.put("database", database);

        Map<String, Object> orm = new LinkedHashMap<>();
        orm.put("schema-mode", defaults.ormSchemaMode());
        root.put("orm", orm);

        Map<String, Object> privacy = new LinkedHashMap<>();
        privacy.put("persist-ip-address", defaults.persistIpAddress());
        privacy.put("persist-virtual-host", defaults.persistVirtualHost());
        root.put("privacy", privacy);

        Map<String, Object> features = new LinkedHashMap<>();
        features.put("online-status", defaults.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        features.put("connection-info", defaults.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
        features.put("activity-summary", defaults.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY));
        features.put("sessions", defaults.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        features.put("session-visits", defaults.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS));
        features.put("population", defaults.isFeatureEnabled(DataRegistryFeature.POPULATION));
        features.put("playtime", defaults.isFeatureEnabled(DataRegistryFeature.PLAYTIME));
        features.put("language", defaults.isFeatureEnabled(DataRegistryFeature.LANGUAGE));
        features.put("nicknames", defaults.isFeatureEnabled(DataRegistryFeature.NICKNAMES));
        features.put("name-history", defaults.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        features.put("service-registry", defaults.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        root.put("features", features);

        PlaytimeTrackingSettings playtimeSettings = defaults.playtimeTrackingSettings();
        Map<String, Object> playtime = new LinkedHashMap<>();
        playtime.put("flush-interval-seconds", playtimeSettings.flushIntervalSeconds());
        playtime.put("resolve-unknown-servers-as-gamemode", playtimeSettings.resolveUnknownServersAsGamemode());
        playtime.put("ignored-gamemodes", List.copyOf(playtimeSettings.ignoredGamemodes()));
        playtime.put(
                "excluded-from-network-total-gamemodes",
                List.copyOf(playtimeSettings.excludedFromNetworkTotalGamemodes())
        );
        playtime.put("server-gamemode-rules", playtimeSettings.serverGamemodeRules().stream()
                .map(rule -> Map.of("match", rule.match(), "gamemode", rule.gamemodeKey()))
                .toList());
        root.put("playtime", playtime);

        Map<String, Object> serviceRegistry = new LinkedHashMap<>();
        serviceRegistry.put("heartbeat-interval-seconds", defaults.serviceHeartbeatIntervalSeconds());
        serviceRegistry.put("probe-interval-seconds", defaults.serviceProbeIntervalSeconds());
        serviceRegistry.put("probe-timeout-millis", defaults.serviceProbeTimeoutMillis());
        serviceRegistry.put("probe-retention-hours", defaults.serviceProbeRetentionHours());
        serviceRegistry.put("probe-purge-interval-hours", defaults.serviceProbePurgeIntervalHours());
        root.put("service-registry", serviceRegistry);

        Map<String, Object> retention = new LinkedHashMap<>();
        retention.put("lifecycle-outbox-days", defaults.lifecycleOutboxRetentionDays());
        retention.put("population-transition-days", defaults.populationTransitionRetentionDays());
        retention.put("service-instance-days", defaults.serviceInstanceRetentionDays());
        retention.put("closed-session-history-days", defaults.closedSessionHistoryRetentionDays());
        retention.put("purge-batch-size", defaults.retentionPurgeBatchSize());
        retention.put("player-history-purge-interval-hours", defaults.playerHistoryPurgeIntervalHours());
        retention.put("service-instance-purge-interval-hours", defaults.serviceInstancePurgeIntervalHours());
        root.put("retention", retention);

        Map<String, Object> lifecycle = new LinkedHashMap<>();
        lifecycle.put("write-max-attempts", defaults.lifecycleWriteMaxAttempts());
        lifecycle.put("retry-base-delay-millis", defaults.lifecycleRetryBaseDelayMillis());
        root.put("lifecycle", lifecycle);

        Map<String, Object> platform = new LinkedHashMap<>();
        Map<String, Object> bukkit = new LinkedHashMap<>();
        bukkit.put("join-delay-ticks", defaults.bukkitJoinDelayTicks());
        bukkit.put("register-service-instance", defaults.bukkitRegisterServiceInstance());
        bukkit.put("service-name", defaults.bukkitServiceName());
        Map<String, Object> velocity = new LinkedHashMap<>();
        velocity.put("service-name", defaults.velocityServiceName());
        platform.put("bukkit", bukkit);
        platform.put("velocity", velocity);
        root.put("platform", platform);

        Map<String, Object> query = new LinkedHashMap<>();
        query.put("executor-threads", defaults.queryExecutorThreads());
        query.put("timeout-millis", defaults.queryTimeoutMillis());
        query.put("development-thread-checks", defaults.queryDevelopmentThreadChecks());
        root.put("query", query);

        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("username", Map.of("max-length", defaults.usernameMaxLength()));
        validation.put("server", Map.of("max-length", defaults.serverNameMaxLength()));
        validation.put("gamemode", Map.of("max-length", playtimeSettings.gamemodeKeyMaxLength()));
        validation.put("virtual-host", Map.of("max-length", defaults.virtualHostMaxLength()));
        validation.put("ip", Map.of("max-length", defaults.ipAddressMaxLength()));
        root.put("validation", validation);
        return root;
    }

    static String defaultTemplate() {
        return renderCanonicalConfig(DataRegistrySettings.defaults());
    }

    static String renderCanonicalConfig(DataRegistrySettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        StringBuilder builder = new StringBuilder(4096);
        builder.append("# DataRegistry runtime settings\n");
        builder.append("# Do not store raw personal connection metadata unless explicitly needed.\n\n");
        builder.append("database:\n");
        builder.append("  # Applies to: Both.\n");
        builder.append("  # DataProvider database type (for example MYSQL).\n");
        builder.append("  type: ").append(settings.databaseType().name()).append('\n');
        builder.append("  profiles:\n");
        builder.append("    # Connection profile for player-domain tables. Must match [A-Za-z0-9._-]{1,64}.\n");
        builder.append("    players:\n");
        builder.append("      connection-id: ").append(settings.playerDatabaseConnectionId()).append('\n');
        builder.append("    # Connection profile for service-registry tables. Must match [A-Za-z0-9._-]{1,64}.\n");
        builder.append("    services:\n");
        builder.append("      connection-id: ").append(settings.serviceDatabaseConnectionId()).append('\n');
        builder.append('\n');
        builder.append("orm:\n");
        builder.append("  # Applies to: Both. Schema mode: validate, update, create, create-drop, or none.\n");
        builder.append("  schema-mode: ").append(settings.ormSchemaMode()).append('\n');
        builder.append('\n');
        builder.append("privacy:\n");
        builder.append("  # Applies to: Velocity lifecycle writes.\n");
        builder.append("  persist-ip-address: ").append(settings.persistIpAddress()).append('\n');
        builder.append("  persist-virtual-host: ").append(settings.persistVirtualHost()).append('\n');
        builder.append('\n');
        builder.append("features:\n");
        builder.append("  # Applies to: Both. Population depends on online-status, sessions and session-visits.\n");
        builder.append("  # Missing population prerequisites are restored automatically when population is enabled.\n");
        builder.append("  online-status: ").append(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)).append('\n');
        builder.append("  connection-info: ").append(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)).append('\n');
        builder.append("  activity-summary: ").append(settings.isFeatureEnabled(DataRegistryFeature.ACTIVITY_SUMMARY)).append('\n');
        builder.append("  sessions: ").append(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)).append('\n');
        builder.append("  session-visits: ").append(settings.isFeatureEnabled(DataRegistryFeature.SESSION_VISITS)).append('\n');
        builder.append("  population: ").append(settings.isFeatureEnabled(DataRegistryFeature.POPULATION)).append('\n');
        builder.append("  playtime: ").append(settings.isFeatureEnabled(DataRegistryFeature.PLAYTIME)).append('\n');
        builder.append("  language: ").append(settings.isFeatureEnabled(DataRegistryFeature.LANGUAGE)).append('\n');
        builder.append("  nicknames: ").append(settings.isFeatureEnabled(DataRegistryFeature.NICKNAMES)).append('\n');
        builder.append("  name-history: ").append(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)).append('\n');
        builder.append("  service-registry: ").append(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)).append('\n');
        builder.append('\n');

        PlaytimeTrackingSettings playtimeSettings = settings.playtimeTrackingSettings();
        builder.append("playtime:\n");
        builder.append("  # Applies to: Velocity. Population reuses these server-to-gamemode mapping rules.\n");
        builder.append("  flush-interval-seconds: ").append(playtimeSettings.flushIntervalSeconds()).append('\n');
        builder.append("  resolve-unknown-servers-as-gamemode: ")
                .append(playtimeSettings.resolveUnknownServersAsGamemode()).append('\n');
        builder.append("  ignored-gamemodes: ");
        appendInlineGamemodeKeys(builder, playtimeSettings.ignoredGamemodes());
        builder.append('\n');
        builder.append("  excluded-from-network-total-gamemodes: ");
        appendInlineGamemodeKeys(builder, playtimeSettings.excludedFromNetworkTotalGamemodes());
        builder.append('\n');
        builder.append("  # Ordered first-match server mapping rules. Supports '*' and '?' wildcards.\n");
        appendServerGamemodeRules(builder, playtimeSettings.serverGamemodeRules());
        builder.append('\n');

        builder.append("service-registry:\n");
        builder.append("  heartbeat-interval-seconds: ").append(settings.serviceHeartbeatIntervalSeconds()).append('\n');
        builder.append("  probe-interval-seconds: ").append(settings.serviceProbeIntervalSeconds()).append('\n');
        builder.append("  probe-timeout-millis: ").append(settings.serviceProbeTimeoutMillis()).append('\n');
        builder.append("  probe-retention-hours: ").append(settings.serviceProbeRetentionHours()).append('\n');
        builder.append("  probe-purge-interval-hours: ").append(settings.serviceProbePurgeIntervalHours()).append('\n');
        builder.append('\n');

        builder.append("retention:\n");
        builder.append("  # Lifecycle idempotency rows. -1 keeps them indefinitely.\n");
        builder.append("  lifecycle-outbox-days: ").append(settings.lifecycleOutboxRetentionDays()).append('\n');
        builder.append("  # Durable population transitions only; memberships, ordinals and aggregate state are never purged.\n");
        builder.append("  # Consumers can detect a cursor that fell behind retention through PopulationTransitionBatch.\n");
        builder.append("  population-transition-days: ").append(settings.populationTransitionRetentionDays()).append('\n');
        builder.append("  service-instance-days: ").append(settings.serviceInstanceRetentionDays()).append('\n');
        builder.append("  closed-session-history-days: ").append(settings.closedSessionHistoryRetentionDays()).append('\n');
        builder.append("  purge-batch-size: ").append(settings.retentionPurgeBatchSize()).append('\n');
        builder.append("  player-history-purge-interval-hours: ")
                .append(settings.playerHistoryPurgeIntervalHours()).append('\n');
        builder.append("  service-instance-purge-interval-hours: ")
                .append(settings.serviceInstancePurgeIntervalHours()).append('\n');
        builder.append('\n');

        builder.append("lifecycle:\n");
        builder.append("  write-max-attempts: ").append(settings.lifecycleWriteMaxAttempts()).append('\n');
        builder.append("  retry-base-delay-millis: ").append(settings.lifecycleRetryBaseDelayMillis()).append('\n');
        builder.append('\n');
        builder.append("platform:\n");
        builder.append("  bukkit:\n");
        builder.append("    join-delay-ticks: ").append(settings.bukkitJoinDelayTicks()).append('\n');
        builder.append("    register-service-instance: ").append(settings.bukkitRegisterServiceInstance()).append('\n');
        builder.append("    service-name: ").append(settings.bukkitServiceName()).append('\n');
        builder.append("  velocity:\n");
        builder.append("    service-name: ").append(settings.velocityServiceName()).append('\n');
        builder.append('\n');
        builder.append("query:\n");
        builder.append("  executor-threads: ").append(settings.queryExecutorThreads()).append('\n');
        builder.append("  timeout-millis: ").append(settings.queryTimeoutMillis()).append('\n');
        builder.append("  development-thread-checks: ").append(settings.queryDevelopmentThreadChecks()).append('\n');
        builder.append('\n');
        builder.append("validation:\n");
        builder.append("  username:\n    max-length: ").append(settings.usernameMaxLength()).append('\n');
        builder.append("  server:\n    max-length: ").append(settings.serverNameMaxLength()).append('\n');
        builder.append("  gamemode:\n    max-length: ").append(playtimeSettings.gamemodeKeyMaxLength()).append('\n');
        builder.append("  virtual-host:\n    max-length: ").append(settings.virtualHostMaxLength()).append('\n');
        builder.append("  ip:\n    max-length: ").append(settings.ipAddressMaxLength()).append('\n');
        return builder.toString();
    }

    private static void appendInlineGamemodeKeys(StringBuilder builder, Iterable<String> gamemodeKeys) {
        builder.append('[');
        boolean first = true;
        for (String gamemodeKey : gamemodeKeys) {
            if (!first) { builder.append(", "); }
            builder.append(gamemodeKey);
            first = false;
        }
        builder.append(']');
    }

    private static void appendServerGamemodeRules(
            StringBuilder builder,
            List<PlaytimeTrackingSettings.ServerGamemodeRule> rules
    ) {
        if (rules.isEmpty()) {
            builder.append("  server-gamemode-rules: []\n");
            return;
        }
        builder.append("  server-gamemode-rules:\n");
        for (PlaytimeTrackingSettings.ServerGamemodeRule rule : rules) {
            builder.append("    - match: \"").append(rule.match()).append("\"\n");
            builder.append("      gamemode: ").append(rule.gamemodeKey()).append('\n');
        }
    }
}
