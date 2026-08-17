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
import nl.hauntedmc.dataregistry.api.population.PlayerPopulationMembership;
import nl.hauntedmc.dataregistry.api.population.PopulationBaselineQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationJoinContext;
import nl.hauntedmc.dataregistry.api.population.PopulationOrdinalQuality;
import nl.hauntedmc.dataregistry.api.population.PopulationScope;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionCause;
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionQuery;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.lifecycle.DisconnectCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Exercises population allocation, movement, migration and read contracts against real MySQL locking. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PopulationMySqlIT {

    private static final String CONNECTION_ID = "player_data_rw";

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("population")
            .withUsername("registry")
            .withPassword("registry-password");

    private HikariDataSource dataSource;

    @BeforeAll
    void startDatabase() {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(8);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("PopulationIntegration");
        dataSource = new HikariDataSource(configuration);
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void concurrentLifecycleAllocatesStableExactOrdinalsAndTracksLogicalGamemodes() throws Exception {
        DataRegistrySettings settings = DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .playtimeTrackingSettings(PlaytimeTrackingSettings.builder()
                        .serverGamemodeRules(List.of(
                                new PlaytimeTrackingSettings.ServerGamemodeRule("survival-*", "survival"),
                                new PlaytimeTrackingSettings.ServerGamemodeRule("creative-*", "creative")
                        ))
                        .build())
                .build();
        DataRegistry registry = newRegistry(settings);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        try {
            assertTrue(registry.initialize());
            assertEquals(
                    PopulationBaselineQuality.VERIFIED,
                    registry.population().findNetworkSnapshot().toCompletableFuture().get(10, TimeUnit.SECONDS)
                            .orElseThrow().membershipBaselineQuality()
            );

            PlayerLifecycleWriter writer = registry.newPlayerLifecycleWriter(logger);
            UUID firstUuid = UUID.fromString("30000000-0000-0000-0000-000000000001");
            UUID secondUuid = UUID.fromString("30000000-0000-0000-0000-000000000002");
            Instant base = Instant.parse("2026-08-17T12:00:00Z");
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                CompletableFuture<Void> first = CompletableFuture.runAsync(
                        () -> writeJoin(writer, firstUuid, "PopulationOne", "1", "survival-1", base),
                        executor
                );
                CompletableFuture<Void> second = CompletableFuture.runAsync(
                        () -> writeJoin(writer, secondUuid, "PopulationTwo", "2", "survival-2", base.plusMillis(1)),
                        executor
                );
                CompletableFuture.allOf(first, second).get(20, TimeUnit.SECONDS);
            }

            PlayerPopulationMembership firstNetwork = registry.population()
                    .findMembership(PlayerLookup.uuid(firstUuid), PopulationScope.network())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            PlayerPopulationMembership secondNetwork = registry.population()
                    .findMembership(PlayerLookup.uuid(secondUuid), PopulationScope.network())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertNotEquals(firstNetwork.ordinal(), secondNetwork.ordinal());
            assertEquals(Set.of(1L, 2L), Set.of(firstNetwork.ordinal(), secondNetwork.ordinal()));
            assertEquals(PopulationOrdinalQuality.RECORDED_EXACT, firstNetwork.ordinalQuality());
            assertEquals(PopulationOrdinalQuality.RECORDED_EXACT, secondNetwork.ordinalQuality());

            var network = registry.population().findNetworkSnapshot().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).orElseThrow();
            var survival = registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(2L, network.uniquePlayerCount());
            assertEquals(2L, network.currentOnline());
            assertEquals(2L, network.onlinePeak());
            assertEquals(2L, survival.uniquePlayerCount());
            assertEquals(2L, survival.currentOnline());
            assertEquals(2L, survival.onlinePeak());

            PopulationJoinContext joinContext = registry.population().findJoinContext(firstUuid, "survival-1")
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertTrue(joinContext.networkFirstJoinThisSession());
            assertTrue(joinContext.gamemodeFirstJoinThisVisit());
            assertEquals("survival", joinContext.gamemodeKey().orElseThrow());

            assertTrue(writer.transfer(new TransferCommand(
                    "transfer:population:1b", firstUuid.toString(), "PopulationOne", "survival-2", base.plusSeconds(10)
            )).succeeded());
            assertEquals(2L, registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().currentOnline());

            assertTrue(writer.transfer(new TransferCommand(
                    "transfer:population:1c", firstUuid.toString(), "PopulationOne", "creative-1", base.plusSeconds(20)
            )).succeeded());
            assertEquals(1L, registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().currentOnline());
            assertEquals(1L, registry.population().findSnapshot(PopulationScope.gamemode("creative"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().currentOnline());

            long beforeDuplicate = registry.population().findSnapshot(PopulationScope.gamemode("creative"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().uniquePlayerCount();
            assertTrue(writer.transfer(new TransferCommand(
                    "transfer:population:1c", firstUuid.toString(), "PopulationOne", "creative-1", base.plusSeconds(20)
            )).duplicate());
            assertEquals(beforeDuplicate, registry.population().findSnapshot(PopulationScope.gamemode("creative"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().uniquePlayerCount());

            assertTrue(writer.disconnect(new DisconnectCommand(
                    "disconnect:population:1", firstUuid.toString(), "PopulationOne", base.plusSeconds(30)
            )).succeeded());
            assertEquals(1L, registry.population().findNetworkSnapshot().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).orElseThrow().currentOnline());
            assertEquals(0L, registry.population().findSnapshot(PopulationScope.gamemode("creative"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().currentOnline());

            var transitions = registry.population().findTransitions(PopulationTransitionQuery.after(0L, 1000))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertFalse(transitions.transitions().isEmpty());
            assertTrue(transitions.transitions().stream()
                    .allMatch(transition -> transition.cause() == PopulationTransitionCause.LIVE));
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void existingCanonicalRowsBackfillDeterministicallyWithoutPretendingHistoricalExactness() throws Exception {
        DataRegistrySettings legacySettings = DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .disableFeature(DataRegistryFeature.POPULATION)
                .build();
        DataRegistry legacy = newRegistry(legacySettings);
        Instant older = Instant.parse("2024-01-01T00:00:00Z");
        Instant newer = Instant.parse("2025-01-01T00:00:00Z");
        UUID olderUuid = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID newerUuid = UUID.fromString("40000000-0000-0000-0000-000000000002");
        try {
            assertTrue(legacy.initialize());
            legacy.getORM().runInTransaction(session -> {
                PlayerEntity newerPlayer = player(newerUuid, "HistoricalNewer");
                PlayerEntity olderPlayer = player(olderUuid, "HistoricalOlder");
                session.persist(newerPlayer);
                session.persist(olderPlayer);
                session.flush();
                activity(session, newerPlayer, newer);
                activity(session, olderPlayer, older);
                playtime(session, newerPlayer, "survival", newer);
                playtime(session, olderPlayer, "survival", older);
                return null;
            });
        } finally {
            legacy.shutdown();
        }

        DataRegistry migrated = newRegistry(DataRegistrySettings.builder().ormSchemaMode("update").build());
        try {
            assertTrue(migrated.initialize());
            var network = migrated.population().findNetworkSnapshot().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(PopulationBaselineQuality.TRACKED_ONLY, network.membershipBaselineQuality());

            PlayerPopulationMembership olderNetwork = migrated.population()
                    .findMembership(PlayerLookup.uuid(olderUuid), PopulationScope.network())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            PlayerPopulationMembership newerNetwork = migrated.population()
                    .findMembership(PlayerLookup.uuid(newerUuid), PopulationScope.network())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertTrue(olderNetwork.ordinal() < newerNetwork.ordinal());
            assertEquals(PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC, olderNetwork.ordinalQuality());
            assertEquals(PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC, newerNetwork.ordinalQuality());

            PlayerPopulationMembership olderSurvival = migrated.population()
                    .findMembership(PlayerLookup.uuid(olderUuid), PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            PlayerPopulationMembership newerSurvival = migrated.population()
                    .findMembership(PlayerLookup.uuid(newerUuid), PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertTrue(olderSurvival.ordinal() < newerSurvival.ordinal());
            assertEquals(PopulationOrdinalQuality.BACKFILLED_DETERMINISTIC, olderSurvival.ordinalQuality());

            assertTrue(migrated.population().findTransitions(PopulationTransitionQuery.after(0L, 100))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).transitions().isEmpty());
        } finally {
            migrated.shutdown();
        }
    }

    private void writeJoin(
            PlayerLifecycleWriter writer,
            UUID uuid,
            String username,
            String suffix,
            String server,
            Instant occurredAt
    ) {
        assertTrue(writer.login(new LoginCommand(
                "login:population:" + suffix,
                uuid.toString(),
                username,
                "203.0.113." + suffix,
                "population.test",
                occurredAt
        )).succeeded());
        assertTrue(writer.transfer(new TransferCommand(
                "transfer:population:" + suffix,
                uuid.toString(),
                username,
                server,
                occurredAt.plusSeconds(1)
        )).succeeded());
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

    private static PlayerEntity player(UUID uuid, String username) {
        PlayerEntity player = new PlayerEntity();
        player.setUuid(uuid.toString());
        player.setUsername(username);
        return player;
    }

    private static void activity(
            org.hibernate.Session session,
            PlayerEntity player,
            Instant firstSeenAt
    ) {
        PlayerActivitySummaryEntity activity = new PlayerActivitySummaryEntity();
        activity.setPlayer(player);
        activity.setFirstSeenAt(firstSeenAt);
        activity.setLastSeenAt(firstSeenAt);
        session.persist(activity);
    }

    private static void playtime(
            org.hibernate.Session session,
            PlayerEntity player,
            String gamemode,
            Instant firstTrackedAt
    ) {
        PlayerPlaytimeEntity playtime = new PlayerPlaytimeEntity();
        playtime.setPlayer(player);
        playtime.setGamemodeKey(gamemode);
        playtime.setTrackedMillis(0L);
        playtime.setSegmentCount(0L);
        playtime.setFirstTrackedAt(firstTrackedAt);
        playtime.setLastTrackedAt(firstTrackedAt);
        playtime.setLifecycleHistoryComplete(false);
        session.persist(playtime);
    }
}
