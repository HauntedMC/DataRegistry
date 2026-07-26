package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.MutationQuery;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerLifecycleOutboxRepositoryTest {

    @Test
    void deleteCreatedBeforeDeletesOnlyTheOldestBoundedBatch() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<Long> idsQuery = mock(Query.class);
        MutationQuery deleteQuery = mock(MutationQuery.class);
        PlayerLifecycleOutboxRepository repository = new PlayerLifecycleOutboxRepository(ormContext);
        Instant cutoff = Instant.parse("2026-01-01T00:00:00Z");

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT o.id FROM PlayerLifecycleOutboxEntity o " +
                        "WHERE o.createdAt < :cutoff ORDER BY o.createdAt ASC, o.id ASC",
                Long.class
        )).thenReturn(idsQuery);
        when(idsQuery.setParameter("cutoff", cutoff)).thenReturn(idsQuery);
        when(idsQuery.setMaxResults(1)).thenReturn(idsQuery);
        when(idsQuery.list()).thenReturn(List.of(4L));
        when(session.createMutationQuery(
                "DELETE FROM PlayerLifecycleOutboxEntity o WHERE o.id IN :ids"
        )).thenReturn(deleteQuery);
        when(deleteQuery.setParameter("ids", List.of(4L))).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);

        assertEquals(1, repository.deleteCreatedBefore(cutoff, 0));
        verify(idsQuery).setMaxResults(1);
    }

    @Test
    void deleteCreatedBeforeRejectsNullCutoff() {
        PlayerLifecycleOutboxRepository repository = new PlayerLifecycleOutboxRepository(mock(ORMContext.class));

        assertThrows(NullPointerException.class, () -> repository.deleteCreatedBefore(null, 10));
    }
}
