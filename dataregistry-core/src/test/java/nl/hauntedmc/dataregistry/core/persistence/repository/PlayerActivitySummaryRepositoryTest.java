package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerActivitySummaryRepositoryTest {

    @Test
    void findByPlayerIdDelegatesToPrimaryKeyLookup() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        PlayerActivitySummaryRepository repository = new PlayerActivitySummaryRepository(ormContext);
        PlayerActivitySummaryEntity summary = new PlayerActivitySummaryEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.find(PlayerActivitySummaryEntity.class, 4L)).thenReturn(summary);

        assertEquals(Optional.of(summary), repository.findByPlayerId(4L));
    }

    @Test
    void findRecentlySeenAppliesMinimumLimitOfOne() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<PlayerActivitySummaryEntity> query = mock(Query.class);
        PlayerActivitySummaryRepository repository = new PlayerActivitySummaryRepository(ormContext);
        List<PlayerActivitySummaryEntity> summaries = List.of(new PlayerActivitySummaryEntity());

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM PlayerActivitySummaryEntity s ORDER BY s.lastSeenAt DESC, s.playerId DESC",
                PlayerActivitySummaryEntity.class
        )).thenReturn(query);
        when(query.setMaxResults(1)).thenReturn(query);
        when(query.list()).thenReturn(summaries);

        List<PlayerActivitySummaryEntity> result = repository.findRecentlySeen(0);

        assertSame(summaries, result);
        verify(query).setMaxResults(1);
    }

    @Test
    void findByPlayerIdRejectsNull() {
        PlayerActivitySummaryRepository repository = new PlayerActivitySummaryRepository(mock(ORMContext.class));
        assertThrows(NullPointerException.class, () -> repository.findByPlayerId(null));
    }
}
