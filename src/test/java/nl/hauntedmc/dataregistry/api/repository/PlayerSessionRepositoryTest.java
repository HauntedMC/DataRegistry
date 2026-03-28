package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
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
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid AND s.endedAt IS NULL ORDER BY s.startedAt DESC",
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
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid ORDER BY s.startedAt DESC",
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
                "SELECT s FROM PlayerSessionEntity s WHERE s.player.id = :pid ORDER BY s.startedAt DESC",
                PlayerSessionEntity.class
        )).thenReturn(latestQuery);
        when(latestQuery.setParameter("pid", 9L)).thenReturn(latestQuery);
        when(latestQuery.setMaxResults(1)).thenReturn(latestQuery);
        when(latestQuery.uniqueResultOptional()).thenReturn(Optional.of(sessionEntity));

        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.endedAt IS NULL ORDER BY s.startedAt DESC",
                PlayerSessionEntity.class
        )).thenReturn(openQuery);
        when(openQuery.setMaxResults(1)).thenReturn(openQuery);
        when(openQuery.list()).thenReturn(List.of(sessionEntity));

        when(session.createQuery(
                "SELECT s FROM PlayerSessionEntity s WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC",
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
    }
}
