package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeStatus;
import nl.hauntedmc.dataregistry.api.repository.NetworkServiceRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceInstanceRepository;
import nl.hauntedmc.dataregistry.api.repository.ServiceProbeRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;
import org.hibernate.query.Query;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceRegistryServiceTest {

    @Test
    void constructorRejectsNullArguments() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        assertThrows(NullPointerException.class, () -> new ServiceRegistryService(null, logger, true));
        assertThrows(NullPointerException.class, () -> new ServiceRegistryService(registry, null, true));
    }

    @Test
    void refreshRunningInstanceCreatesServiceAndInstanceWhenMissing() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> serviceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> instanceQuery = mock(Query.class);

        when(registry.getServiceORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(contains("FROM NetworkServiceEntity"), org.mockito.ArgumentMatchers.eq(NetworkServiceEntity.class)))
                .thenReturn(serviceQuery);
        when(serviceQuery.setParameter(anyString(), any())).thenReturn(serviceQuery);
        when(serviceQuery.setMaxResults(anyInt())).thenReturn(serviceQuery);
        when(serviceQuery.uniqueResult()).thenReturn(null);
        when(session.createQuery(contains("FROM ServiceInstanceEntity"), org.mockito.ArgumentMatchers.eq(ServiceInstanceEntity.class)))
                .thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.setMaxResults(anyInt())).thenReturn(instanceQuery);
        when(instanceQuery.uniqueResult()).thenReturn(null);

        service.refreshRunningInstance(
                ServiceKind.BACKEND,
                "paper-lobby-1",
                "PAPER",
                "e4a9f6d6-2b90-4f9f-9807-99eb62a4a350",
                "127.0.0.1",
                25565
        );

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(session, times(2)).persist(captor.capture());
        NetworkServiceEntity persistedService = null;
        ServiceInstanceEntity persistedInstance = null;
        for (Object value : captor.getAllValues()) {
            if (value instanceof NetworkServiceEntity networkServiceEntity) {
                persistedService = networkServiceEntity;
            }
            if (value instanceof ServiceInstanceEntity serviceInstanceEntity) {
                persistedInstance = serviceInstanceEntity;
            }
        }
        assertNotNull(persistedService);
        assertNotNull(persistedInstance);
        assertEquals(ServiceKind.BACKEND, persistedService.getServiceKind());
        assertEquals("paper-lobby-1", persistedService.getServiceName());
        assertEquals("PAPER", persistedService.getPlatform());
        assertEquals(persistedService, persistedInstance.getService());
        assertEquals(ServiceInstanceStatus.RUNNING, persistedInstance.getStatus());
        assertEquals("127.0.0.1", persistedInstance.getHost());
        assertEquals(25565, persistedInstance.getPort());
    }

    @Test
    void refreshRunningInstanceUpdatesExistingRows() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> serviceQuery = mock(Query.class);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> instanceQuery = mock(Query.class);
        NetworkServiceEntity existingService = new NetworkServiceEntity();
        existingService.setServiceKind(ServiceKind.PROXY);
        existingService.setServiceName("velocity-edge");
        existingService.setPlatform("OLD");
        ServiceInstanceEntity existingInstance = new ServiceInstanceEntity();
        existingInstance.setService(existingService);
        existingInstance.setInstanceId("e4a9f6d6-2b90-4f9f-9807-99eb62a4a350");
        existingInstance.setStatus(ServiceInstanceStatus.STOPPED);
        existingInstance.setStoppedAt(java.time.Instant.EPOCH);

        when(registry.getServiceORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(contains("FROM NetworkServiceEntity"), org.mockito.ArgumentMatchers.eq(NetworkServiceEntity.class)))
                .thenReturn(serviceQuery);
        when(serviceQuery.setParameter(anyString(), any())).thenReturn(serviceQuery);
        when(serviceQuery.setMaxResults(anyInt())).thenReturn(serviceQuery);
        when(serviceQuery.uniqueResult()).thenReturn(existingService);
        when(session.createQuery(contains("FROM ServiceInstanceEntity"), org.mockito.ArgumentMatchers.eq(ServiceInstanceEntity.class)))
                .thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.setMaxResults(anyInt())).thenReturn(instanceQuery);
        when(instanceQuery.uniqueResult()).thenReturn(existingInstance);

        service.refreshRunningInstance(
                ServiceKind.PROXY,
                "velocity-edge",
                "VELOCITY",
                "e4a9f6d6-2b90-4f9f-9807-99eb62a4a350",
                "10.0.0.5",
                25577
        );

        verify(session, never()).persist(any());
        assertEquals("VELOCITY", existingService.getPlatform());
        assertNotNull(existingService.getLastSeenAt());
        assertEquals(ServiceInstanceStatus.RUNNING, existingInstance.getStatus());
        assertEquals("10.0.0.5", existingInstance.getHost());
        assertEquals(25577, existingInstance.getPort());
        assertNull(existingInstance.getStoppedAt());
    }

    @Test
    void markStoppedSetsInstanceStateToStopped() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        @SuppressWarnings("unchecked")
        Query<ServiceInstanceEntity> instanceQuery = mock(Query.class);
        ServiceInstanceEntity existingInstance = new ServiceInstanceEntity();
        existingInstance.setStatus(ServiceInstanceStatus.RUNNING);

        when(registry.getServiceORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(contains("FROM ServiceInstanceEntity"), org.mockito.ArgumentMatchers.eq(ServiceInstanceEntity.class)))
                .thenReturn(instanceQuery);
        when(instanceQuery.setParameter(anyString(), any())).thenReturn(instanceQuery);
        when(instanceQuery.setMaxResults(anyInt())).thenReturn(instanceQuery);
        when(instanceQuery.uniqueResult()).thenReturn(existingInstance);

        service.markStopped("e4a9f6d6-2b90-4f9f-9807-99eb62a4a350");

        assertEquals(ServiceInstanceStatus.STOPPED, existingInstance.getStatus());
        assertNotNull(existingInstance.getStoppedAt());
        assertNotNull(existingInstance.getLastSeenAt());
    }

    @Test
    void recordProbeCreatesServiceWhenMissingAndPersistsProbeRow() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        Session session = mock(Session.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        @SuppressWarnings("unchecked")
        Query<NetworkServiceEntity> serviceQuery = mock(Query.class);

        when(registry.getServiceORM()).thenReturn(ormContext);
        executeTransactionsWithSession(ormContext, session);
        when(session.createQuery(contains("FROM NetworkServiceEntity"), org.mockito.ArgumentMatchers.eq(NetworkServiceEntity.class)))
                .thenReturn(serviceQuery);
        when(serviceQuery.setParameter(anyString(), any())).thenReturn(serviceQuery);
        when(serviceQuery.setMaxResults(anyInt())).thenReturn(serviceQuery);
        when(serviceQuery.uniqueResult()).thenReturn(null);

        service.recordProbe(
                ServiceKind.BACKEND,
                "paper-lobby-1",
                "PAPER",
                "observer-instance-1",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                null,
                22L,
                null,
                null
        );

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(session, times(2)).persist(captor.capture());
        NetworkServiceEntity persistedService = null;
        ServiceProbeEntity persistedProbe = null;
        for (Object value : captor.getAllValues()) {
            if (value instanceof NetworkServiceEntity networkServiceEntity) {
                persistedService = networkServiceEntity;
            }
            if (value instanceof ServiceProbeEntity serviceProbeEntity) {
                persistedProbe = serviceProbeEntity;
            }
        }

        assertNotNull(persistedService);
        assertNotNull(persistedProbe);
        assertEquals(ServiceKind.BACKEND, persistedService.getServiceKind());
        assertEquals("paper-lobby-1", persistedService.getServiceName());
        assertEquals("PAPER", persistedService.getPlatform());
        assertEquals(persistedService, persistedProbe.getService());
        assertEquals("observer-instance-1", persistedProbe.getObserverInstanceId());
        assertEquals(ServiceProbeStatus.UP, persistedProbe.getStatus());
        assertEquals("127.0.0.1", persistedProbe.getTargetHost());
        assertEquals(25565, persistedProbe.getTargetPort());
        assertEquals(22L, persistedProbe.getLatencyMillis());
        assertNotNull(persistedProbe.getCheckedAt());
    }

    @Test
    void probeHelpersAndEffectiveHealthProvideExpectedViews() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        NetworkServiceRepository serviceRepository = mock(NetworkServiceRepository.class);
        ServiceInstanceRepository instanceRepository = mock(ServiceInstanceRepository.class);
        ServiceProbeRepository probeRepository = mock(ServiceProbeRepository.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        Instant now = Instant.now();

        NetworkServiceEntity backendA = new NetworkServiceEntity();
        backendA.setServiceKind(ServiceKind.BACKEND);
        backendA.setServiceName("backend-a");
        backendA.setPlatform("PAPER");
        backendA.setFirstSeenAt(now.minusSeconds(300));
        backendA.setLastSeenAt(now);

        NetworkServiceEntity backendB = new NetworkServiceEntity();
        backendB.setServiceKind(ServiceKind.BACKEND);
        backendB.setServiceName("backend-b");
        backendB.setPlatform("PAPER");
        backendB.setFirstSeenAt(now.minusSeconds(300));
        backendB.setLastSeenAt(now.minusSeconds(10));

        NetworkServiceEntity backendC = new NetworkServiceEntity();
        backendC.setServiceKind(ServiceKind.BACKEND);
        backendC.setServiceName("backend-c");
        backendC.setPlatform("PAPER");
        backendC.setFirstSeenAt(now.minusSeconds(300));
        backendC.setLastSeenAt(now);

        ServiceInstanceEntity runningA = new ServiceInstanceEntity();
        runningA.setService(backendA);
        runningA.setStatus(ServiceInstanceStatus.RUNNING);
        runningA.setLastSeenAt(now.minusSeconds(2));

        ServiceInstanceEntity runningC = new ServiceInstanceEntity();
        runningC.setService(backendC);
        runningC.setStatus(ServiceInstanceStatus.RUNNING);
        runningC.setLastSeenAt(now.minusSeconds(2));

        ServiceProbeEntity probeA = new ServiceProbeEntity();
        probeA.setService(backendA);
        probeA.setObserverInstanceId("observer-1");
        probeA.setStatus(ServiceProbeStatus.UP);
        probeA.setCheckedAt(now.minusSeconds(1));

        ServiceProbeEntity probeB = new ServiceProbeEntity();
        probeB.setService(backendB);
        probeB.setObserverInstanceId("observer-1");
        probeB.setStatus(ServiceProbeStatus.TIMEOUT);
        probeB.setCheckedAt(now.minusSeconds(1));

        when(registry.getNetworkServiceRepository()).thenReturn(serviceRepository);
        when(registry.getServiceInstanceRepository()).thenReturn(instanceRepository);
        when(registry.getServiceProbeRepository()).thenReturn(probeRepository);
        when(serviceRepository.findAllOrdered()).thenReturn(List.of(backendA, backendB, backendC));
        when(instanceRepository.findAllOrdered()).thenReturn(List.of(runningA, runningC));
        when(instanceRepository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, "10.0.0.5", 25565))
                .thenReturn(Optional.of(runningA));
        when(probeRepository.findMostRecentByService(ServiceKind.BACKEND, "backend-a")).thenReturn(Optional.of(probeA));
        when(probeRepository.findMostRecentByService(ServiceKind.BACKEND, "backend-b")).thenReturn(Optional.of(probeB));
        when(probeRepository.findMostRecentByService(ServiceKind.BACKEND, "backend-c")).thenReturn(Optional.empty());
        when(probeRepository.findRecentByService(ServiceKind.BACKEND, "backend-a", 1)).thenReturn(List.of(probeA));
        when(probeRepository.findByObserverInstanceId("observer-1", 1)).thenReturn(List.of(probeA, probeB));
        when(probeRepository.countByStatus(any())).thenReturn(1L);
        when(probeRepository.deleteCheckedBefore(any(Instant.class), org.mockito.ArgumentMatchers.eq(2)))
                .thenReturn(2, 1);

        assertTrue(service.findMostRecentProbe(ServiceKind.BACKEND, "backend-a").isPresent());
        assertEquals(1, service.listRecentProbes(ServiceKind.BACKEND, "backend-a", 1).size());
        assertEquals(2, service.listRecentProbesByObserver("observer-1", 1).size());
        assertEquals(1L, service.countProbesByStatus().get(ServiceProbeStatus.UP));
        assertTrue(service.findMostRecentRunningInstanceByEndpoint(ServiceKind.BACKEND, "10.0.0.5", 25565).isPresent());
        assertEquals(3, service.purgeProbesOlderThan(Duration.ofHours(24), 2));

        List<ServiceRegistryService.ServiceEffectiveHealthView> effective =
                service.listServiceEffectiveHealth(Duration.ofSeconds(10), Duration.ofSeconds(10));
        assertEquals(3, effective.size());

        ServiceRegistryService.EffectiveServiceHealthStatus statusA = null;
        ServiceRegistryService.EffectiveServiceHealthStatus statusB = null;
        ServiceRegistryService.EffectiveServiceHealthStatus statusC = null;
        for (ServiceRegistryService.ServiceEffectiveHealthView view : effective) {
            if ("backend-a".equals(view.serviceName())) {
                statusA = view.effectiveStatus();
            } else if ("backend-b".equals(view.serviceName())) {
                statusB = view.effectiveStatus();
            } else if ("backend-c".equals(view.serviceName())) {
                statusC = view.effectiveStatus();
            }
        }
        assertEquals(ServiceRegistryService.EffectiveServiceHealthStatus.HEALTHY, statusA);
        assertEquals(ServiceRegistryService.EffectiveServiceHealthStatus.UNREACHABLE, statusB);
        assertEquals(ServiceRegistryService.EffectiveServiceHealthStatus.UNKNOWN, statusC);

        Optional<ServiceRegistryService.ServiceEffectiveHealthView> backendAView = service.findServiceEffectiveHealth(
                ServiceKind.BACKEND,
                "backend-a",
                Duration.ofSeconds(10),
                Duration.ofSeconds(10)
        );
        assertTrue(backendAView.isPresent());
        assertEquals(ServiceRegistryService.EffectiveServiceHealthStatus.HEALTHY, backendAView.get().effectiveStatus());
    }

    @Test
    void findMostRecentRunningInstanceByEndpointWithinHonorsFreshnessWindow() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceInstanceRepository instanceRepository = mock(ServiceInstanceRepository.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);

        Instant now = Instant.now();
        NetworkServiceEntity backend = new NetworkServiceEntity();
        backend.setServiceKind(ServiceKind.BACKEND);
        backend.setServiceName("backend-a");
        backend.setPlatform("PAPER");

        ServiceInstanceEntity running = new ServiceInstanceEntity();
        running.setService(backend);
        running.setInstanceId("inst-1");
        running.setStatus(ServiceInstanceStatus.RUNNING);
        running.setHost("10.0.0.5");
        running.setPort(25565);
        running.setLastSeenAt(now.minusSeconds(5));

        when(registry.getServiceInstanceRepository()).thenReturn(instanceRepository);
        when(instanceRepository.findMostRecentRunningByEndpoint(ServiceKind.BACKEND, "10.0.0.5", 25565))
                .thenReturn(Optional.of(running));

        assertTrue(
                service.findMostRecentRunningInstanceByEndpointWithin(
                        ServiceKind.BACKEND,
                        "10.0.0.5",
                        25565,
                        Duration.ofSeconds(10)
                ).isPresent()
        );
        assertTrue(
                service.findMostRecentRunningInstanceByEndpointWithin(
                        ServiceKind.BACKEND,
                        "10.0.0.5",
                        25565,
                        Duration.ofSeconds(2)
                ).isEmpty()
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findMostRecentRunningInstanceByEndpointWithin(
                        ServiceKind.BACKEND,
                        "10.0.0.5",
                        25565,
                        Duration.ofSeconds(-1)
                )
        );
    }

    @Test
    void recordProbeValidatesRequiredFieldsAndLogsSanitizedErrors() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);

        service.recordProbe(
                null,
                "paper",
                "PAPER",
                "observer",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                null,
                1L,
                null,
                null
        );
        service.recordProbe(
                ServiceKind.BACKEND,
                " ",
                "PAPER",
                "observer",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                null,
                1L,
                null,
                null
        );
        verify(logger).warn("recordProbe called with null serviceKind or status.");
        verify(logger).warn("recordProbe called with invalid required service metadata.");

        when(registry.getServiceORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("probe tx failed")).when(ormContext).runInTransaction(any());

        service.recordProbe(
                ServiceKind.BACKEND,
                "paper",
                "PAPER",
                "observer\nid",
                ServiceProbeStatus.ERROR,
                "127.0.0.1",
                25565,
                null,
                1L,
                "network\nerror",
                "connection refused"
        );
        verify(logger).error(contains("BACKEND:paper"), any(RuntimeException.class));
    }

    @Test
    void disabledFeatureSkipsRegistryWrites() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, false);

        service.refreshRunningInstance(ServiceKind.BACKEND, "paper", "PAPER", "id", null, null);
        service.markStopped("id");
        service.recordProbe(
                ServiceKind.BACKEND,
                "paper",
                "PAPER",
                "observer-id",
                ServiceProbeStatus.UP,
                "127.0.0.1",
                25565,
                null,
                10L,
                null,
                null
        );

        verify(registry, never()).getServiceORM();
    }

    @Test
    void helperReadMethodsProjectUsefulServiceInformation() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        NetworkServiceRepository serviceRepository = mock(NetworkServiceRepository.class);
        ServiceInstanceRepository instanceRepository = mock(ServiceInstanceRepository.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        Instant now = Instant.now();

        NetworkServiceEntity networkService = new NetworkServiceEntity();
        networkService.setId(1L);
        networkService.setServiceKind(ServiceKind.BACKEND);
        networkService.setServiceName("paper-lobby-1");
        networkService.setPlatform("PAPER");
        networkService.setFirstSeenAt(now.minusSeconds(120));
        networkService.setLastSeenAt(now);

        ServiceInstanceEntity running = new ServiceInstanceEntity();
        running.setService(networkService);
        running.setInstanceId("inst-running");
        running.setStatus(ServiceInstanceStatus.RUNNING);
        running.setHost("10.0.0.5");
        running.setPort(25565);
        running.setStartedAt(now.minusSeconds(90));
        running.setLastSeenAt(now.minusSeconds(3));

        ServiceInstanceEntity stopped = new ServiceInstanceEntity();
        stopped.setService(networkService);
        stopped.setInstanceId("inst-stopped");
        stopped.setStatus(ServiceInstanceStatus.STOPPED);
        stopped.setHost("10.0.0.6");
        stopped.setPort(25566);
        stopped.setStartedAt(now.minusSeconds(200));
        stopped.setLastSeenAt(now.minusSeconds(70));
        stopped.setStoppedAt(now.minusSeconds(60));

        when(registry.getNetworkServiceRepository()).thenReturn(serviceRepository);
        when(registry.getServiceInstanceRepository()).thenReturn(instanceRepository);
        when(serviceRepository.findAllOrdered()).thenReturn(List.of(networkService));
        when(serviceRepository.findByKind(ServiceKind.BACKEND)).thenReturn(List.of(networkService));
        when(serviceRepository.findByKindAndName(ServiceKind.BACKEND, "paper-lobby-1"))
                .thenReturn(Optional.of(networkService));
        when(instanceRepository.findAllOrdered()).thenReturn(List.of(running, stopped));
        when(instanceRepository.findByStatus(ServiceInstanceStatus.RUNNING)).thenReturn(List.of(running));
        when(instanceRepository.findByService(ServiceKind.BACKEND, "paper-lobby-1")).thenReturn(List.of(running, stopped));
        when(instanceRepository.findByInstanceId("inst-running")).thenReturn(Optional.of(running));
        when(instanceRepository.findMostRecentByServiceAndStatus(
                ServiceKind.BACKEND,
                "paper-lobby-1",
                ServiceInstanceStatus.RUNNING
        )).thenReturn(Optional.of(running));

        assertEquals(1, service.listServices().size());
        assertEquals(1, service.listServices(ServiceKind.BACKEND).size());
        assertTrue(service.findService(ServiceKind.BACKEND, "paper-lobby-1").isPresent());
        assertEquals(2, service.listInstances(ServiceKind.BACKEND, "paper-lobby-1").size());
        assertTrue(service.findInstance("inst-running").isPresent());
        assertTrue(service.resolveEndpoint(ServiceKind.BACKEND, "paper-lobby-1").isPresent());
        assertEquals("10.0.0.5:25565", service.resolveEndpoint(ServiceKind.BACKEND, "paper-lobby-1").orElseThrow());
        assertTrue(service.isInstanceActiveWithin("inst-running", Duration.ofSeconds(10)));
        assertEquals(1, service.listServiceHealth().size());
        assertEquals(1L, service.listServiceHealth().getFirst().runningInstanceCount());

        Map<ServiceKind, Long> counts = service.countRunningInstancesByKind();
        assertEquals(1L, counts.get(ServiceKind.BACKEND));
    }

    @Test
    void helperReadMethodsRespectStalenessAndInputValidation() {
        DataRegistry registry = mock(DataRegistry.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceInstanceRepository instanceRepository = mock(ServiceInstanceRepository.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);
        Instant now = Instant.now();

        NetworkServiceEntity networkService = new NetworkServiceEntity();
        networkService.setServiceKind(ServiceKind.PROXY);
        networkService.setServiceName("velocity-edge");
        networkService.setPlatform("VELOCITY");

        ServiceInstanceEntity fresh = new ServiceInstanceEntity();
        fresh.setService(networkService);
        fresh.setInstanceId("fresh");
        fresh.setStatus(ServiceInstanceStatus.RUNNING);
        fresh.setLastSeenAt(now.minusSeconds(2));

        ServiceInstanceEntity stale = new ServiceInstanceEntity();
        stale.setService(networkService);
        stale.setInstanceId("stale");
        stale.setStatus(ServiceInstanceStatus.RUNNING);
        stale.setLastSeenAt(now.minusSeconds(120));

        when(registry.getServiceInstanceRepository()).thenReturn(instanceRepository);
        when(instanceRepository.findByInstanceId("fresh")).thenReturn(Optional.of(fresh));
        when(instanceRepository.findRunningSeenBefore(any(Instant.class))).thenReturn(List.of(stale));

        assertEquals(1, service.listStaleRunningInstances(Duration.ofSeconds(30)).size());
        assertEquals("stale", service.listStaleRunningInstances(Duration.ofSeconds(30)).getFirst().instanceId());
        assertThrows(IllegalArgumentException.class, () -> service.isInstanceActiveWithin("fresh", Duration.ofSeconds(-1)));
        assertThrows(IllegalArgumentException.class, () -> service.listStaleRunningInstances(Duration.ofSeconds(-1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.listServiceEffectiveHealth(Duration.ofSeconds(-1), Duration.ofSeconds(10))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.findServiceEffectiveHealth(
                        ServiceKind.PROXY,
                        "velocity-edge",
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(-1)
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.purgeProbesOlderThan(Duration.ofSeconds(-1), 100)
        );
    }

    @Test
    void refreshRunningInstanceValidatesRequiredFieldsAndLogsSanitizedErrors() {
        DataRegistry registry = mock(DataRegistry.class);
        ORMContext ormContext = mock(ORMContext.class);
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        ServiceRegistryService service = new ServiceRegistryService(registry, logger, true);

        service.refreshRunningInstance(null, "paper", "PAPER", "id", null, null);
        service.refreshRunningInstance(ServiceKind.BACKEND, " ", "PAPER", "id", null, null);
        verify(logger).warn("refreshRunningInstance called with null serviceKind.");
        verify(logger).warn("refreshRunningInstance called with invalid required service metadata.");

        when(registry.getServiceORM()).thenReturn(ormContext);
        doThrow(new RuntimeException("tx failed")).when(ormContext).runInTransaction(any());

        service.refreshRunningInstance(
                ServiceKind.BACKEND,
                "paper",
                "PAPER",
                "id\nvalue",
                null,
                null
        );
        verify(logger).error(contains("id_value"), any(RuntimeException.class));
    }
}
