package nl.hauntedmc.dataregistry.core;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import nl.hauntedmc.dataprovider.api.DataProviderAPI;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataprovider.database.DatabaseType;
import nl.hauntedmc.dataprovider.database.relational.RelationalDatabaseProvider;
import nl.hauntedmc.dataprovider.logging.LoggerAdapter;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.config.DataRegistrySettings;
import nl.hauntedmc.dataregistry.core.lifecycle.DisconnectCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionVisitEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Verifies DataProvider ORM bootstrap and the public DataRegistry API against MySQL. */
@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DataRegistryMySqlIT {

    private static final String CONNECTION_ID = "player_data_rw";
    private static final String PLAYER_NAME = "AcceptancePlayer";
    private static final UUID PLAYER_UUID = UUID.fromString("8a1c5035-c774-405e-ae4a-0948f0595d12");
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
    void startDatabase() throws Exception {
        HikariConfig configuration = new HikariConfig();
        configuration.setJdbcUrl(MYSQL.getJdbcUrl());
        configuration.setUsername(MYSQL.getUsername());
        configuration.setPassword(MYSQL.getPassword());
        configuration.setMaximumPoolSize(3);
        configuration.setMinimumIdle(0);
        configuration.setPoolName("DataRegistryIntegration");
        dataSource = new HikariDataSource(configuration);
    }

    @AfterAll
    void stopDatabase() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void ormSchemaBootsAndPersistsLifecycleAndPublicPreferenceOperations() throws Exception {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("update"), any(Class[].class)
        ))
                .thenAnswer(invocation -> createOrmContext(invocation.getArguments()));

        DataRegistry registry = new DataRegistry(platformLogger, "DataRegistry", dataProvider, MYSQL_SETTINGS);
        try {
            assertTrue(registry.initialize(), "Hibernate must initialize the schema successfully.");

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
            assertEquals(loginTime, registry.players().findConnection(identity.playerId())
                    .toCompletableFuture().get(10, TimeUnit.SECONDS).orElseThrow().firstConnectionAt());
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

    @Test
    void leaderboardQueriesAggregateAndLimitInMySql() throws Exception {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("update"), any(Class[].class)
        ))
                .thenAnswer(invocation -> createOrmContext(invocation.getArguments()));

        DataRegistry registry = new DataRegistry(platformLogger, "DataRegistry", dataProvider, MYSQL_SETTINGS);
        try {
            assertTrue(registry.initialize());
            registry.getORM().runInTransaction(session -> {
                PlayerEntity alice = new PlayerEntity();
                alice.setUuid("10000000-0000-0000-0000-000000000001");
                alice.setUsername("LeaderboardAlice");
                session.persist(alice);
                PlayerEntity bob = new PlayerEntity();
                bob.setUuid("10000000-0000-0000-0000-000000000002");
                bob.setUsername("LeaderboardBob");
                session.persist(bob);

                PlayerPlaytimeEntity alicePlaytime = new PlayerPlaytimeEntity();
                alicePlaytime.setPlayer(alice);
                alicePlaytime.setGamemodeKey("skyblock");
                alicePlaytime.setTrackedMillis(3_000L);
                alicePlaytime.setSegmentCount(1L);
                alicePlaytime.setFirstTrackedAt(Instant.now().minusSeconds(60));
                alicePlaytime.setLastTrackedAt(Instant.now().minusSeconds(30));
                session.persist(alicePlaytime);

                PlayerPlaytimeEntity bobPlaytime = new PlayerPlaytimeEntity();
                bobPlaytime.setPlayer(bob);
                bobPlaytime.setGamemodeKey("skyblock");
                bobPlaytime.setTrackedMillis(7_000L);
                bobPlaytime.setSegmentCount(1L);
                bobPlaytime.setFirstTrackedAt(Instant.now().minusSeconds(60));
                bobPlaytime.setLastTrackedAt(Instant.now().minusSeconds(30));
                session.persist(bobPlaytime);

                PlayerSessionEntity bobSession = new PlayerSessionEntity();
                bobSession.setPlayer(bob);
                bobSession.setStartedAt(Instant.now().minusSeconds(30));
                session.persist(bobSession);
                PlayerPlaytimeSegmentEntity bobSegment = new PlayerPlaytimeSegmentEntity();
                bobSegment.setPlayer(bob);
                bobSegment.setSession(bobSession);
                bobSegment.setGamemodeKey("skyblock");
                bobSegment.setEntryServer("skyblock-1");
                bobSegment.setLastServer("skyblock-1");
                bobSegment.setStartedAt(Instant.now().minusSeconds(15));
                bobSegment.setLastAccruedAt(Instant.now().minusSeconds(2));
                session.persist(bobSegment);
                return null;
            });

            List<nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry> leaderboard = registry.players()
                    .findTopPlaytimeByGamemode("skyblock", 1)
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            assertEquals(1, leaderboard.size());
            assertEquals("LeaderboardBob", leaderboard.getFirst().username());
            assertTrue(leaderboard.getFirst().trackedMillis() >= 7_000L);
        } finally {
            registry.shutdown();
        }
    }

    @Test
    void closedSessionHistoryRetentionDeletesOnlyFullyClosedSessionChainsInMySql() {
        DataProviderAPI dataProvider = mock(DataProviderAPI.class);
        RelationalDatabaseProvider provider = mock(RelationalDatabaseProvider.class);
        ILoggerAdapter platformLogger = mock(ILoggerAdapter.class);
        when(dataProvider.registerDatabaseOrThrow(DatabaseType.MYSQL, CONNECTION_ID)).thenReturn(provider);
        when(provider.isConnected()).thenReturn(true);
        when(provider.getDataSource()).thenReturn(dataSource);
        when(dataProvider.createOrmContext(
                eq(dataSource), any(LoggerAdapter.class), eq("update"), any(Class[].class)
        ))
                .thenAnswer(invocation -> createOrmContext(invocation.getArguments()));

        Instant closedAt = Instant.parse("2020-01-01T00:00:00Z");
        PlayerEntity player = new PlayerEntity();
        player.setUuid("20000000-0000-0000-0000-000000000001");
        player.setUsername("RetentionPlayer");
        PlayerSessionEntity removableSession = session(player, closedAt, closedAt.plusSeconds(60));
        PlayerSessionVisitEntity removableVisit = visit(player, removableSession, closedAt, closedAt.plusSeconds(30));
        PlayerPlaytimeSegmentEntity removableSegment = segment(
                player,
                removableSession,
                closedAt,
                closedAt.plusSeconds(30)
        );
        PlayerSessionEntity protectedSession = session(player, closedAt, closedAt.plusSeconds(60));
        PlayerPlaytimeSegmentEntity openSegment = segment(player, protectedSession, closedAt, null);

        DataRegistry registry = new DataRegistry(platformLogger, "DataRegistry", dataProvider, MYSQL_SETTINGS);
        try {
            assertTrue(registry.initialize());
            registry.getORM().runInTransaction(ormSession -> {
                ormSession.persist(player);
                ormSession.persist(removableSession);
                ormSession.persist(removableVisit);
                ormSession.persist(removableSegment);
                ormSession.persist(protectedSession);
                ormSession.persist(openSegment);
                return null;
            });

            assertEquals(1, registry.purgeClosedSessionHistoryOlderThan(Duration.ofDays(30), 100));
            registry.getORM().runInTransaction(ormSession -> {
                assertNull(ormSession.find(PlayerSessionEntity.class, removableSession.getId()));
                assertNull(ormSession.find(PlayerSessionVisitEntity.class, removableVisit.getId()));
                assertNull(ormSession.find(PlayerPlaytimeSegmentEntity.class, removableSegment.getId()));
                assertTrue(ormSession.find(PlayerSessionEntity.class, protectedSession.getId()) != null);
                assertTrue(ormSession.find(PlayerPlaytimeSegmentEntity.class, openSegment.getId()) != null);
                return null;
            });
        } finally {
            registry.shutdown();
        }
    }

    private static PlayerSessionEntity session(PlayerEntity player, Instant startedAt, Instant endedAt) {
        PlayerSessionEntity session = new PlayerSessionEntity();
        session.setPlayer(player);
        session.setStartedAt(startedAt);
        session.setEndedAt(endedAt);
        return session;
    }

    private static PlayerSessionVisitEntity visit(
            PlayerEntity player,
            PlayerSessionEntity session,
            Instant enteredAt,
            Instant leftAt
    ) {
        PlayerSessionVisitEntity visit = new PlayerSessionVisitEntity();
        visit.setPlayer(player);
        visit.setSession(session);
        visit.setServerName("retention-server");
        visit.setEnteredAt(enteredAt);
        visit.setLeftAt(leftAt);
        return visit;
    }

    private static PlayerPlaytimeSegmentEntity segment(
            PlayerEntity player,
            PlayerSessionEntity session,
            Instant startedAt,
            Instant endedAt
    ) {
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        segment.setPlayer(player);
        segment.setSession(session);
        segment.setGamemodeKey("retention");
        segment.setEntryServer("retention-server");
        segment.setLastServer("retention-server");
        segment.setStartedAt(startedAt);
        segment.setLastAccruedAt(startedAt);
        segment.setEndedAt(endedAt);
        return segment;
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
