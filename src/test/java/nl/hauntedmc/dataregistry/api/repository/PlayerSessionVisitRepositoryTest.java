package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSessionVisitRepositoryTest {

    @Test
    void findOpenVisitForPlayerReturnsLatestOpenVisit() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> query = mock(Query.class);
        PlayerSessionVisitRepository repository = new PlayerSessionVisitRepository(ormContext);
        PlayerSessionVisitEntity visit = new PlayerSessionVisitEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v WHERE v.player.id = :playerId AND v.leftAt IS NULL ORDER BY v.enteredAt DESC, v.id DESC",
                PlayerSessionVisitEntity.class
        )).thenReturn(query);
        when(query.setParameter("playerId", 3L)).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.uniqueResultOptional()).thenReturn(Optional.of(visit));

        assertEquals(Optional.of(visit), repository.findOpenVisitForPlayer(3L));
    }

    @Test
    void helperMethodsQueryRecentPlayerAndSessionVisits() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> playerQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> sessionQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<PlayerSessionVisitEntity> enteredAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countQuery = mock(Query.class);
        PlayerSessionVisitRepository repository = new PlayerSessionVisitRepository(ormContext);
        PlayerSessionVisitEntity visit = new PlayerSessionVisitEntity();
        Instant threshold = Instant.now().minusSeconds(30L);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v WHERE v.player.id = :playerId ORDER BY v.enteredAt DESC, v.id DESC",
                PlayerSessionVisitEntity.class
        )).thenReturn(playerQuery);
        when(playerQuery.setParameter("playerId", 7L)).thenReturn(playerQuery);
        when(playerQuery.setMaxResults(1)).thenReturn(playerQuery);
        when(playerQuery.list()).thenReturn(List.of(visit));

        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v WHERE v.session.id = :sessionId ORDER BY v.enteredAt ASC, v.id ASC",
                PlayerSessionVisitEntity.class
        )).thenReturn(sessionQuery);
        when(sessionQuery.setParameter("sessionId", 11L)).thenReturn(sessionQuery);
        when(sessionQuery.setMaxResults(1)).thenReturn(sessionQuery);
        when(sessionQuery.list()).thenReturn(List.of(visit));

        when(session.createQuery(
                "SELECT v FROM PlayerSessionVisitEntity v WHERE v.enteredAt >= :enteredAfter ORDER BY v.enteredAt DESC, v.id DESC",
                PlayerSessionVisitEntity.class
        )).thenReturn(enteredAfterQuery);
        when(enteredAfterQuery.setParameter("enteredAfter", threshold)).thenReturn(enteredAfterQuery);
        when(enteredAfterQuery.setMaxResults(1)).thenReturn(enteredAfterQuery);
        when(enteredAfterQuery.list()).thenReturn(List.of(visit));

        when(session.createQuery(
                "SELECT COUNT(v) FROM PlayerSessionVisitEntity v WHERE v.leftAt IS NULL",
                Long.class
        )).thenReturn(countQuery);
        when(countQuery.getSingleResult()).thenReturn(2L);

        assertEquals(1, repository.findRecentVisitsForPlayer(7L, 0).size());
        assertEquals(1, repository.findRecentVisitsForSession(11L, 0).size());
        assertEquals(1, repository.findVisitsEnteredAfter(threshold, 0).size());
        assertEquals(2L, repository.countOpenVisits());
        verify(playerQuery).setMaxResults(1);
    }

    @Test
    void helperMethodsRejectNullArguments() {
        PlayerSessionVisitRepository repository = new PlayerSessionVisitRepository(mock(ORMContext.class));
        Instant threshold = Instant.now();

        assertThrows(NullPointerException.class, () -> repository.findOpenVisitForPlayer(null));
        assertThrows(NullPointerException.class, () -> repository.findRecentVisitsForPlayer(null, 5));
        assertThrows(NullPointerException.class, () -> repository.findRecentVisitsForSession(null, 5));
        assertThrows(NullPointerException.class, () -> repository.findVisitsEnteredAfter(null, 5));
    }
}
