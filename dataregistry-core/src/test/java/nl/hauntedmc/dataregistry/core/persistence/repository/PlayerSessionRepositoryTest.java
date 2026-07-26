package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
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

class PlayerSessionRepositoryTest {

    @Test
    void findOpenSessionForPlayerReturnsLatestOpenSession() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> query = mock(Query.class);
        PlayerSessionRepository repository = new PlayerSessionRepository(ormContext);
        PlayerSessionEntity openSession = new PlayerSessionEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid AND s.endedAt IS NULL ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(query);
        when(query.setParameter("pid", 12L)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(openSession));

        Optional<PlayerSessionEntity> result = repository.findOpenSessionForPlayer(12L);
        assertEquals(Optional.of(openSession), result);
    }

    @Test
    void closeAllOpenSessionsExecutesBulkUpdate() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        MutationQuery mutationQuery = mock(MutationQuery.class);
        PlayerSessionRepository repository = new PlayerSessionRepository(ormContext);
        Instant endTime = Instant.now();

        executeTransactionsWithSession(ormContext, session);
        when(session.createMutationQuery(
                "UPDATE PlayerSessionEntity s SET s.endedAt = :end WHERE s.player.id = :pid AND s.endedAt IS NULL"
        )).thenReturn(mutationQuery);
        when(mutationQuery.setParameter("pid", 15L)).thenReturn(mutationQuery);
        when(mutationQuery.setParameter("end", endTime)).thenReturn(mutationQuery);
        when(mutationQuery.executeUpdate()).thenReturn(3);

        int updatedRows = repository.closeAllOpenSessions(15L, endTime);

        assertEquals(3, updatedRows);
    }

    @Test
    void findRecentSessionsUsesMinimumLimitOfOne() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> query = mock(Query.class);
        PlayerSessionRepository repository = new PlayerSessionRepository(ormContext);
        List<PlayerSessionEntity> sessions = List.of(new PlayerSessionEntity());

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(query);
        when(query.setParameter("pid", 15L)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.list()).thenReturn(sessions);

        List<PlayerSessionEntity> result = repository.findRecentSessions(15L, 0);

        assertSame(sessions, result);
        verify(query).setMaxResults(1);
    }

    @Test
    void helperMethodsExposeOpenLatestAndRecentSessionQueries() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> latestQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> openQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionEntity> startedAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countQuery = mock(Query.class);
        PlayerSessionRepository repository = new PlayerSessionRepository(ormContext);
        PlayerSessionEntity sessionEntity = new PlayerSessionEntity();
        Instant threshold = Instant.now().minusSeconds(60);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(latestQuery);
        when(latestQuery.setParameter("pid", 9L)).thenReturn(latestQuery);
        when(latestQuery.setMaxResults(1)).thenReturn(latestQuery);
        when(latestQuery.uniqueResultOptional()).thenReturn(Optional.of(sessionEntity));

        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.endedAt IS NULL ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(openQuery);
        when(openQuery.setMaxResults(1)).thenReturn(openQuery);
        when(openQuery.list()).thenReturn(List.of(sessionEntity));

        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC, s.id DESC",
                PlayerSessionEntity.class
        )).thenReturn(startedAfterQuery);
        when(startedAfterQuery.setParameter("startedAfter", threshold)).thenReturn(startedAfterQuery);
        when(startedAfterQuery.setMaxResults(1)).thenReturn(startedAfterQuery);
        when(startedAfterQuery.list()).thenReturn(List.of(sessionEntity));

        when(session.createQuery(
                "SELECT COUNT(s) FROM PlayerSessionEntity s WHERE s.endedAt IS NULL",
                Long.class
        )).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(3L);

        assertEquals(Optional.of(sessionEntity), repository.findLatestSessionForPlayer(9L));
        assertEquals(1, repository.findOpenSessions(0).size());
        assertEquals(1, repository.findSessionsStartedAfter(threshold, 0).size());
        assertEquals(3L, repository.countOpenSessions());
    }

    @Test
    void helperMethodsRejectNullRequiredArguments() {
        PlayerSessionRepository repository = new PlayerSessionRepository(mock(ORMContext.class));
        Instant now = Instant.now();

        assertThrows(NullPointerException.class, () -> repository.findOpenSessionForPlayer(null));
        assertThrows(NullPointerException.class, () -> repository.closeAllOpenSessions(null, now));
        assertThrows(NullPointerException.class, () -> repository.closeAllOpenSessions(1L, null));
        assertThrows(NullPointerException.class, () -> repository.findRecentSessions(null, 10));
        assertThrows(NullPointerException.class, () -> repository.findLatestSessionForPlayer(null));
        assertThrows(NullPointerException.class, () -> repository.findSessionsStartedAfter(null, 10));
        assertThrows(NullPointerException.class, () -> repository.deleteClosedHistoryBefore(null, 10));
    }

    @Test
    void deleteClosedHistoryRemovesOnlyAClosedBoundedSessionChain() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<Long> idsQuery = mock(Query.class);
        MutationQuery visitDelete = mock(MutationQuery.class);
        MutationQuery segmentDelete = mock(MutationQuery.class);
        MutationQuery sessionDelete = mock(MutationQuery.class);
        PlayerSessionRepository repository = new PlayerSessionRepository(ormContext);
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");
        List<Long> sessionIds = List.of(7L);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s.id FROM PlayerSessionEntity s " +
                        "WHERE s.endedAt < :cutoff " +
                        "AND NOT EXISTS (SELECT 1 FROM PlayerSessionVisitEntity v " +
                        "WHERE v.session.id = s.id AND v.leftAt IS NULL) " +
                        "AND NOT EXISTS (SELECT 1 FROM PlayerPlaytimeSegmentEntity p " +
                        "WHERE p.session.id = s.id AND p.endedAt IS NULL) " +
                        "ORDER BY s.endedAt ASC, s.id ASC",
                Long.class
        )).thenReturn(idsQuery);
        when(idsQuery.setParameter("cutoff", cutoff)).thenReturn(idsQuery);
        when(idsQuery.setMaxResults(1)).thenReturn(idsQuery);
        when(idsQuery.list()).thenReturn(sessionIds);
        when(session.createMutationQuery(
                "DELETE FROM PlayerSessionVisitEntity v WHERE v.session.id IN :sessionIds"
        )).thenReturn(visitDelete);
        when(visitDelete.setParameter("sessionIds", sessionIds)).thenReturn(visitDelete);
        when(visitDelete.executeUpdate()).thenReturn(2);
        when(session.createMutationQuery(
                "DELETE FROM PlayerPlaytimeSegmentEntity p WHERE p.session.id IN :sessionIds"
        )).thenReturn(segmentDelete);
        when(segmentDelete.setParameter("sessionIds", sessionIds)).thenReturn(segmentDelete);
        when(segmentDelete.executeUpdate()).thenReturn(3);
        when(session.createMutationQuery(
                "DELETE FROM PlayerSessionEntity s WHERE s.id IN :sessionIds"
        )).thenReturn(sessionDelete);
        when(sessionDelete.setParameter("sessionIds", sessionIds)).thenReturn(sessionDelete);
        when(sessionDelete.executeUpdate()).thenReturn(1);

        assertEquals(1, repository.deleteClosedHistoryBefore(cutoff, 0));
        verify(idsQuery).setMaxResults(1);
        verify(visitDelete).executeUpdate();
        verify(segmentDelete).executeUpdate();
        verify(sessionDelete).executeUpdate();
    }
}
