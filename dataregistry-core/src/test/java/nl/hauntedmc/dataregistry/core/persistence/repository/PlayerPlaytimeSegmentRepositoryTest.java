package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerPlaytimeSegmentRepositoryTest {

    @Test
    void findOpenSegmentForPlayerRestrictsToLiveSessionSegments() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> query = mock(Query.class);
        PlayerPlaytimeSegmentRepository repository = new PlayerPlaytimeSegmentRepository(ormContext);
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                        "AND s.session.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(query);
        when(query.setParameter("playerId", 12L)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(segment));

        Optional<PlayerPlaytimeSegmentEntity> result = repository.findOpenSegmentForPlayer(12L);

        assertEquals(Optional.of(segment), result);
    }

    @Test
    void helperMethodsExposeRecentOpenAndCountQueries() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> recentByPlayerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> recentByGamemodeQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> openQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerPlaytimeSegmentEntity> startedAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countQuery = mock(Query.class);
        PlayerPlaytimeSegmentRepository repository = new PlayerPlaytimeSegmentRepository(ormContext);
        PlayerPlaytimeSegmentEntity segment = new PlayerPlaytimeSegmentEntity();
        Instant threshold = Instant.now().minusSeconds(60);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.player.id = :playerId ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(recentByPlayerQuery);
        when(recentByPlayerQuery.setParameter("playerId", 8L)).thenReturn(recentByPlayerQuery);
        when(recentByPlayerQuery.setMaxResults(1)).thenReturn(recentByPlayerQuery);
        when(recentByPlayerQuery.list()).thenReturn(List.of(segment));

        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.player.id = :playerId AND s.gamemodeKey = :gamemodeKey " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(recentByGamemodeQuery);
        when(recentByGamemodeQuery.setParameter("playerId", 8L)).thenReturn(recentByGamemodeQuery);
        when(recentByGamemodeQuery.setParameter("gamemodeKey", "skyblock")).thenReturn(recentByGamemodeQuery);
        when(recentByGamemodeQuery.setMaxResults(1)).thenReturn(recentByGamemodeQuery);
        when(recentByGamemodeQuery.list()).thenReturn(List.of(segment));

        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.endedAt IS NULL AND s.session.endedAt IS NULL " +
                        "ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(openQuery);
        when(openQuery.setMaxResults(1)).thenReturn(openQuery);
        when(openQuery.list()).thenReturn(List.of(segment));

        when(session.createQuery(
                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC, s.id DESC",
                PlayerPlaytimeSegmentEntity.class
        )).thenReturn(startedAfterQuery);
        when(startedAfterQuery.setParameter("startedAfter", threshold)).thenReturn(startedAfterQuery);
        when(startedAfterQuery.setMaxResults(1)).thenReturn(startedAfterQuery);
        when(startedAfterQuery.list()).thenReturn(List.of(segment));

        when(session.createQuery(
                "SELECT COUNT(s) FROM PlayerPlaytimeSegmentEntity s " +
                        "WHERE s.endedAt IS NULL AND s.session.endedAt IS NULL",
                Long.class
        )).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(4L);

        assertSame(segment, repository.findRecentSegmentsForPlayer(8L, 0).getFirst());
        assertSame(segment, repository.findRecentSegmentsForPlayerAndGamemode(8L, " SkyBlock ", 0).getFirst());
        assertSame(segment, repository.findOpenSegments(0).getFirst());
        assertSame(segment, repository.findSegmentsStartedAfter(threshold, 0).getFirst());
        assertEquals(4L, repository.countOpenSegments());
        verify(recentByGamemodeQuery).setParameter("gamemodeKey", "skyblock");
    }

    @Test
    void helperMethodsRejectNullRequiredArguments() {
        PlayerPlaytimeSegmentRepository repository = new PlayerPlaytimeSegmentRepository(mock(ORMContext.class));
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> repository.findOpenSegmentForPlayer(null));
        assertThrows(NullPointerException.class, () -> repository.findRecentSegmentsForPlayer(null, 10));
        assertThrows(NullPointerException.class, () -> repository.findRecentSegmentsForPlayerAndGamemode(null, "lobby", 10));
        assertThrows(NullPointerException.class, () -> repository.findRecentSegmentsForPlayerAndGamemode(1L, null, 10));
        assertThrows(NullPointerException.class, () -> repository.findSegmentsStartedAfter(null, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findRecentSegmentsForPlayerAndGamemode(1L, "   ", 10));
    }
}
