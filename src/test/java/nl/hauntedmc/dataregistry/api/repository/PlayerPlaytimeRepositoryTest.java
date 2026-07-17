package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeLeaderboardEntry;
import nl.hauntedmc.dataregistry.api.playtime.PlayerPlaytimeSnapshot;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerPlaytimeRepositoryTest {

    @Test
    void findSnapshotByPlayerIdIncludesLiveSegmentAndNetworkExclusions() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> aggregateQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> segmentQuery = mock(Query.class);
        PlayerPlaytimeRepository repository = new PlayerPlaytimeRepository(ormContext, Set.of("lobby"));
        PlayerEntity player = player(10L, "Alice");
        PlayerPlaytimeEntity lobbyAggregate = aggregate(player, "lobby", 5_000L);
        PlayerPlaytimeEntity skyblockAggregate = aggregate(player, "skyblock", 8_000L);
        PlayerSessionEntity openSession = openSession(player);
        PlayerPlaytimeSegmentEntity openSegment = openSegment(player, openSession, "lobby");
        Instant asOf = openSegment.getLastAccruedAt().plusSeconds(5);

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerEntity.class, 10L)).thenReturn(player);
        when(session.createQuery(anyString(), eq(PlayerPlaytimeEntity.class))).thenReturn(aggregateQuery);
        when(aggregateQuery.setParameter(eq("playerId"), eq(10L))).thenReturn(aggregateQuery);
        when(aggregateQuery.list()).thenReturn(List.of(lobbyAggregate, skyblockAggregate));
        when(session.createQuery(anyString(), eq(PlayerPlaytimeSegmentEntity.class))).thenReturn(segmentQuery);
        when(segmentQuery.setParameter(eq("playerId"), eq(10L))).thenReturn(segmentQuery);
        when(segmentQuery.setMaxResults(1)).thenReturn(segmentQuery);
        when(segmentQuery.uniqueResultOptional()).thenReturn(Optional.of(openSegment));

        Optional<PlayerPlaytimeSnapshot> snapshot = repository.findSnapshotByPlayerId(10L, asOf);

        assertTrue(snapshot.isPresent());
        assertEquals(18_000L, snapshot.get().trackedTotalMillis());
        assertEquals(8_000L, snapshot.get().networkTotalMillis());
        assertEquals(2, snapshot.get().gamemodes().size());
        assertTrue(snapshot.get().gamemodes().stream().anyMatch(entry -> entry.active()));
    }

    @Test
    void findTopPlayersByNetworkTotalBuildsRankedEntries() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeEntity> aggregateQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> segmentQuery = mock(Query.class);
        PlayerPlaytimeRepository repository = new PlayerPlaytimeRepository(ormContext);
        PlayerEntity alice = player(10L, "Alice");
        PlayerEntity bob = player(11L, "Bob");
        PlayerPlaytimeEntity aliceLobby = aggregate(alice, "lobby", 12_000L);
        PlayerPlaytimeEntity aliceSkyblock = aggregate(alice, "skyblock", 3_000L);
        PlayerPlaytimeEntity bobSkyblock = aggregate(bob, "skyblock", 7_000L);
        PlayerSessionEntity bobSession = openSession(bob);
        PlayerPlaytimeSegmentEntity bobOpenSegment = openSegment(bob, bobSession, "skyblock");
        bobOpenSegment.setLastAccruedAt(bobOpenSegment.getLastAccruedAt().minusSeconds(2));

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(anyString(), eq(PlayerPlaytimeEntity.class))).thenReturn(aggregateQuery);
        when(aggregateQuery.list()).thenReturn(List.of(aliceLobby, aliceSkyblock, bobSkyblock));
        when(session.createQuery(anyString(), eq(PlayerPlaytimeSegmentEntity.class))).thenReturn(segmentQuery);
        when(segmentQuery.list()).thenReturn(List.of(bobOpenSegment));

        List<PlayerPlaytimeLeaderboardEntry> leaderboard = repository.findTopPlayersByNetworkTotal(
                2,
                Set.of("lobby")
        );

        assertEquals(2, leaderboard.size());
        assertEquals(1L, leaderboard.get(0).rank());
        assertEquals("Bob", leaderboard.get(0).username());
        assertEquals(2L, leaderboard.get(1).rank());
        assertEquals("Alice", leaderboard.get(1).username());
    }

    @Test
    void findSnapshotByPlayerUuidReturnsEmptyForInvalidUuid() {
        PlayerPlaytimeRepository repository = new PlayerPlaytimeRepository(mock(ORMContext.class));

        assertFalse(repository.findSnapshotByPlayerUuid("not-a-uuid").isPresent());
    }

    private static PlayerEntity player(Long id, String username) {
        PlayerEntity player = new PlayerEntity();
        player.setId(id);
        player.setUuid("5636f31b-ca53-424d-a5d1-aa98a4b02e71");
        player.setUsername(username);
        return player;
    }

    private static PlayerPlaytimeEntity aggregate(PlayerEntity player, String gamemodeKey, long trackedMillis) {
        PlayerPlaytimeEntity entity = new PlayerPlaytimeEntity();
        entity.setId(1L);
        entity.setPlayer(player);
        entity.setGamemodeKey(gamemodeKey);
        entity.setTrackedMillis(trackedMillis);
        entity.setSegmentCount(1L);
        entity.setFirstTrackedAt(Instant.now().minusSeconds(200));
        entity.setLastTrackedAt(Instant.now().minusSeconds(10));
        return entity;
    }

    private static PlayerSessionEntity openSession(PlayerEntity player) {
        PlayerSessionEntity session = new PlayerSessionEntity();
        session.setId(5L);
        session.setPlayer(player);
        session.setStartedAt(Instant.now().minusSeconds(60));
        return session;
    }

    private static PlayerPlaytimeSegmentEntity openSegment(
            PlayerEntity player,
            PlayerSessionEntity session,
            String gamemodeKey
    ) {
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        segment.setId(8L);
        segment.setPlayer(player);
        segment.setSession(session);
        segment.setGamemodeKey(gamemodeKey);
        segment.setEntryServer(gamemodeKey + "-1");
        segment.setLastServer(gamemodeKey + "-2");
        segment.setStartedAt(Instant.now().minusSeconds(30));
        segment.setLastAccruedAt(Instant.now().minusSeconds(5));
        return segment;
    }
}
