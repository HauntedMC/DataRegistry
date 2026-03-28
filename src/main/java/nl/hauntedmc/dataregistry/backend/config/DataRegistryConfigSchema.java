package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Defines the expected config tree shape and canonical output rendering.
 */
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
        features.put("sessions", defaults.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        features.put("name-history", defaults.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        features.put("service-registry", defaults.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
        root.put("features", features);

        Map<String, Object> serviceRegistry = new LinkedHashMap<>();
        serviceRegistry.put("heartbeat-interval-seconds", defaults.serviceHeartbeatIntervalSeconds());
        serviceRegistry.put("probe-interval-seconds", defaults.serviceProbeIntervalSeconds());
        serviceRegistry.put("probe-timeout-millis", defaults.serviceProbeTimeoutMillis());
        serviceRegistry.put("probe-retention-hours", defaults.serviceProbeRetentionHours());
        root.put("service-registry", serviceRegistry);

        Map<String, Object> platform = new LinkedHashMap<>();
        Map<String, Object> bukkit = new LinkedHashMap<>();
        bukkit.put("join-delay-ticks", defaults.bukkitJoinDelayTicks());
        bukkit.put("service-name", defaults.bukkitServiceName());
        Map<String, Object> velocity = new LinkedHashMap<>();
        velocity.put("service-name", defaults.velocityServiceName());
        platform.put("bukkit", bukkit);
        platform.put("velocity", velocity);
        root.put("platform", platform);

        Map<String, Object> validation = new LinkedHashMap<>();
        Map<String, Object> username = new LinkedHashMap<>();
        username.put("max-length", defaults.usernameMaxLength());
        Map<String, Object> server = new LinkedHashMap<>();
        server.put("max-length", defaults.serverNameMaxLength());
        Map<String, Object> virtualHost = new LinkedHashMap<>();
        virtualHost.put("max-length", defaults.virtualHostMaxLength());
        Map<String, Object> ip = new LinkedHashMap<>();
        ip.put("max-length", defaults.ipAddressMaxLength());
        validation.put("username", username);
        validation.put("server", server);
        validation.put("virtual-host", virtualHost);
        validation.put("ip", ip);
        root.put("validation", validation);

        return root;
    }

    static String defaultTemplate() {
        return renderCanonicalConfig(DataRegistrySettings.defaults());
    }

    static String renderCanonicalConfig(DataRegistrySettings settings) {
        Objects.requireNonNull(settings, "settings must not be null");
        StringBuilder builder = new StringBuilder(2048);
        builder.append("# DataRegistry runtime settings\n");
        builder.append("# Do not store raw personal connection metadata unless explicitly needed.\n\n");
        builder.append("database:\n");
        builder.append("  # DataProvider database type (for example MYSQL).\n");
        builder.append("  type: ").append(settings.databaseType().name()).append('\n');
        builder.append("  profiles:\n");
        builder.append("    # Connection profile for player-domain tables.\n");
        builder.append("    # Must match [A-Za-z0-9._-]{1,64}.\n");
        builder.append("    players:\n");
        builder.append("      connection-id: ").append(settings.playerDatabaseConnectionId()).append('\n');
        builder.append("    # Connection profile for service-registry tables.\n");
        builder.append("    # Must match [A-Za-z0-9._-]{1,64}.\n");
        builder.append("    services:\n");
        builder.append("      connection-id: ").append(settings.serviceDatabaseConnectionId()).append('\n');
        builder.append('\n');
        builder.append("orm:\n");
        builder.append("  # Schema mode controls ORM DDL behavior:\n");
        builder.append("  # validate: verify schema only (recommended for production)\n");
        builder.append("  # update: auto-apply additive changes (development/staging)\n");
        builder.append("  # create: drop and recreate schema at startup (ephemeral/local only)\n");
        builder.append("  # create-drop: create at startup, drop at shutdown (tests/local only)\n");
        builder.append("  # none: disable ORM schema management (use external migrations)\n");
        builder.append("  schema-mode: ").append(settings.ormSchemaMode()).append('\n');
        builder.append('\n');
        builder.append("privacy:\n");
        builder.append("  # Persist player IP addresses in connection info.\n");
        builder.append("  persist-ip-address: ").append(settings.persistIpAddress()).append('\n');
        builder.append("  # Persist player virtual host values in connection info.\n");
        builder.append("  persist-virtual-host: ").append(settings.persistVirtualHost()).append('\n');
        builder.append('\n');
        builder.append("features:\n");
        builder.append("  # Toggle built-in data domains.\n");
        builder.append("  # Disabled domains are not registered in ORM and will not receive writes.\n");
        builder.append("  online-status: ").append(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)).append('\n');
        builder.append("  connection-info: ").append(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)).append('\n');
        builder.append("  sessions: ").append(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS)).append('\n');
        builder.append("  name-history: ").append(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY)).append('\n');
        builder.append("  service-registry: ").append(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY)).append('\n');
        builder.append('\n');
        builder.append("service-registry:\n");
        builder.append("  # Heartbeat write interval for service instances (seconds, 5-300).\n");
        builder.append("  heartbeat-interval-seconds: ").append(settings.serviceHeartbeatIntervalSeconds()).append('\n');
        builder.append("  # Velocity backend-probe interval (seconds, 5-300).\n");
        builder.append("  probe-interval-seconds: ").append(settings.serviceProbeIntervalSeconds()).append('\n');
        builder.append("  # Velocity backend-probe timeout (milliseconds, 200-10000).\n");
        builder.append("  probe-timeout-millis: ").append(settings.serviceProbeTimeoutMillis()).append('\n');
        builder.append("  # Retention window for probe history cleanup (hours, 1-2160).\n");
        builder.append("  probe-retention-hours: ").append(settings.serviceProbeRetentionHours()).append('\n');
        builder.append('\n');
        builder.append("platform:\n");
        builder.append("  bukkit:\n");
        builder.append("    # Delay after join event before snapshotting status (ticks, 0-200).\n");
        builder.append("    join-delay-ticks: ").append(settings.bukkitJoinDelayTicks()).append('\n');
        builder.append("    # Backend logical service name; set equal to the Velocity server name for stable identity.\n");
        builder.append("    # Set to 'auto' to derive from host:port fallback naming.\n");
        builder.append("    service-name: ").append(settings.bukkitServiceName()).append('\n');
        builder.append("  velocity:\n");
        builder.append("    # Proxy logical service name; set to 'auto' to derive from host:port fallback naming.\n");
        builder.append("    service-name: ").append(settings.velocityServiceName()).append('\n');
        builder.append('\n');
        builder.append("validation:\n");
        builder.append("  username:\n");
        builder.append("    # Max persisted username length (1-32).\n");
        builder.append("    max-length: ").append(settings.usernameMaxLength()).append('\n');
        builder.append("  server:\n");
        builder.append("    # Max persisted server name length (1-64).\n");
        builder.append("    max-length: ").append(settings.serverNameMaxLength()).append('\n');
        builder.append("  virtual-host:\n");
        builder.append("    # Max persisted virtual host length (1-255).\n");
        builder.append("    max-length: ").append(settings.virtualHostMaxLength()).append('\n');
        builder.append("  ip:\n");
        builder.append("    # Max persisted IP text length (7-45).\n");
        builder.append("    max-length: ").append(settings.ipAddressMaxLength()).append('\n');
        return builder.toString();
    }
}
