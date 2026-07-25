package nl.hauntedmc.dataregistry.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies the shipped MySQL migration, DataProvider ORM bootstrap, and public DataRegistry API together. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRegistryMySqlIT {

    private static final String CONNECTION_ID = "player_data_rw";
    private static final String PLAYER_NAME = "AcceptancePlayer";
    private static final UUID PLAYER_UUID = UUID.fromString("8a1c5035-c774-405e-ae4a-0948f0595d12");

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("dataregistry")
            .withUsername("registry")
            .withPassword("registry-password");

    private HikariDataSource dataSource;

    @BeforeAll
    void startDatabase() throws Exception {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(3);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("DataRegistryIntegration");
        dataSource = new HikariDataSource(configuration);
        applyBaselineMigration();
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void migratedSchemaBootsAndPersistsLifecycleAndPublicPreferenceOperations() throws Exception {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("validate"), any(Class[].class)
        ))
                .thenAnswer(invocation -> createOrmContext(invocation.getArguments()));

        DataRegistry registry = new DataRegistry(platformLogger, "DataRegistry", dataProvider);
        try {
            assertTrue(registry.initialize(), "The released baseline must satisfy Hibernate validate mode.");

            PlayerLifecycleWriter writer = registry.newPlayerLifecycleWriter(platformLogger);
            Instant loginTime = Instant.parse("2026-07-25T12:00:00Z");
            assertTrue(writer.login(new LoginCommand(
                    "login:integration:1", PLAYER_UUID.toString(), PLAYER_NAME, "203.0.113.10", "example.test", loginTime
            )).succeeded());
            assertTrue(writer.transfer(new TransferCommand(
                    "transfer:integration:1", PLAYER_UUID.toString(), PLAYER_NAME, "survival", loginTime.plusSeconds(30)
            )).succeeded());
            assertTrue(writer.disconnect(new DisconnectCommand(
                    "disconnect:integration:1", PLAYER_UUID.toString(), PLAYER_NAME, loginTime.plusSeconds(60)
            )).succeeded());

            PlayerIdentity identity = registry.players().findIdentity(PLAYER_UUID)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(PLAYER_UUID, identity.uuid());
            assertEquals(PLAYER_NAME, identity.username());
            registry.players().saveLanguage(identity.playerId(), "NL", "nl")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            registry.players().saveNickname(identity.playerId(), "Registry Tester")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals("NL", registry.players().findLanguage(identity.playerId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().language());
            assertEquals("Registry Tester", registry.players().findNickname(identity.playerId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow());
            assertFalse(registry.players().findOnlineStatus(identity.playerId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().online());
        } finally {
            registry.shutdown();
        }
        assertFalse(registry.isReady());
    }

    private void applyBaselineMigration() throws Exception {
        String migration = readMigration();
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            for (String command : migration.split(";\\s*(?:\\R|$)")) {
                String sql = command.strip();
                if (!sql.isEmpty() && !sql.startsWith("--")) {
                    statement.execute(sql);
                }
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

    private static String readMigration() throws IOException {
        try (InputStream stream = DataRegistryMySqlIT.class.getClassLoader()
                .getResourceAsStream("db/migration/V1__baseline.sql")) {
            if (stream == null) {
                throw new IOException("The DataRegistry V1 migration resource is missing.");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8)
                    .replaceFirst("(?s)^--[^\\r\\n]*(?:\\r?\\n)", "");
        }
    }
}
