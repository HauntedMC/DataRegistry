package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkServiceRepositoryTest {

    @Test
    void helperMethodsQueryByKindNameAndRecency() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> kindAndNameQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> byNameQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> seenAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByKindQuery = mock(Query.class);
        NetworkServiceRepository repository = new NetworkServiceRepository(ormContext);
        NetworkServiceEntity service = new NetworkServiceEntity();
        service.setServiceKind(ServiceKind.BACKEND);
        service.setServiceName("paper-lobby-1");
        Instant threshold = Instant.now().minusSeconds(60);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT s FROM NetworkServiceEntity s WHERE s.serviceKind = :kind AND s.serviceName = :serviceName",
                NetworkServiceEntity.class
        )).thenReturn(kindAndNameQuery);
        when(kindAndNameQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(kindAndNameQuery);
        when(kindAndNameQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(kindAndNameQuery);
        when(kindAndNameQuery.setMaxResults(1)).thenReturn(kindAndNameQuery);
        when(kindAndNameQuery.uniqueResultOptional()).thenReturn(Optional.of(service));

        when(session.createQuery(
                "SELECT s FROM NetworkServiceEntity s WHERE s.serviceName = :serviceName ORDER BY s.serviceKind ASC",
                NetworkServiceEntity.class
        )).thenReturn(byNameQuery);
        when(byNameQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(byNameQuery);
        when(byNameQuery.list()).thenReturn(List.of(service));

        when(session.createQuery(
                "SELECT s FROM NetworkServiceEntity s WHERE s.lastSeenAt >= :seenAfter " +
                        "ORDER BY s.lastSeenAt DESC, s.serviceKind ASC, s.serviceName ASC",
                NetworkServiceEntity.class
        )).thenReturn(seenAfterQuery);
        when(seenAfterQuery.setParameter("seenAfter", threshold)).thenReturn(seenAfterQuery);
        when(seenAfterQuery.setMaxResults(1)).thenReturn(seenAfterQuery);
        when(seenAfterQuery.list()).thenReturn(List.of(service));

        when(session.createQuery(
                "SELECT COUNT(s) FROM NetworkServiceEntity s WHERE s.serviceKind = :kind",
                Long.class
        )).thenReturn(countByKindQuery);
        when(countByKindQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(countByKindQuery);
        when(countByKindQuery.getSingleResult()).thenReturn(2L);

        assertEquals(Optional.of(service), repository.findByKindAndName(ServiceKind.BACKEND, " paper-lobby-1 "));
        assertTrue(repository.existsByKindAndName(ServiceKind.BACKEND, "paper-lobby-1"));
        assertEquals(List.of(service), repository.findByServiceName("paper-lobby-1"));
        assertEquals(List.of(service), repository.findSeenAfter(threshold, 0));
        assertEquals(2L, repository.countByKind(ServiceKind.BACKEND));
        verify(seenAfterQuery).setMaxResults(1);
    }

    @Test
    void helperMethodsRejectInvalidRequiredArguments() {
        NetworkServiceRepository repository = new NetworkServiceRepository(mock(ORMContext.class));

        assertThrows(NullPointerException.class, () -> repository.findByKindAndName(null, "paper"));
        assertThrows(NullPointerException.class, () -> repository.findByKindAndName(ServiceKind.BACKEND, null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByKindAndName(ServiceKind.BACKEND, " "));
        assertThrows(NullPointerException.class, () -> repository.findByServiceName(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByServiceName(" "));
        assertThrows(NullPointerException.class, () -> repository.findSeenAfter(null, 5));
        assertThrows(NullPointerException.class, () -> repository.countByKind(null));
        assertThrows(NullPointerException.class, () -> repository.existsByKindAndName(null, "paper"));
        assertThrows(NullPointerException.class, () -> repository.existsByKindAndName(ServiceKind.BACKEND, null));
        assertThrows(IllegalArgumentException.class, () -> repository.existsByKindAndName(ServiceKind.BACKEND, " "));
    }
}
