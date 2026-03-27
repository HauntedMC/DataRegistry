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
}
