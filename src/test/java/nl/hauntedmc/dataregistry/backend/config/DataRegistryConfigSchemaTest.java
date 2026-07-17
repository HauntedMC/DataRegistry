package nl.hauntedmc.dataregistry.backend.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryConfigSchemaTest {

    @Test
    void defaultsTreeContainsAllExpectedSectionsAndKeys() {
        DataRegistrySettings defaults = DataRegistrySettings.defaults();

        Map<String, Object> tree = DataRegistryConfigSchema.defaultsTree(defaults);

        assertTrue(tree.containsKey("database"));
        assertTrue(tree.containsKey("orm"));
        assertTrue(tree.containsKey("privacy"));
        assertTrue(tree.containsKey("features"));
        assertTrue(tree.containsKey("playtime"));
        assertTrue(tree.containsKey("service-registry"));
        assertTrue(tree.containsKey("platform"));
        assertTrue(tree.containsKey("validation"));

        @SuppressWarnings("unchecked")
        Map<String, Object> database = (Map<String, Object>) tree.get("database");
        @SuppressWarnings("unchecked")
        Map<String, Object> profiles = (Map<String, Object>) database.get("profiles");
        @SuppressWarnings("unchecked")
        Map<String, Object> players = (Map<String, Object>) profiles.get("players");
        @SuppressWarnings("unchecked")
        Map<String, Object> services = (Map<String, Object>) profiles.get("services");
        assertEquals(defaults.databaseType().name(), database.get("type"));
        assertEquals(defaults.playerDatabaseConnectionId(), players.get("connection-id"));
        assertEquals(defaults.serviceDatabaseConnectionId(), services.get("connection-id"));
    }

    @Test
    void renderCanonicalConfigDocumentsSchemaModesAndValidationRanges() {
        String rendered = DataRegistryConfigSchema.renderCanonicalConfig(DataRegistrySettings.defaults());

        assertTrue(rendered.contains("Applies to: Both."));
        assertTrue(rendered.contains("Applies to: Velocity."));
        assertTrue(rendered.contains("Applies to: Bukkit."));
        assertTrue(rendered.contains("schema-mode: validate"));
        assertTrue(rendered.contains("validate: verify schema only (recommended for production)"));
        assertTrue(rendered.contains("update: auto-apply additive changes (development/staging)"));
        assertTrue(rendered.contains("create: drop and recreate schema at startup (ephemeral/local only)"));
        assertTrue(rendered.contains("create-drop: create at startup, drop at shutdown (tests/local only)"));
        assertTrue(rendered.contains("none: disable ORM schema management (use external migrations)"));
        assertTrue(rendered.contains("playtime:"));
        assertTrue(rendered.contains("flush-interval-seconds: 30"));
        assertTrue(rendered.contains("resolve-unknown-servers-as-gamemode: true"));
        assertTrue(rendered.contains("server-gamemode-rules: []"));
        assertTrue(rendered.contains("activity-summary: true"));
        assertTrue(rendered.contains("session-visits: true"));
        assertTrue(rendered.contains("language: true"));
        assertTrue(rendered.contains("nicknames: true"));
        assertTrue(rendered.contains("Player lifecycle domains are written on Velocity; Bukkit still needs these toggles"));
        assertTrue(rendered.contains("heartbeat-interval-seconds: 30"));
        assertTrue(rendered.contains("probe-interval-seconds: 15"));
        assertTrue(rendered.contains("probe-timeout-millis: 1500"));
        assertTrue(rendered.contains("probe-retention-hours: 168"));
        assertTrue(rendered.contains("probe-purge-interval-hours: 12"));
        assertTrue(rendered.contains("register-service-instance: false"));
        assertTrue(rendered.contains("service-name: auto"));
        assertTrue(rendered.contains("max-length: 32"));
        assertTrue(rendered.contains("max-length: 64"));
        assertTrue(rendered.contains("max-length: 255"));
        assertTrue(rendered.contains("max-length: 45"));
    }
}
