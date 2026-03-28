package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistrySettingsTest {

    @Test
    void defaultsProvideExpectedSafeRuntimeValues() {
        DataRegistrySettings settings = DataRegistrySettings.defaults();

        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("player_data_rw", settings.playerDatabaseConnectionId());
        assertEquals("player_data_rw", settings.serviceDatabaseConnectionId());
        assertEquals("validate", settings.ormSchemaMode());
        assertEquals(4, settings.bukkitJoinDelayTicks());
        assertEquals("auto", settings.bukkitServiceName());
        assertEquals("auto", settings.velocityServiceName());
        assertEquals(32, settings.usernameMaxLength());
        assertEquals(64, settings.serverNameMaxLength());
        assertEquals(255, settings.virtualHostMaxLength());
        assertEquals(45, settings.ipAddressMaxLength());
        assertEquals(30, settings.serviceHeartbeatIntervalSeconds());
        assertEquals(15, settings.serviceProbeIntervalSeconds());
        assertEquals(1500, settings.serviceProbeTimeoutMillis());
        assertEquals(168, settings.serviceProbeRetentionHours());
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.NAME_HISTORY));
        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SERVICE_REGISTRY));
    }

    @Test
    void builderNormalizesSchemaModeAndConnectionIds() {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .databaseType(DatabaseType.MYSQL)
                .playerDatabaseConnectionId("  main.players_1  ")
                .serviceDatabaseConnectionId("  main.services_1  ")
                .ormSchemaMode("  CREATE-DROP ")
                .persistIpAddress(true)
                .persistVirtualHost(true)
                .bukkitServiceName(" lobby-01 ")
                .velocityServiceName(" proxy-edge ")
                .build();

        assertEquals("main.players_1", settings.playerDatabaseConnectionId());
        assertEquals("main.services_1", settings.serviceDatabaseConnectionId());
        assertEquals("create-drop", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertTrue(settings.persistVirtualHost());
        assertEquals("lobby-01", settings.bukkitServiceName());
        assertEquals("proxy-edge", settings.velocityServiceName());
    }

    @Test
    void builderRejectsInvalidConnectionIds() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DataRegistrySettings.builder()
                        .playerDatabaseConnectionId("invalid value with spaces")
                        .serviceDatabaseConnectionId("services-rw")
                        .build()
        );
        IllegalArgumentException serviceException = assertThrows(
                IllegalArgumentException.class,
                () -> DataRegistrySettings.builder()
                        .playerDatabaseConnectionId("players-rw")
                        .serviceDatabaseConnectionId("invalid value with spaces")
                        .build()
        );

        assertTrue(exception.getMessage().contains("playerDatabaseConnectionId"));
        assertTrue(serviceException.getMessage().contains("serviceDatabaseConnectionId"));
    }

    @Test
    void builderRejectsUnsupportedSchemaMode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DataRegistrySettings.builder()
                        .ormSchemaMode("danger")
                        .build()
        );

        assertTrue(exception.getMessage().contains("Unsupported ormSchemaMode"));
    }

    @Test
    void builderRejectsOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().bukkitJoinDelayTicks(201).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().usernameMaxLength(0).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().usernameMaxLength(33).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serverNameMaxLength(65).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().virtualHostMaxLength(256).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().ipAddressMaxLength(6).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceHeartbeatIntervalSeconds(4).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceHeartbeatIntervalSeconds(301).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeIntervalSeconds(4).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeIntervalSeconds(301).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeTimeoutMillis(199).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeTimeoutMillis(10001).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeRetentionHours(0).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serviceProbeRetentionHours(2161).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().bukkitServiceName(" ").build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().velocityServiceName(" ").build());
    }

    @Test
    void builderAppliesEnabledFeatureSet() {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .enabledFeatures(EnumSet.of(DataRegistryFeature.SESSIONS))
                .build();

        assertTrue(settings.isFeatureEnabled(DataRegistryFeature.SESSIONS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS));
        assertFalse(settings.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO));
    }
}
