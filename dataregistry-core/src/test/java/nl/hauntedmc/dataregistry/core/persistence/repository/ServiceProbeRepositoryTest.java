package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeStatus;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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
        Query<ServiceProbeEntity> latestByObserverQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> checkedAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> byObserverQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByStatusQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByServiceAndStatusQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> staleIdsQuery = mock(Query.class);
        MutationQuery deleteByIdsQuery = mock(MutationQuery.class);

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
                        "WHERE p.service.serviceKind = :kind " +
                        "AND p.service.serviceName = :serviceName " +
                        "AND p.observerInstanceId = :observerInstanceId " +
                        "ORDER BY p.checkedAt DESC, p.id DESC",
                ServiceProbeEntity.class
        )).thenReturn(latestByObserverQuery);
        when(latestByObserverQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(latestByObserverQuery);
        when(latestByObserverQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(latestByObserverQuery);
        when(latestByObserverQuery.setParameter("observerInstanceId", "observer-1"))
                .thenReturn(latestByObserverQuery);
        when(latestByObserverQuery.setMaxResults(1)).thenReturn(latestByObserverQuery);
        when(latestByObserverQuery.uniqueResultOptional()).thenReturn(Optional.of(probe));

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

        when(session.createQuery(
                "SELECT p.id FROM ServiceProbeEntity p " +
                        "WHERE p.checkedAt < :checkedBefore " +
                        "ORDER BY p.checkedAt ASC, p.id ASC",
                Long.class
        )).thenReturn(staleIdsQuery);
        when(staleIdsQuery.setParameter("checkedBefore", cutoff)).thenReturn(staleIdsQuery);
        when(staleIdsQuery.setMaxResults(1)).thenReturn(staleIdsQuery);
        when(staleIdsQuery.list()).thenReturn(List.of(7L), List.of());

        when(session.createMutationQuery(
                "DELETE FROM ServiceProbeEntity p WHERE p.id IN :ids"
        )).thenReturn(deleteByIdsQuery);
        when(deleteByIdsQuery.setParameter("ids", List.of(7L))).thenReturn(deleteByIdsQuery);
        when(deleteByIdsQuery.executeUpdate()).thenReturn(1);

        assertEquals(Optional.of(probe), repository.findMostRecentByService(ServiceKind.BACKEND, " paper-lobby-1 "));
        assertEquals(List.of(probe), repository.findRecentByService(ServiceKind.BACKEND, "paper-lobby-1", 0));
        assertEquals(
                Optional.of(probe),
                repository.findMostRecentByServiceAndObserver(
                        ServiceKind.BACKEND,
                        "paper-lobby-1",
                        " observer-1 "
                )
        );
        assertEquals(List.of(probe), repository.findCheckedAfter(cutoff, 0));
        assertEquals(List.of(probe), repository.findByObserverInstanceId(" observer-1 ", 0));
        assertEquals(5L, repository.countByStatus(ServiceProbeStatus.UP));
        assertEquals(1, repository.deleteCheckedBefore(cutoff, 0));
        assertEquals(
                2L,
                repository.countByServiceAndStatus(
                        ServiceKind.BACKEND,
                        "paper-lobby-1",
                        ServiceProbeStatus.TIMEOUT
                )
        );
        verify(recentByServiceQuery).setMaxResults(1);
        verify(latestByObserverQuery).setMaxResults(1);
        verify(checkedAfterQuery).setMaxResults(1);
        verify(byObserverQuery).setMaxResults(1);
        verify(staleIdsQuery, times(2)).setMaxResults(1);
    }

    @Test
    void deleteCheckedBeforeDrainsAllEligibleRowsAcrossBoundedTransactions() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<Long> staleIdsQuery = mock(Query.class);
        MutationQuery deleteByIdsQuery = mock(MutationQuery.class);
        Instant cutoff = Instant.now().minusSeconds(60);

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT p.id FROM ServiceProbeEntity p " +
                        "WHERE p.checkedAt < :checkedBefore " +
                        "ORDER BY p.checkedAt ASC, p.id ASC",
                Long.class
        )).thenReturn(staleIdsQuery);
        when(staleIdsQuery.setParameter("checkedBefore", cutoff)).thenReturn(staleIdsQuery);
        when(staleIdsQuery.setMaxResults(2)).thenReturn(staleIdsQuery);
        when(staleIdsQuery.list()).thenReturn(List.of(1L, 2L), List.of(3L));
        when(session.createMutationQuery("DELETE FROM ServiceProbeEntity p WHERE p.id IN :ids"))
                .thenReturn(deleteByIdsQuery);
        when(deleteByIdsQuery.setParameter("ids", List.of(1L, 2L))).thenReturn(deleteByIdsQuery);
        when(deleteByIdsQuery.setParameter("ids", List.of(3L))).thenReturn(deleteByIdsQuery);
        when(deleteByIdsQuery.executeUpdate()).thenReturn(2, 1);

        assertEquals(3, new ServiceProbeRepository(ormContext).deleteCheckedBefore(cutoff, 2));
        verify(staleIdsQuery, times(2)).setMaxResults(2);
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
        assertThrows(NullPointerException.class, () ->
                repository.findMostRecentByServiceAndObserver(null, "paper", "observer"));
        assertThrows(NullPointerException.class, () ->
                repository.findMostRecentByServiceAndObserver(ServiceKind.BACKEND, null, "observer"));
        assertThrows(IllegalArgumentException.class, () ->
                repository.findMostRecentByServiceAndObserver(ServiceKind.BACKEND, " ", "observer"));
        assertThrows(NullPointerException.class, () ->
                repository.findMostRecentByServiceAndObserver(ServiceKind.BACKEND, "paper", null));
        assertThrows(IllegalArgumentException.class, () ->
                repository.findMostRecentByServiceAndObserver(ServiceKind.BACKEND, "paper", " "));
        assertThrows(NullPointerException.class, () -> repository.findCheckedAfter(null, 10));
        assertThrows(NullPointerException.class, () -> repository.findByObserverInstanceId(null, 10));
        assertThrows(IllegalArgumentException.class, () -> repository.findByObserverInstanceId(" ", 10));
        assertThrows(NullPointerException.class, () -> repository.countByStatus(null));
        assertThrows(NullPointerException.class, () -> repository.deleteCheckedBefore(null, 10));
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
