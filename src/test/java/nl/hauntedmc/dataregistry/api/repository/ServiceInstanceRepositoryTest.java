package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
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

class ServiceInstanceRepositoryTest {

    @Test
    void lookupHelpersReturnExpectedResults() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> byInstanceIdQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> byStatusQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> byServiceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> runningByServiceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> mostRecentQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> byEndpointQuery = mock(Query.class);
        ServiceInstanceRepository repository = new ServiceInstanceRepository(ormContext);
        ServiceInstanceEntity instance = new ServiceInstanceEntity();
        instance.setInstanceId("inst-1");

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.instanceId = :instanceId",
                ServiceInstanceEntity.class
        )).thenReturn(byInstanceIdQuery);
        when(byInstanceIdQuery.setParameter("instanceId", "inst-1")).thenReturn(byInstanceIdQuery);
        when(byInstanceIdQuery.setMaxResults(1)).thenReturn(byInstanceIdQuery);
        when(byInstanceIdQuery.uniqueResultOptional()).thenReturn(Optional.of(instance));

        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.status = :status ORDER BY i.lastSeenAt DESC",
                ServiceInstanceEntity.class
        )).thenReturn(byStatusQuery);
        when(byStatusQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(byStatusQuery);
        when(byStatusQuery.list()).thenReturn(List.of(instance));

        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.service.serviceKind = :kind " +
                        "AND i.service.serviceName = :serviceName ORDER BY i.lastSeenAt DESC",
                ServiceInstanceEntity.class
        )).thenReturn(byServiceQuery);
        when(byServiceQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(byServiceQuery);
        when(byServiceQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(byServiceQuery);
        when(byServiceQuery.list()).thenReturn(List.of(instance));

        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.service.serviceKind = :kind " +
                        "AND i.service.serviceName = :serviceName AND i.status = :status " +
                        "ORDER BY i.lastSeenAt DESC",
                ServiceInstanceEntity.class
        )).thenReturn(runningByServiceQuery, mostRecentQuery);
        when(runningByServiceQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(runningByServiceQuery);
        when(runningByServiceQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(runningByServiceQuery);
        when(runningByServiceQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(runningByServiceQuery);
        when(runningByServiceQuery.setMaxResults(1)).thenReturn(runningByServiceQuery);
        when(runningByServiceQuery.list()).thenReturn(List.of(instance));
        when(mostRecentQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(mostRecentQuery);
        when(mostRecentQuery.setParameter("serviceName", "paper-lobby-1")).thenReturn(mostRecentQuery);
        when(mostRecentQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(mostRecentQuery);
        when(mostRecentQuery.setMaxResults(1)).thenReturn(mostRecentQuery);
        when(mostRecentQuery.uniqueResultOptional()).thenReturn(Optional.of(instance));

        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i " +
                        "WHERE i.service.serviceKind = :kind " +
                        "AND i.status = :status " +
                        "AND i.host = :host " +
                        "AND i.port = :port " +
                        "ORDER BY i.lastSeenAt DESC",
                ServiceInstanceEntity.class
        )).thenReturn(byEndpointQuery);
        when(byEndpointQuery.setParameter("kind", ServiceKind.BACKEND)).thenReturn(byEndpointQuery);
        when(byEndpointQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(byEndpointQuery);
        when(byEndpointQuery.setParameter("host", "10.0.0.5")).thenReturn(byEndpointQuery);
        when(byEndpointQuery.setParameter("port", 25565)).thenReturn(byEndpointQuery);
        when(byEndpointQuery.setMaxResults(1)).thenReturn(byEndpointQuery);
        when(byEndpointQuery.uniqueResultOptional()).thenReturn(Optional.of(instance));

        assertEquals(Optional.of(instance), repository.findByInstanceId(" inst-1 "));
        assertTrue(repository.existsByInstanceId("inst-1"));
        assertEquals(List.of(instance), repository.findByStatus(ServiceInstanceStatus.RUNNING));
        assertEquals(List.of(instance), repository.findByService(ServiceKind.BACKEND, "paper-lobby-1"));
        assertEquals(List.of(instance), repository.findRunningByService(ServiceKind.BACKEND, " paper-lobby-1 ", 0));
        assertEquals(
                Optional.of(instance),
                repository.findMostRecentByServiceAndStatus(
                        ServiceKind.BACKEND,
                        "paper-lobby-1",
                        ServiceInstanceStatus.RUNNING
                )
        );
        assertEquals(
                Optional.of(instance),
                repository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, " 10.0.0.5 ", 25565)
        );
        verify(runningByServiceQuery).setMaxResults(1);
    }

    @Test
    void analyticsHelpersReturnCountsAndRecencyLists() {
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> seenAfterQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> staleLimitedQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> staleAllQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByStatusQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<Long> countByServiceAndStatusQuery = mock(Query.class);
        ServiceInstanceRepository repository = new ServiceInstanceRepository(ormContext);
        Instant seenAfter = Instant.now().minusSeconds(60);
        Instant seenBefore = Instant.now().minusSeconds(300);
        ServiceInstanceEntity staleA = new ServiceInstanceEntity();
        ServiceInstanceEntity staleB = new ServiceInstanceEntity();

        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.lastSeenAt >= :seenAfter ORDER BY i.lastSeenAt DESC",
                ServiceInstanceEntity.class
        )).thenReturn(seenAfterQuery);
        when(seenAfterQuery.setParameter("seenAfter", seenAfter)).thenReturn(seenAfterQuery);
        when(seenAfterQuery.setMaxResults(1)).thenReturn(seenAfterQuery);
        when(seenAfterQuery.list()).thenReturn(List.of(staleA));

        when(session.createQuery(
                "SELECT i FROM ServiceInstanceEntity i WHERE i.status = :status AND i.lastSeenAt < :seenBefore " +
                        "ORDER BY i.lastSeenAt ASC",
                ServiceInstanceEntity.class
        )).thenReturn(staleLimitedQuery, staleAllQuery);
        when(staleLimitedQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(staleLimitedQuery);
        when(staleLimitedQuery.setParameter("seenBefore", seenBefore)).thenReturn(staleLimitedQuery);
        when(staleLimitedQuery.setMaxResults(1)).thenReturn(staleLimitedQuery);
        when(staleLimitedQuery.list()).thenReturn(List.of(staleA));
        when(staleAllQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(staleAllQuery);
        when(staleAllQuery.setParameter("seenBefore", seenBefore)).thenReturn(staleAllQuery);
        when(staleAllQuery.list()).thenReturn(List.of(staleA, staleB));

        when(session.createQuery(
                "SELECT COUNT(i) FROM ServiceInstanceEntity i WHERE i.status = :status",
                Long.class
        )).thenReturn(countByStatusQuery);
        when(countByStatusQuery.setParameter("status", ServiceInstanceStatus.RUNNING)).thenReturn(countByStatusQuery);
        when(countByStatusQuery.getSingleResult()).thenReturn(3L);

        when(session.createQuery(
                "SELECT COUNT(i) FROM ServiceInstanceEntity i " +
                        "WHERE i.service.serviceKind = :kind " +
                        "AND i.service.serviceName = :serviceName " +
                        "AND i.status = :status",
                Long.class
        )).thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("kind", ServiceKind.BACKEND))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("serviceName", "paper-lobby-1"))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.setParameter("status", ServiceInstanceStatus.RUNNING))
                .thenReturn(countByServiceAndStatusQuery);
        when(countByServiceAndStatusQuery.getSingleResult()).thenReturn(2L);

        assertEquals(1, repository.findSeenAfter(seenAfter, 0).size());
        assertEquals(1, repository.findRunningSeenBefore(seenBefore, 0).size());
        assertEquals(2, repository.findRunningSeenBefore(seenBefore).size());
        assertEquals(3L, repository.countByStatus(ServiceInstanceStatus.RUNNING));
        assertEquals(
                2L,
                repository.countByServiceAndStatus(
                        ServiceKind.BACKEND,
                        "paper-lobby-1",
                        ServiceInstanceStatus.RUNNING
                )
        );
        verify(staleLimitedQuery).setMaxResults(1);
    }

    @Test
    void helperMethodsRejectInvalidArguments() {
        ServiceInstanceRepository repository = new ServiceInstanceRepository(mock(ORMContext.class));

        assertThrows(NullPointerException.class, () -> repository.findByInstanceId(null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByInstanceId(" "));
        assertThrows(NullPointerException.class, () -> repository.existsByInstanceId(null));
        assertThrows(IllegalArgumentException.class, () -> repository.existsByInstanceId(" "));
        assertThrows(NullPointerException.class, () -> repository.findByStatus(null));
        assertThrows(NullPointerException.class, () -> repository.findByService(null, "paper"));
        assertThrows(NullPointerException.class, () -> repository.findByService(ServiceKind.BACKEND, null));
        assertThrows(IllegalArgumentException.class, () -> repository.findByService(ServiceKind.BACKEND, " "));
        assertThrows(NullPointerException.class, () -> repository.findRunningByService(null, "paper", 5));
        assertThrows(NullPointerException.class, () -> repository.findRunningByService(ServiceKind.BACKEND, null, 5));
        assertThrows(IllegalArgumentException.class, () -> repository.findRunningByService(ServiceKind.BACKEND, " ", 5));
        assertThrows(NullPointerException.class, () -> repository.findSeenAfter(null, 5));
        assertThrows(NullPointerException.class, () -> repository.findRunningSeenBefore((Instant) null, 5));
        assertThrows(NullPointerException.class, () -> repository.findRunningSeenBefore((Instant) null));
        assertThrows(NullPointerException.class, () -> repository.countByStatus(null));
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(null, "paper", ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, null, ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, " ", ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.countByServiceAndStatus(ServiceKind.BACKEND, "paper", null)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentByServiceAndStatus(null, "paper", ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentByServiceAndStatus(ServiceKind.BACKEND, null, ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findMostRecentByServiceAndStatus(ServiceKind.BACKEND, " ", ServiceInstanceStatus.RUNNING)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentByServiceAndStatus(ServiceKind.BACKEND, "paper", null)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentRunningByEndpoint(null, "10.0.0.5", 25565)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, null, 25565)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, " ", 25565)
        );
        assertThrows(
                NullPointerException.class,
                () -> repository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, "10.0.0.5", null)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> repository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, "10.0.0.5", 65536)
        );
    }
}
