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
import nl.hauntedmc.dataregistry.api.population.PopulationTransitionType;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.lifecycle.DisconnectCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteStatus;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PopulationTransitionEntity;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
            assertEquals(
                    PlayerLifecycleWriteStatus.DUPLICATE,
                    writer.transfer(new TransferCommand(
                            "transfer:population:1c",
                            firstUuid.toString(),
                            "PopulationOne",
                            "creative-1",
                            base.plusSeconds(20)
                    )).status()
            );
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
    void existingCanonicalRowsBackfillDeterministicallyEvenWhenPlaytimeRuntimeIsDisabled() throws Exception {
        DataRegistrySettings prePopulationSettings = DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .disableFeature(DataRegistryFeature.POPULATION)
                .build();
        DataRegistry prePopulationRegistry = newRegistry(prePopulationSettings);
        Instant older = Instant.parse("2024-01-01T00:00:00Z");
        Instant newer = Instant.parse("2025-01-01T00:00:00Z");
        UUID olderUuid = UUID.fromString("40000000-0000-0000-0000-000000000001");
        UUID newerUuid = UUID.fromString("40000000-0000-0000-0000-000000000002");
        try {
            assertTrue(prePopulationRegistry.initialize());
            prePopulationRegistry.getORM().runInTransaction(session -> {
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
            prePopulationRegistry.shutdown();
        }

        DataRegistrySettings migratedSettings = DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .disableFeature(DataRegistryFeature.PLAYTIME)
                .build();
        DataRegistry migrated = newRegistry(migratedSettings);
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
            assertEquals(0L, migrated.population().latestTransitionId().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS));
        } finally {
            migrated.shutdown();
        }
    }

    @Test
    void policyReconciliationMovesOnlinePopulationAndCreatesMissingMembership() throws Exception {
        PlaytimeTrackingSettings initialPolicy = PlaytimeTrackingSettings.builder()
                .resolveUnknownServersAsGamemode(false)
                .build();
        DataRegistry registry = newRegistry(DataRegistrySettings.builder()
                .ormSchemaMode("update")
                .playtimeTrackingSettings(initialPolicy)
                .build());
        UUID uuid = UUID.fromString("50000000-0000-0000-0000-000000000001");
        Instant base = Instant.parse("2026-08-17T18:00:00Z");
        try {
            assertTrue(registry.initialize());
            PlayerLifecycleWriter writer = registry.newPlayerLifecycleWriter(mock(ILoggerAdapter.class));
            writeJoin(writer, uuid, "PolicyPlayer", "5", "lobby-1", base);
            assertTrue(registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).isEmpty());

            PlaytimeTrackingSettings replacement = PlaytimeTrackingSettings.builder()
                    .resolveUnknownServersAsGamemode(false)
                    .serverGamemodeRules(List.of(
                            new PlaytimeTrackingSettings.ServerGamemodeRule("lobby-*", "survival")
                    ))
                    .build();
            registry.reconcilePlaytimePolicy(replacement);

            var survival = registry.population().findSnapshot(PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(1L, survival.uniquePlayerCount());
            assertEquals(1L, survival.currentOnline());
            PlayerPopulationMembership membership = registry.population()
                    .findMembership(PlayerLookup.uuid(uuid), PopulationScope.gamemode("survival"))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow();
            assertEquals(PopulationOrdinalQuality.RECORDED_EXACT, membership.ordinalQuality());

            var reconciliationTransitions = registry.population().findTransitions(
                            PopulationTransitionQuery.after(0L, 1000)
                                    .withCauses(Set.of(PopulationTransitionCause.RECONCILIATION))
                    )
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).transitions();
            assertTrue(reconciliationTransitions.stream().anyMatch(transition ->
                    transition.type() == PopulationTransitionType.MEMBERSHIP_ADDED
                            && transition.scope().equals(PopulationScope.gamemode("survival"))));
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void transitionRetentionKeepsContiguousSuffixAndNewestHighWaterAnchor() throws Exception {
        DataRegistry registry = newRegistry(DataRegistrySettings.builder().ormSchemaMode("update").build());
        try {
            assertTrue(registry.initialize());
            Instant now = Instant.now();
            registry.getORM().runInTransaction(session -> {
                persistTransition(session, now.minus(Duration.ofDays(200)));
                persistTransition(session, now.minus(Duration.ofDays(1)));
                persistTransition(session, now.minus(Duration.ofDays(200)));
                return null;
            });

            long highWater = registry.population().latestTransitionId().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            assertTrue(highWater > 0L);
            assertEquals(1, registry.purgePopulationTransitionsOlderThan(Duration.ofDays(90), 100));

            var afterFirstPurge = registry.population().findTransitions(PopulationTransitionQuery.after(0L, 100))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(2, afterFirstPurge.transitions().size());
            assertEquals(highWater, afterFirstPurge.latestAvailableId());
            assertEquals(highWater, registry.population().latestTransitionId().toCompletableFuture()
                    .get(10, TimeUnit.SECONDS));

            assertEquals(1, registry.purgePopulationTransitionsOlderThan(Duration.ZERO, 100));
            var anchored = registry.population().findTransitions(PopulationTransitionQuery.after(0L, 100))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertEquals(1, anchored.transitions().size());
            assertEquals(highWater, anchored.earliestAvailableId());
            assertEquals(highWater, anchored.latestAvailableId());
            assertEquals(0, registry.purgePopulationTransitionsOlderThan(Duration.ZERO, 100));
        } finally {
            registry.shutdown();
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

    private static void persistTransition(org.hibernate.Session session, Instant occurredAt) {
        PopulationScope scope = PopulationScope.network();
        PopulationTransitionEntity transition = new PopulationTransitionEntity();
        transition.setTransitionType(PopulationTransitionType.ONLINE_CHANGED);
        transition.setTransitionCause(PopulationTransitionCause.LIVE);
        transition.setScopeId(scope.storageKey());
        transition.setScopeType(scope.type());
        transition.setScopeKey(scope.key());
        transition.setPreviousValue(0L);
        transition.setCurrentValue(1L);
        transition.setOccurredAt(occurredAt);
        transition.setCreatedAt(Instant.now());
        session.persist(transition);
    }
}
