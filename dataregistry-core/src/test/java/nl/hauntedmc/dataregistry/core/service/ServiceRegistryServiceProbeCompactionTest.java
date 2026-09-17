package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.entity.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeStatus;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ServiceRegistryServiceProbeCompactionTest {

    @Test
    void healthySteadyStateRefreshesCurrentRowButFailuresAndRecoveryAppendHistory() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> serviceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceProbeEntity> latestProbeQuery = mock(Query.class);
        AtomicReference<ServiceProbeEntity> latestProbe = new AtomicReference<>();
        AtomicInteger persistedProbeRows = new AtomicInteger();

        NetworkServiceEntity serviceEntity = new NetworkServiceEntity();
        serviceEntity.setId(1L);
        serviceEntity.setServiceKind(ServiceKind.BACKEND);
        serviceEntity.setServiceName("lobby");
        serviceEntity.setPlatform("PAPER");
        serviceEntity.setFirstSeenAt(Instant.now().minusSeconds(60));
        serviceEntity.setLastSeenAt(Instant.now());

        when(registry.getServiceORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(
                contains("FROM NetworkServiceEntity"),
                eq(NetworkServiceEntity.class)
        )).thenReturn(serviceQuery);
        when(serviceQuery.setParameter(anyString(), any())).thenReturn(serviceQuery);
        when(serviceQuery.setMaxResults(anyInt())).thenReturn(serviceQuery);
        when(serviceQuery.uniqueResult()).thenReturn(serviceEntity);

        when(session.createQuery(
                contains("FROM ServiceProbeEntity p"),
                eq(ServiceProbeEntity.class)
        )).thenReturn(latestProbeQuery);
        when(latestProbeQuery.setParameter(anyString(), any())).thenReturn(latestProbeQuery);
        when(latestProbeQuery.setMaxResults(anyInt())).thenReturn(latestProbeQuery);
        when(latestProbeQuery.uniqueResult()).thenAnswer(ignored -> latestProbe.get());
        doAnswer(invocation -> {
            Object entity = invocation.getArgument(0);
            if (entity instanceof ServiceProbeEntity probe) {
                probe.setId((long) persistedProbeRows.incrementAndGet());
                latestProbe.set(probe);
            }
            return null;
        }).when(session).persist(any());

        ServiceRegistryService service = new ServiceRegistryService(
                registry,
                logger,
                true,
                new ServiceProbeHistoryPolicy(Duration.ofMinutes(5))
        );

        service.recordProbe(
                ServiceKind.BACKEND,
                "lobby",
                "PAPER",
                "observer-1",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                "backend-instance-1",
                25L,
                null,
                null
        );
        assertEquals(1, persistedProbeRows.get());
        ServiceProbeEntity rollingHealthy = latestProbe.get();
        assertNotNull(rollingHealthy);
        Instant firstCheckedAt = rollingHealthy.getCheckedAt();

        service.recordProbe(
                ServiceKind.BACKEND,
                "lobby",
                "PAPER",
                "observer-1",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                "backend-instance-1",
                11L,
                null,
                null
        );

        assertEquals(1, persistedProbeRows.get());
        assertEquals(rollingHealthy, latestProbe.get());
        assertEquals(11L, rollingHealthy.getLatencyMillis());
        assertTrue(!rollingHealthy.getCheckedAt().isBefore(firstCheckedAt));

        service.recordProbe(
                ServiceKind.BACKEND,
                "lobby",
                "PAPER",
                "observer-1",
                ServiceProbeStatus.TIMEOUT,
                "127.0.0.1",
                25565,
                "backend-instance-1",
                null,
                "timeout",
                "Probe timed out"
        );
        assertEquals(2, persistedProbeRows.get());
        assertEquals(ServiceProbeStatus.TIMEOUT, latestProbe.get().getStatus());

        service.recordProbe(
                ServiceKind.BACKEND,
                "lobby",
                "PAPER",
                "observer-1",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                "backend-instance-1",
                13L,
                null,
                null
        );
        assertEquals(3, persistedProbeRows.get());
        assertEquals(ServiceProbeStatus.UP, latestProbe.get().getStatus());
    }
}
