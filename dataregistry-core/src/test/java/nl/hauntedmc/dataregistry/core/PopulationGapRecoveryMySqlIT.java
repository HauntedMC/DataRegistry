package nl.hauntedmc.dataregistry.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopulationGapRecoveryMySqlIT {

    private static final String CONNECTION_ID = "player_data_rw";
    private static final PlaytimeTrackingSettings MAPPING = PlaytimeTrackingSettings.builder()
            .serverGamemodeRules(List.of(
                    new PlaytimeTrackingSettings.ServerGamemodeRule("survival-*", "survival")
            ))
            .build();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("population_gap")
            .withUsername("registry")
            .withPassword("registry-password");

    private HikariDataSource dataSource;

    @BeforeAll
    void startDatabase() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(4);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("PopulationGapIntegration");
        dataSource = new HikariDataSource(configuration);
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("SET FOREIGN_KEY_CHECKS = 0");
            try {
                List<String> tableNames = new ArrayList<>();
                try (ResultSet tables = statement.executeQuery("SHOW TABLES")) {
                    while (tables.next()) {
                        tableNames.add(tables.getString(1));
                    }
                }
                for (String tableName : tableNames) {
                    statement.execute("DROP TABLE `" + tableName.replace("`", "``") + "`");
                }
            } finally {
                statement.execute("SET FOREIGN_KEY_CHECKS = 1");
            }
        }
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void reenableAfterUntrackedGapDowngradesAllExistingGamemodeBaselines() throws Exception {
        UUID originalUuid = UUID.fromString("60000000-0000-0000-0000-000000000001");
        UUID gapUuid = UUID.fromString("60000000-0000-0000-0000-000000000002");

        DataRegistry original = newRegistry(DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .playtimeTrackingSettings(MAPPING)
                .build());
        try {
            assertTrue(original.initialize());
            writeJoin(original.newPlayerLifecycleWriter(mock(ILoggerAdapter.class)), originalUuid, "Original", "survival-1");
            var survival = original.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(PopulationBaselineQuality.VERIFIED, survival.membershipBaselineQuality());
            assertEquals(PopulationBaselineQuality.VERIFIED, survival.peakBaselineQuality());
        } finally {
            original.shutdown();
        }

        DataRegistry duringGap = newRegistry(DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .playtimeTrackingSettings(MAPPING)
                .disableFeature(DataRegistryFeature.POPULATION)
                .disableFeature(DataRegistryFeature.PLAYTIME)
                .build());
        try {
            assertTrue(duringGap.initialize());
            writeJoin(duringGap.newPlayerLifecycleWriter(mock(ILoggerAdapter.class)), gapUuid, "GapPlayer", "survival-1");
        } finally {
            duringGap.shutdown();
        }

        DataRegistry recovered = newRegistry(DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .playtimeTrackingSettings(MAPPING)
                .disableFeature(DataRegistryFeature.PLAYTIME)
                .build());
        try {
            assertTrue(recovered.initialize());
            var network = recovered.population().findNetworkSnapshot().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).orElseThrow();
            var survival = recovered.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();

            assertEquals(2L, network.uniquePlayerCount());
            assertEquals(PopulationBaselineQuality.TRACKED_ONLY, network.membershipBaselineQuality());
            assertEquals(PopulationBaselineQuality.TRACKED_ONLY, network.peakBaselineQuality());
            assertEquals(PopulationBaselineQuality.TRACKED_ONLY, survival.membershipBaselineQuality());
            assertEquals(PopulationBaselineQuality.TRACKED_ONLY, survival.peakBaselineQuality());
            assertEquals(
                    PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC,
                    recovered.population().findMembership(PlayerLookup.uuid(gapUuid), PopulationScope.network())
                            .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().ordinalQuality()
            );
        } finally {
            recovered.shutdown();
        }
    }

    @Test
    void rawBackendNameUsesCanonicalPersistedRepresentationEverywhere() throws Exception {
        String rawServerName = "SURVIVAL-extra";
        PlaytimeTrackingSettings mapping = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .serverGamemodeRules(List.of(
                        new PlaytimeTrackingSettings.ServerGamemodeRule("survival", "survival")
                ))
                .build();
        DataRegistry registry = newRegistry(DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .serverNameMaxLength(8)
                .playtimeTrackingSettings(mapping)
                .build());
        UUID uuid = UUID.fromString("60000000-0000-0000-0000-000000000003");
        try {
            assertTrue(registry.initialize());
            writeJoin(registry.newPlayerLifecycleWriter(mock(ILoggerAdapter.class)), uuid, "Canonical", rawServerName);

            var resolved = registry.population().resolveGamemode(rawServerName)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals("survival", resolved.serverName());
            assertEquals("survival", resolved.gamemodeKey());
            assertTrue(resolved.tracked());

            var survival = registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(1L, survival.currentOnline());

            var context = registry.population().findJoinContext(uuid, rawServerName)
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals("survival", context.serverName());
            assertTrue(context.networkFirstJoinThisSession());
            assertTrue(context.gamemodeFirstJoinThisVisit());
        } finally {
            registry.shutdown();
        }
    }

    private static void writeJoin(PlayerLifecycleWriter writer, UUID uuid, String username, String serverName) {
        assertTrue(writer.login(LoginCommand.create(uuid.toString(), username, null, null)).succeeded());
        assertTrue(writer.transfer(TransferCommand.create(uuid.toString(), username, serverName)).succeeded());
    }

    private DataRegistry newRegistry(DataRegistrySettings settings) {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("update"), any(Class[].class)
        )).thenAnswer(invocation -> createOrmContext(invocation.getArguments()));
        return new DataRegistry(mock(ILoggerAdapter.class), "DataRegistry", dataProvider, settings);
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
