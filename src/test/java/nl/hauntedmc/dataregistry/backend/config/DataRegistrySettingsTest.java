package nl.hauntedmc.dataregistry.backend.config;

import nl.hauntedmc.dataprovider.database.DatabaseType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistrySettingsTest {

    @Test
    void defaultsProvideExpectedSafeRuntimeValues() {
        DataRegistrySettings settings = DataRegistrySettings.defaults();

        assertEquals(DatabaseType.MYSQL, settings.databaseType());
        assertEquals("player_data_rw", settings.databaseConnectionId());
        assertEquals("validate", settings.ormSchemaMode());
        assertEquals(4, settings.bukkitJoinDelayTicks());
        assertEquals(32, settings.usernameMaxLength());
        assertEquals(64, settings.serverNameMaxLength());
        assertEquals(255, settings.virtualHostMaxLength());
        assertEquals(45, settings.ipAddressMaxLength());
    }

    @Test
    void builderNormalizesSchemaModeAndConnectionId() {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .databaseType(DatabaseType.MYSQL)
                .databaseConnectionId("  main.players_1  ")
                .ormSchemaMode("  CREATE-DROP ")
                .persistIpAddress(true)
                .persistVirtualHost(true)
                .build();

        assertEquals("main.players_1", settings.databaseConnectionId());
        assertEquals("create-drop", settings.ormSchemaMode());
        assertTrue(settings.persistIpAddress());
        assertTrue(settings.persistVirtualHost());
    }

    @Test
    void builderRejectsInvalidConnectionId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> DataRegistrySettings.builder()
                        .databaseConnectionId("invalid value with spaces")
                        .build()
        );

        assertTrue(exception.getMessage().contains("databaseConnectionId"));
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
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().serverNameMaxLength(129).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().virtualHostMaxLength(256).build());
        assertThrows(IllegalArgumentException.class, () -> DataRegistrySettings.builder().ipAddressMaxLength(6).build());
    }
}
