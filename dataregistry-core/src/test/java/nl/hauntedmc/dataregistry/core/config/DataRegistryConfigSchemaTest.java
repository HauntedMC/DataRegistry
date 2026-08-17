package nl.hauntedmc.dataregistry.core.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertTrue(tree.containsKey("retention"));
        assertTrue(tree.containsKey("lifecycle"));
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
        @SuppressWarnings("unchecked")
        Map<String, Object> features = (Map<String, Object>) tree.get("features");
        @SuppressWarnings("unchecked")
        Map<String, Object> retention = (Map<String, Object>) tree.get("retention");
        @SuppressWarnings("unchecked")
        Map<String, Object> lifecycle = (Map<String, Object>) tree.get("lifecycle");
        assertEquals(defaults.databaseType().name(), database.get("type"));
        assertEquals(defaults.playerDatabaseConnectionId(), players.get("connection-id"));
        assertEquals(defaults.serviceDatabaseConnectionId(), services.get("connection-id"));
        assertEquals(true, features.get("population"));
        assertEquals(90, retention.get("population-transition-days"));
        assertEquals(-1, retention.get("closed-session-history-days"));
        assertEquals(500, retention.get("purge-batch-size"));
        assertEquals(3, lifecycle.get("write-max-attempts"));
    }

    @Test
    void renderCanonicalConfigDocumentsSchemaModesAndValidationRanges() {
        String rendered = DataRegistryConfigSchema.renderCanonicalConfig(DataRegistrySettings.defaults());

        assertTrue(rendered.contains("Applies to: Both."));
        assertTrue(rendered.contains("Applies to: Velocity."));
        assertTrue(rendered.contains("Applies to: Bukkit."));
        assertTrue(rendered.contains("schema-mode: update"));
        assertTrue(rendered.contains("validate: verify schema only"));
        assertTrue(rendered.contains("update: auto-apply additive changes (default)"));
        assertTrue(rendered.contains("create: drop and recreate schema at startup (ephemeral/local only)"));
        assertTrue(rendered.contains("create-drop: create at startup, drop at shutdown (tests/local only)"));
        assertTrue(rendered.contains("none: disable ORM schema management"));
        assertTrue(rendered.contains("playtime:"));
        assertTrue(rendered.contains("flush-interval-seconds: 30"));
        assertTrue(rendered.contains("resolve-unknown-servers-as-gamemode: true"));
        assertTrue(rendered.contains("only when it is a valid gamemode key"));
        assertTrue(rendered.contains("server-gamemode-rules: []"));
        assertTrue(rendered.contains("activity-summary: true"));
        assertTrue(rendered.contains("session-visits: true"));
        assertTrue(rendered.contains("population: true"));
        assertTrue(rendered.contains("language: true"));
        assertTrue(rendered.contains("nicknames: true"));
        assertTrue(rendered.contains("Requires online-status, sessions and session-visits"));
        assertTrue(rendered.contains("population-transition-days: 90"));
        assertTrue(rendered.contains("heartbeat-interval-seconds: 30"));
        assertTrue(rendered.contains("probe-interval-seconds: 15"));
        assertTrue(rendered.contains("probe-timeout-millis: 1500"));
        assertTrue(rendered.contains("probe-retention-hours: -1"));
        assertTrue(rendered.contains("probe-purge-interval-hours: 12"));
        assertTrue(rendered.contains("purge-batch-size: 500"));
        assertTrue(rendered.contains("player-history-purge-interval-hours: 1"));
        assertTrue(rendered.contains("service-instance-purge-interval-hours: 24"));
        assertTrue(rendered.contains("write-max-attempts: 3"));
        assertTrue(rendered.contains("retry-base-delay-millis: 25"));
        assertTrue(rendered.contains("register-service-instance: false"));
        assertTrue(rendered.contains("service-name: auto"));
        assertTrue(rendered.contains("service name (up to 96 characters)"));
        assertTrue(rendered.contains("max-length: 32"));
        assertTrue(rendered.contains("max-length: 64"));
        assertTrue(rendered.contains("max-length: 255"));
        assertTrue(rendered.contains("max-length: 45"));
    }

    @Test
    void packagedDefaultConfigMatchesCanonicalTemplateIncludingDocumentation() throws Exception {
        try (InputStream input = DataRegistryConfigSchemaTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(input);
            String packaged = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals(DataRegistryConfigSchema.defaultTemplate(), packaged);
        }
    }
}
