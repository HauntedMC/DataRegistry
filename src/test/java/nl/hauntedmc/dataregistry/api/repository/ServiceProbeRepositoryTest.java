package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeStatus;
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

class ServiceProbeRepositoryTest {

    @Test
    void helperMethodsQueryRecentProbeDataAndCounts() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> latestByServiceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> recentByServiceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> checkedAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> byObserverQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByStatusQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByServiceAndStatusQuery = mock(Query.class);

        ServiceProbeRepository repository = new ServiceProbeRepository(ormContext);
        ServiceProbeEntity probe = new ServiceProbeEntity();
        Instant cutoff = Instant.now().minusSeconds(45);

        executeTransactionsWithSession(ormContext, session);

        when(session.createQuery(
                "SELECT p FROM ServiceProbeEntity p " +
                        "WHERE p.service.serviceKind = :kind " +
                        "AND p.service.serviceName = :serviceName " +
                        "ORDER BY p.checkedAt DESC, p.id DESC",
                ServiceProbeEntity.class
        )).thenReturn(latestByServiceQuery, recentByServiceQuery);
        when(latestByServiceQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(latestByServiceQuery);
        when(latestByServiceQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(latestByServiceQuery);
        when(latestByServiceQuery.setMaxResults(1)).thenReturn(latestByServiceQuery);
        when(latestByServiceQuery.uniqueResultOptional()).thenReturn(Optional.of(probe));
        when(recentByServiceQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(recentByServiceQuery);
        when(recentByServiceQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(recentByServiceQuery);
        when(recentByServiceQuery.setMaxResults(1)).thenReturn(recentByServiceQuery);
        when(recentByServiceQuery.list()).thenReturn(List.of(probe));

        when(session.createQuery(
                "SELECT p FROM ServiceProbeEntity p " +
                        "WHERE p.checkedAt >= :checkedAfter " +
                        "ORDER BY p.checkedAt DESC, p.id DESC",
                ServiceProbeEntity.class
        )).thenReturn(checkedAfterQuery);
        when(checkedAfterQuery.setParameter("checkedAfter", cutoff)).thenReturn(checkedAfterQuery);
        when(checkedAfterQuery.setMaxResults(1)).thenReturn(checkedAfterQuery);
        when(checkedAfterQuery.list()).thenReturn(List.of(probe));

        when(session.createQuery(
                "SELECT p FROM ServiceProbeEntity p " +
                        "WHERE p.observerInstanceId = :observerInstanceId " +
                        "ORDER BY p.checkedAt DESC, p.id DESC",
                ServiceProbeEntity.class
        )).thenReturn(byObserverQuery);
        when(byObserverQuery.setParameter("observerInstanceId", "observer-1")).thenReturn(byObserverQuery);
        when(byObserverQuery.setMaxResults(1)).thenReturn(byObserverQuery);
        when(byObserverQuery.list()).thenReturn(List.of(probe));

        when(session.createQuery(
                "SELECT COUNT(p) FROM ServiceProbeEntity p WHERE p.status = :status",
                Long.class
        )).thenReturn(countByStatusQuery);
        when(countByStatusQuery.setParameter("status", ServiceProbeStatus.UP)).thenReturn(countByStatusQuery);
        when(countByStatusQuery.getSingleResult()).thenReturn(5L);

        when(session.createQuery(
                "SELECT COUNT(p) FROM ServiceProbeEntity p " +
                        "WHERE p.service.serviceKind = :kind " +
                        "AND p.service.serviceName = :serviceName " +
                        "AND p.status = :status",
                Long.class
        )).thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("kind", ServiceKind.BACKEND))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("serviceName", "paper-lobby-1"))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("status", ServiceProbeStatus.TIMEOUT))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.getSingleResult()).thenReturn(2L);

        assertEquals(Optional.of(probe), repository.findMostRecentByService(ServiceKind.BACKEND, " paper-lobby-1 "));
        assertEquals(List.of(probe), repository.findRecentByService(ServiceKind.BACKEND, "paper-lobby-1", 0));
        assertEquals(List.of(probe), repository.findCheckedAfter(cutoff, 0));
        assertEquals(List.of(probe), repository.findByObserverInstanceId(" observer-1 ", 0));
        assertEquals(5L, repository.countByStatus(ServiceProbeStatus.UP));
        assertEquals(
                2L,
                repository.countByServiceAndStatus(
                        ServiceKind.BACKEND,
                        "paper-lobby-1",
                        ServiceProbeStatus.TIMEOUT
                )
        );
        verify(recentByServiceQuery).setMaxResults(1);
        verify(checkedAfterQuery).setMaxResults(1);
        verify(byObserverQuery).setMaxResults(1);
    }

    @Test
    void helperMethodsRejectInvalidArguments() {
        ServiceProbeRepository repository = new ServiceProbeRepository(mock(ORMContext.class));

        assertThrows(NullPointerException.class, () -> repository.findMostRecentByService(null, "paper"));
        assertThrows(NullPointerException.class, () -> repository.findMostRecentByService(ServiceKind.BACKEND, null));
        assertThrows(IllegalArgumentException.class, () -> repository.findMostRecentByService(ServiceKind.BACKEND, " "));
        assertThrows(NullPointerException.class, () -> repository.findRecentByService(null, "paper", 10));
        assertThrows(NullPointerException.class, () -> repository.findRecentByService(ServiceKind.BACKEND, null, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findRecentByService(ServiceKind.BACKEND, " ", 10));
        assertThrows(NullPointerException.class, () -> repository.findCheckedAfter(null, 10));
        assertThrows(NullPointerException.class, () -> repository.findByObserverInstanceId(null, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findByObserverInstanceId(" ", 10));
        assertThrows(NullPointerException.class, () -> repository.countByStatus(null));
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(null, "paper", ServiceProbeStatus.UP)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, null, ServiceProbeStatus.UP)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, " ", ServiceProbeStatus.UP)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, "paper", null)
        );
    }
}
