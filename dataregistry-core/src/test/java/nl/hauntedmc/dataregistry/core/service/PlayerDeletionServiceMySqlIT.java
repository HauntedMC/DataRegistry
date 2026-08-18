package nl.hauntedmc.dataregistry.core.service;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.lifecycle.DisconnectCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlayerDeletionServiceMySqlIT {

    private static final String CONNECTION_ID = "player_data_rw";
    private static final UUID PLAYER_UUID = UUID.fromString("4df840d8-6528-4d90-9fa2-f25f849e6913");
    private static final String PLAYER_NAME = "DeletionDebugPlayer";
    private static final DataRegistrySettings MYSQL_SETTINGS = DataRegistrySettings.builder()
            .ormSchemaMode("update")
            .build();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dataregistry")
            .withUsername("registry")
            .withPassword("registry-password");

    private HikariDataSource dataSource;

    @BeforeAll
    void startDatabase() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(3);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("DataRegistryPlayerDeletionIntegration");
        dataSource = new HikariDataSource(configuration);
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void deletesOwnedAndExternalDependenciesAndNextJoinGetsFreshIdentity() throws Exception {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("update"), any(Class[].class)
        )).thenAnswer(invocation -> createOrmContext(invocation.getArguments()));

        DataRegistry registry = new DataRegistry(platformLogger, "DataRegistry", dataProvider, MYSQL_SETTINGS);
        try {
            assertTrue(registry.initialize());
            PlayerLifecycleWriter writer = registry.newPlayerLifecycleWriter(platformLogger);
            Instant startedAt = Instant.parse("2026-08-18T12:00:00Z");

            assertTrue(writer.login(new LoginCommand(
                    "login:deletion:1",
                    PLAYER_UUID.toString(),
                    PLAYER_NAME,
                    "203.0.113.20",
                    "debug.example.test",
                    startedAt
            )).succeeded());
            PlayerIdentity originalIdentity = registry.players().findIdentity(PLAYER_UUID)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();

            PlayerDeletionService deletionService = new PlayerDeletionService(
                    registry,
                    registry.newPlayerService(platformLogger),
                    platformLogger
            );
            assertThrows(IllegalStateException.class, () -> deletionService.delete(originalIdentity));

            assertTrue(writer.transfer(new TransferCommand(
                    "transfer:deletion:1",
                    PLAYER_UUID.toString(),
                    PLAYER_NAME,
                    "survival-1",
                    startedAt.plusSeconds(10)
            )).succeeded());
            registry.players().saveLanguage(originalIdentity.playerId(), "NL", "nl")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            registry.players().saveNickname(originalIdentity.playerId(), "Deletion Tester")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertTrue(writer.disconnect(new DisconnectCommand(
                    "disconnect:deletion:1",
                    PLAYER_UUID.toString(),
                    PLAYER_NAME,
                    startedAt.plusSeconds(30)
            )).succeeded());
            registry.newPlayerService(platformLogger).onPlayerQuit(PLAYER_NAME, PLAYER_UUID.toString());

            createExternalPlayerReference(originalIdentity.playerId());
            assertEquals(1L, externalReferenceCount(originalIdentity.playerId()));

            PlayerDeletionResult result = deletionService.delete(originalIdentity);

            assertEquals(originalIdentity, result.deletedIdentity());
            assertTrue(result.deletedDependentRows() > 0);
            assertEquals(1, result.deletedRowsByTable().get("debug_external_player_settings"));
            assertFalse(registry.players().findIdentity(PLAYER_UUID)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).isPresent());
            assertEquals(0L, externalReferenceCount(originalIdentity.playerId()));
            assertEquals(0L, tablePlayerReferenceCount("player_lifecycle_outbox", originalIdentity.playerId()));
            assertEquals(0L, tablePlayerReferenceCount("player_sessions", originalIdentity.playerId()));
            assertEquals(0L, tablePlayerReferenceCount("player_session_visits", originalIdentity.playerId()));

            assertTrue(writer.login(new LoginCommand(
                    "login:deletion:2",
                    PLAYER_UUID.toString(),
                    PLAYER_NAME,
                    "203.0.113.20",
                    "debug.example.test",
                    startedAt.plusSeconds(60)
            )).succeeded());
            PlayerIdentity replacementIdentity = registry.players().findIdentity(PLAYER_UUID)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertNotEquals(originalIdentity.playerId(), replacementIdentity.playerId());
            assertEquals(PLAYER_UUID, replacementIdentity.uuid());

            assertTrue(writer.disconnect(new DisconnectCommand(
                    "disconnect:deletion:2",
                    PLAYER_UUID.toString(),
                    PLAYER_NAME,
                    startedAt.plusSeconds(90)
            )).succeeded());
            registry.newPlayerService(platformLogger).onPlayerQuit(PLAYER_NAME, PLAYER_UUID.toString());
        } finally {
            registry.shutdown();
        }
    }

    private void createExternalPlayerReference(long playerId) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS debug_external_player_settings");
            statement.executeUpdate("""
                    CREATE TABLE debug_external_player_settings (
                        player_id BIGINT NOT NULL PRIMARY KEY,
                        enabled BOOLEAN NOT NULL,
                        CONSTRAINT fk_debug_external_player
                            FOREIGN KEY (player_id) REFERENCES player_entity(id)
                    )
                    """);
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO debug_external_player_settings (player_id, enabled) VALUES (?, true)"
             )) {
            statement.setLong(1, playerId);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private long externalReferenceCount(long playerId) throws Exception {
        return tablePlayerReferenceCount("debug_external_player_settings", playerId);
    }

    private long tablePlayerReferenceCount(String table, long playerId) throws Exception {
        if (!table.matches("[a-z0-9_]+")) {
            throw new IllegalArgumentException("Unexpected test table name.");
        }
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT COUNT(*) FROM " + table + " WHERE player_id = ?"
             )) {
            statement.setLong(1, playerId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertTrue(resultSet.next());
                return resultSet.getLong(1);
            }
        }
    }

    private ORMContext createOrmContext(Object[] arguments) {
        Class<?>[] entityClasses = new Class<?>[arguments.length - 3];
        for (int index = 3; index < arguments.length; index++) {
            entityClasses[index - 3] = (Class<?>) arguments[index];
        }
        return new nl.hauntedmc.dataprovider.core.orm.ORMContext(
                "DataRegistry", dataSource, (LoggerAdapter) arguments[1], (String) arguments[2], entityClasses
        );
    }
}
