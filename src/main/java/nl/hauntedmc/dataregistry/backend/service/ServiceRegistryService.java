package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeStatus;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class ServiceRegistryService {

    private static final int SERVICE_NAME_MAX_LENGTH = 96;
    private static final int PLATFORM_MAX_LENGTH = 32;
    private static final int HOST_MAX_LENGTH = 255;
    private static final int INSTANCE_ID_MAX_LENGTH = 36;
    private static final int PROBE_ERROR_CODE_MAX_LENGTH = 64;
    private static final int PROBE_ERROR_DETAIL_MAX_LENGTH = 255;

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final boolean featureEnabled;

    /**
     * Creates a feature-aware service registry facade for both writes (heartbeats/state updates) and reads.
     */
    public ServiceRegistryService(DataRegistry dataRegistry, ILoggerAdapter logger, boolean featureEnabled) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.featureEnabled = featureEnabled;
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    /**
     * Upserts/refreshes a running service instance heartbeat.
     */
    public void refreshRunningInstance(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            String instanceId,
            String host,
            Integer port
    ) {
        if (!featureEnabled) {
            return;
        }
        if (serviceKind == null) {
            logger.warn("refreshRunningInstance called with null serviceKind.");
            return;
        }

        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        String normalizedPlatform = Sanitization.trimToLengthOrNull(platform, PLATFORM_MAX_LENGTH);
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, INSTANCE_ID_MAX_LENGTH);
        if (normalizedServiceName == null || normalizedPlatform == null || normalizedInstanceId == null) {
            logger.warn("refreshRunningInstance called with invalid required service metadata.");
            return;
        }
        String normalizedHost = Sanitization.trimToLengthOrNull(host, HOST_MAX_LENGTH);

        try {
            dataRegistry.getServiceORM().runInTransaction(session -> {
                Instant now = Instant.now();

                NetworkServiceEntity service = session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceKind = :kind AND s.serviceName = :name",
                                NetworkServiceEntity.class
                        )
                        .setParameter("kind", serviceKind)
                        .setParameter("name", normalizedServiceName)
                        .setMaxResults(1)
                        .uniqueResult();

                if (service == null) {
                    service = new NetworkServiceEntity();
                    service.setServiceKind(serviceKind);
                    service.setServiceName(normalizedServiceName);
                    service.setPlatform(normalizedPlatform);
                    service.setFirstSeenAt(now);
                    service.setLastSeenAt(now);
                    session.persist(service);
                } else {
                    service.setPlatform(normalizedPlatform);
                    service.setLastSeenAt(now);
                }

                ServiceInstanceEntity instance = session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i WHERE i.instanceId = :instanceId",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("instanceId", normalizedInstanceId)
                        .setMaxResults(1)
                        .uniqueResult();

                if (instance == null) {
                    instance = new ServiceInstanceEntity();
                    instance.setService(service);
                    instance.setInstanceId(normalizedInstanceId);
                    instance.setStatus(ServiceInstanceStatus.RUNNING);
                    instance.setStartedAt(now);
                    instance.setLastSeenAt(now);
                    instance.setHost(normalizedHost);
                    instance.setPort(normalizePort(port));
                    session.persist(instance);
                    return null;
                }

                instance.setStatus(ServiceInstanceStatus.RUNNING);
                instance.setLastSeenAt(now);
                instance.setStoppedAt(null);
                instance.setHost(normalizedHost);
                instance.setPort(normalizePort(port));
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to refresh service instance '" + Sanitization.safeForLog(normalizedInstanceId) + "'.",
                    exception
            );
        }
    }

    /**
     * Marks a known service instance as stopped.
     */
    public void markStopped(String instanceId) {
        if (!featureEnabled) {
            return;
        }
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, INSTANCE_ID_MAX_LENGTH);
        if (normalizedInstanceId == null) {
            return;
        }
        try {
            dataRegistry.getServiceORM().runInTransaction(session -> {
                ServiceInstanceEntity instance = session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i WHERE i.instanceId = :instanceId",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("instanceId", normalizedInstanceId)
                        .setMaxResults(1)
                        .uniqueResult();
                if (instance == null) {
                    return null;
                }
                Instant now = Instant.now();
                instance.setStatus(ServiceInstanceStatus.STOPPED);
                instance.setStoppedAt(now);
                instance.setLastSeenAt(now);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to mark service instance '" + Sanitization.safeForLog(normalizedInstanceId) + "' as stopped.",
                    exception
            );
        }
    }

    /**
     * Appends a proxy-side health probe result for one logical service.
     */
    public void recordProbe(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            String observerInstanceId,
            ServiceProbeStatus status,
            String targetHost,
            Integer targetPort,
            String targetInstanceId,
            Long latencyMillis,
            String errorCode,
            String errorDetail
    ) {
        if (!featureEnabled) {
            return;
        }
        if (serviceKind == null || status == null) {
            logger.warn("recordProbe called with null serviceKind or status.");
            return;
        }

        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        String normalizedPlatform = Sanitization.trimToLengthOrNull(platform, PLATFORM_MAX_LENGTH);
        String normalizedObserverInstanceId = Sanitization.trimToLengthOrNull(observerInstanceId, INSTANCE_ID_MAX_LENGTH);
        if (normalizedServiceName == null || normalizedPlatform == null || normalizedObserverInstanceId == null) {
            logger.warn("recordProbe called with invalid required service metadata.");
            return;
        }
        String normalizedTargetHost = Sanitization.trimToLengthOrNull(targetHost, HOST_MAX_LENGTH);
        String normalizedTargetInstanceId = Sanitization.trimToLengthOrNull(targetInstanceId, INSTANCE_ID_MAX_LENGTH);
        String normalizedErrorCode = Sanitization.trimToLengthOrNull(errorCode, PROBE_ERROR_CODE_MAX_LENGTH);
        String normalizedErrorDetail = Sanitization.trimToLengthOrNull(errorDetail, PROBE_ERROR_DETAIL_MAX_LENGTH);
        Integer normalizedPort = normalizePort(targetPort);
        Long normalizedLatencyMillis = normalizeLatencyMillis(latencyMillis);

        try {
            dataRegistry.getServiceORM().runInTransaction(session -> {
                Instant now = Instant.now();

                NetworkServiceEntity service = session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceKind = :kind AND s.serviceName = :name",
                                NetworkServiceEntity.class
                        )
                        .setParameter("kind", serviceKind)
                        .setParameter("name", normalizedServiceName)
                        .setMaxResults(1)
                        .uniqueResult();

                if (service == null) {
                    service = new NetworkServiceEntity();
                    service.setServiceKind(serviceKind);
                    service.setServiceName(normalizedServiceName);
                    service.setPlatform(normalizedPlatform);
                    service.setFirstSeenAt(now);
                    service.setLastSeenAt(now);
                    session.persist(service);
                } else {
                    service.setPlatform(normalizedPlatform);
                    service.setLastSeenAt(now);
                }

                ServiceProbeEntity probe = new ServiceProbeEntity();
                probe.setService(service);
                probe.setObserverInstanceId(normalizedObserverInstanceId);
                probe.setStatus(status);
                probe.setTargetHost(normalizedTargetHost);
                probe.setTargetPort(normalizedPort);
                probe.setTargetInstanceId(normalizedTargetInstanceId);
                probe.setLatencyMillis(normalizedLatencyMillis);
                probe.setErrorCode(normalizedErrorCode);
                probe.setErrorDetail(normalizedErrorDetail);
                probe.setCheckedAt(now);
                session.persist(probe);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to record service probe for '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
        }
    }

    /**
     * Returns all registered logical services as immutable API views.
     */
    public List<ServiceView> listServices() {
        if (!featureEnabled) {
            return List.of();
        }
        try {
            return dataRegistry.getNetworkServiceRepository()
                    .findAllOrdered()
                    .stream()
                    .map(ServiceRegistryService::toServiceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error("Failed to list network services.", exception);
            return List.of();
        }
    }

    /**
     * Returns all registered logical services for a specific kind.
     */
    public List<ServiceView> listServices(ServiceKind serviceKind) {
        if (!featureEnabled || serviceKind == null) {
            return List.of();
        }
        try {
            return dataRegistry.getNetworkServiceRepository()
                    .findByKind(serviceKind)
                    .stream()
                    .map(ServiceRegistryService::toServiceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error("Failed to list network services for kind=" + Sanitization.safeForLog(serviceKind.name()) + ".", exception);
            return List.of();
        }
    }

    /**
     * Finds a logical service by kind + name.
     */
    public Optional<ServiceView> findService(ServiceKind serviceKind, String serviceName) {
        if (!featureEnabled || serviceKind == null) {
            return Optional.empty();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return Optional.empty();
        }
        try {
            return dataRegistry.getNetworkServiceRepository()
                    .findByKindAndName(serviceKind, normalizedServiceName)
                    .map(ServiceRegistryService::toServiceView);
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to find network service '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Returns all known instances across all service kinds.
     */
    public List<ServiceInstanceView> listInstances() {
        if (!featureEnabled) {
            return List.of();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findAllOrdered()
                    .stream()
                    .map(ServiceRegistryService::toInstanceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error("Failed to list service instances.", exception);
            return List.of();
        }
    }

    /**
     * Returns all currently running service instances.
     */
    public List<ServiceInstanceView> listRunningInstances() {
        if (!featureEnabled) {
            return List.of();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findByStatus(ServiceInstanceStatus.RUNNING)
                    .stream()
                    .map(ServiceRegistryService::toInstanceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error("Failed to list running service instances.", exception);
            return List.of();
        }
    }

    /**
     * Returns all known instances for a logical service.
     */
    public List<ServiceInstanceView> listInstances(ServiceKind serviceKind, String serviceName) {
        if (!featureEnabled || serviceKind == null) {
            return List.of();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return List.of();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findByService(serviceKind, normalizedServiceName)
                    .stream()
                    .map(ServiceRegistryService::toInstanceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to list service instances for '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
            return List.of();
        }
    }

    /**
     * Finds a specific instance by its stable runtime ID.
     */
    public Optional<ServiceInstanceView> findInstance(String instanceId) {
        if (!featureEnabled) {
            return Optional.empty();
        }
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, INSTANCE_ID_MAX_LENGTH);
        if (normalizedInstanceId == null) {
            return Optional.empty();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findByInstanceId(normalizedInstanceId)
                    .map(ServiceRegistryService::toInstanceView);
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to find service instance '" + Sanitization.safeForLog(normalizedInstanceId) + "'.",
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Finds the most recently seen running instance for a logical service.
     */
    public Optional<ServiceInstanceView> findMostRecentRunningInstance(ServiceKind serviceKind, String serviceName) {
        if (!featureEnabled || serviceKind == null) {
            return Optional.empty();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return Optional.empty();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findMostRecentByServiceAndStatus(serviceKind, normalizedServiceName, ServiceInstanceStatus.RUNNING)
                    .map(ServiceRegistryService::toInstanceView);
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to find most recent running service instance for '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Finds the most recently seen running instance for a kind + endpoint.
     */
    public Optional<ServiceInstanceView> findMostRecentRunningInstanceByEndpoint(
            ServiceKind serviceKind,
            String host,
            Integer port
    ) {
        if (!featureEnabled || serviceKind == null) {
            return Optional.empty();
        }
        String normalizedHost = Sanitization.trimToLengthOrNull(host, HOST_MAX_LENGTH);
        Integer normalizedPort = normalizePort(port);
        if (normalizedHost == null || normalizedPort == null) {
            return Optional.empty();
        }
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findMostRecentRunningByEndpoint(serviceKind, normalizedHost, normalizedPort)
                    .map(ServiceRegistryService::toInstanceView);
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to find running service instance by endpoint '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedHost + ":" + normalizedPort) + "'.",
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Finds the most recently seen running instance for a kind + endpoint that is fresh within {@code maxAge}.
     */
    public Optional<ServiceInstanceView> findMostRecentRunningInstanceByEndpointWithin(
            ServiceKind serviceKind,
            String host,
            Integer port,
            Duration maxAge
    ) {
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must not be negative.");
        }
        Instant cutoff = Instant.now().minus(maxAge);
        return findMostRecentRunningInstanceByEndpoint(serviceKind, host, port)
                .filter(instance -> instance.lastSeenAt() != null && !instance.lastSeenAt().isBefore(cutoff));
    }

    /**
     * Resolves the freshest running endpoint for a logical service as {@code host:port}.
     */
    public Optional<String> resolveEndpoint(ServiceKind serviceKind, String serviceName) {
        return findMostRecentRunningInstance(serviceKind, serviceName)
                .flatMap(ServiceInstanceView::endpoint);
    }

    /**
     * Returns whether a running instance has been seen within the given freshness window.
     */
    public boolean isInstanceActiveWithin(String instanceId, Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge must not be null");
        if (maxAge.isNegative()) {
            throw new IllegalArgumentException("maxAge must not be negative.");
        }
        if (!featureEnabled) {
            return false;
        }
        Instant cutoff = Instant.now().minus(maxAge);
        return findInstance(instanceId)
                .filter(instance -> instance.status() == ServiceInstanceStatus.RUNNING)
                .map(ServiceInstanceView::lastSeenAt)
                .filter(lastSeenAt -> lastSeenAt != null && !lastSeenAt.isBefore(cutoff))
                .isPresent();
    }

    /**
     * Returns running instances that are stale based on heartbeat age.
     */
    public List<ServiceInstanceView> listStaleRunningInstances(Duration staleAfter) {
        Objects.requireNonNull(staleAfter, "staleAfter must not be null");
        if (staleAfter.isNegative()) {
            throw new IllegalArgumentException("staleAfter must not be negative.");
        }
        if (!featureEnabled) {
            return List.of();
        }
        Instant cutoff = Instant.now().minus(staleAfter);
        try {
            return dataRegistry.getServiceInstanceRepository()
                    .findRunningSeenBefore(cutoff)
                    .stream()
                    .map(ServiceRegistryService::toInstanceView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error("Failed to list stale running service instances.", exception);
            return List.of();
        }
    }

    /**
     * Returns the most recent probe for one logical service.
     */
    public Optional<ServiceProbeView> findMostRecentProbe(ServiceKind serviceKind, String serviceName) {
        if (!featureEnabled || serviceKind == null) {
            return Optional.empty();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return Optional.empty();
        }
        try {
            return dataRegistry.getServiceProbeRepository()
                    .findMostRecentByService(serviceKind, normalizedServiceName)
                    .map(ServiceRegistryService::toProbeView);
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to find most recent probe for '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
            return Optional.empty();
        }
    }

    /**
     * Returns recent probes for one logical service, newest first.
     */
    public List<ServiceProbeView> listRecentProbes(ServiceKind serviceKind, String serviceName, int limit) {
        if (!featureEnabled || serviceKind == null) {
            return List.of();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return List.of();
        }
        try {
            return dataRegistry.getServiceProbeRepository()
                    .findRecentByService(serviceKind, normalizedServiceName, Math.max(1, limit))
                    .stream()
                    .map(ServiceRegistryService::toProbeView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to list probes for '" +
                            Sanitization.safeForLog(serviceKind.name() + ":" + normalizedServiceName) + "'.",
                    exception
            );
            return List.of();
        }
    }

    /**
     * Returns recent probes emitted by one observer instance.
     */
    public List<ServiceProbeView> listRecentProbesByObserver(String observerInstanceId, int limit) {
        if (!featureEnabled) {
            return List.of();
        }
        String normalizedObserverInstanceId =
                Sanitization.trimToLengthOrNull(observerInstanceId, INSTANCE_ID_MAX_LENGTH);
        if (normalizedObserverInstanceId == null) {
            return List.of();
        }
        try {
            return dataRegistry.getServiceProbeRepository()
                    .findByObserverInstanceId(normalizedObserverInstanceId, Math.max(1, limit))
                    .stream()
                    .map(ServiceRegistryService::toProbeView)
                    .toList();
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to list probes by observer instance '" +
                            Sanitization.safeForLog(normalizedObserverInstanceId) + "'.",
                    exception
            );
            return List.of();
        }
    }

    /**
     * Deletes stale probes older than {@code retentionWindow} in bounded batches.
     */
    public int purgeProbesOlderThan(Duration retentionWindow, int batchSize) {
        Objects.requireNonNull(retentionWindow, "retentionWindow must not be null");
        if (retentionWindow.isNegative()) {
            throw new IllegalArgumentException("retentionWindow must not be negative.");
        }
        if (!featureEnabled) {
            return 0;
        }
        Instant cutoff = Instant.now().minus(retentionWindow);
        int boundedBatchSize = Math.max(1, batchSize);
        int deleted = 0;
        try {
            while (true) {
                int removed = dataRegistry.getServiceProbeRepository().deleteCheckedBefore(cutoff, boundedBatchSize);
                deleted += removed;
                if (removed < boundedBatchSize) {
                    break;
                }
            }
            return deleted;
        } catch (RuntimeException exception) {
            logger.error("Failed to purge stale service probes.", exception);
            return deleted;
        }
    }

    /**
     * Returns counts of probe rows grouped by status.
     */
    public Map<ServiceProbeStatus, Long> countProbesByStatus() {
        EnumMap<ServiceProbeStatus, Long> counts = new EnumMap<>(ServiceProbeStatus.class);
        for (ServiceProbeStatus probeStatus : ServiceProbeStatus.values()) {
            counts.put(probeStatus, 0L);
        }
        if (!featureEnabled) {
            return Map.copyOf(counts);
        }
        for (ServiceProbeStatus probeStatus : ServiceProbeStatus.values()) {
            try {
                counts.put(probeStatus, dataRegistry.getServiceProbeRepository().countByStatus(probeStatus));
            } catch (RuntimeException exception) {
                logger.error(
                        "Failed to count probes for status '" + Sanitization.safeForLog(probeStatus.name()) + "'.",
                        exception
                );
                counts.put(probeStatus, 0L);
            }
        }
        return Map.copyOf(counts);
    }

    /**
     * Returns count of running instances grouped by service kind.
     */
    public Map<ServiceKind, Long> countRunningInstancesByKind() {
        EnumMap<ServiceKind, Long> counts = new EnumMap<>(ServiceKind.class);
        for (ServiceKind serviceKind : ServiceKind.values()) {
            counts.put(serviceKind, 0L);
        }
        if (!featureEnabled) {
            return Map.copyOf(counts);
        }
        for (ServiceInstanceView instanceView : listRunningInstances()) {
            ServiceKind serviceKind = instanceView.serviceKind();
            if (serviceKind != null) {
                counts.merge(serviceKind, 1L, Long::sum);
            }
        }
        return Map.copyOf(counts);
    }

    /**
     * Returns aggregated per-service health information including instance counts.
     */
    public List<ServiceHealthView> listServiceHealth() {
        if (!featureEnabled) {
            return List.of();
        }

        List<ServiceView> services = listServices();
        List<ServiceInstanceView> instances = listInstances();
        Map<ServiceKey, MutableHealth> healthByService = new LinkedHashMap<>();

        for (ServiceView service : services) {
            ServiceKey key = new ServiceKey(service.serviceKind(), service.serviceName());
            healthByService.put(key, new MutableHealth(service.platform(), service.firstSeenAt(), service.lastSeenAt()));
        }

        for (ServiceInstanceView instance : instances) {
            if (instance.serviceKind() == null || instance.serviceName() == null) {
                continue;
            }
            ServiceKey key = new ServiceKey(instance.serviceKind(), instance.serviceName());
            MutableHealth health = healthByService.computeIfAbsent(
                    key,
                    ignored -> new MutableHealth(instance.platform(), null, instance.lastSeenAt())
            );
            health.totalInstanceCount++;
            if (instance.status() == ServiceInstanceStatus.RUNNING) {
                health.runningInstanceCount++;
                if (health.latestRunningSeenAt == null || isAfter(instance.lastSeenAt(), health.latestRunningSeenAt)) {
                    health.latestRunningSeenAt = instance.lastSeenAt();
                }
            }
            if (isAfter(instance.lastSeenAt(), health.lastSeenAt)) {
                health.lastSeenAt = instance.lastSeenAt();
            }
        }

        List<ServiceHealthView> result = new ArrayList<>(healthByService.size());
        for (Map.Entry<ServiceKey, MutableHealth> entry : healthByService.entrySet()) {
            ServiceKey key = entry.getKey();
            MutableHealth health = entry.getValue();
            result.add(new ServiceHealthView(
                    key.serviceKind,
                    key.serviceName,
                    health.platform,
                    health.firstSeenAt,
                    health.lastSeenAt,
                    health.latestRunningSeenAt,
                    health.totalInstanceCount,
                    health.runningInstanceCount
            ));
        }
        result.sort(Comparator
                .comparing((ServiceHealthView healthView) -> healthView.serviceKind().name())
                .thenComparing(ServiceHealthView::serviceName));
        return result;
    }

    /**
     * Returns effective health per service by combining heartbeat freshness and probe freshness.
     */
    public List<ServiceEffectiveHealthView> listServiceEffectiveHealth(
            Duration heartbeatFreshnessWindow,
            Duration probeFreshnessWindow
    ) {
        Objects.requireNonNull(heartbeatFreshnessWindow, "heartbeatFreshnessWindow must not be null");
        Objects.requireNonNull(probeFreshnessWindow, "probeFreshnessWindow must not be null");
        if (heartbeatFreshnessWindow.isNegative()) {
            throw new IllegalArgumentException("heartbeatFreshnessWindow must not be negative.");
        }
        if (probeFreshnessWindow.isNegative()) {
            throw new IllegalArgumentException("probeFreshnessWindow must not be negative.");
        }
        if (!featureEnabled) {
            return List.of();
        }

        Instant now = Instant.now();
        Instant heartbeatCutoff = now.minus(heartbeatFreshnessWindow);
        Instant probeCutoff = now.minus(probeFreshnessWindow);
        List<ServiceHealthView> baseHealth = listServiceHealth();
        List<ServiceEffectiveHealthView> result = new ArrayList<>(baseHealth.size());

        for (ServiceHealthView healthView : baseHealth) {
            Optional<ServiceProbeView> latestProbe =
                    findMostRecentProbe(healthView.serviceKind(), healthView.serviceName());
            result.add(toEffectiveHealthView(healthView, latestProbe.orElse(null), heartbeatCutoff, probeCutoff));
        }

        result.sort(Comparator
                .comparing((ServiceEffectiveHealthView healthView) -> healthView.serviceKind().name())
                .thenComparing(ServiceEffectiveHealthView::serviceName));
        return result;
    }

    /**
     * Returns effective health for one logical service by combining heartbeats and probes.
     */
    public Optional<ServiceEffectiveHealthView> findServiceEffectiveHealth(
            ServiceKind serviceKind,
            String serviceName,
            Duration heartbeatFreshnessWindow,
            Duration probeFreshnessWindow
    ) {
        Objects.requireNonNull(heartbeatFreshnessWindow, "heartbeatFreshnessWindow must not be null");
        Objects.requireNonNull(probeFreshnessWindow, "probeFreshnessWindow must not be null");
        if (heartbeatFreshnessWindow.isNegative()) {
            throw new IllegalArgumentException("heartbeatFreshnessWindow must not be negative.");
        }
        if (probeFreshnessWindow.isNegative()) {
            throw new IllegalArgumentException("probeFreshnessWindow must not be negative.");
        }
        if (!featureEnabled || serviceKind == null) {
            return Optional.empty();
        }
        String normalizedServiceName = Sanitization.trimToLengthOrNull(serviceName, SERVICE_NAME_MAX_LENGTH);
        if (normalizedServiceName == null) {
            return Optional.empty();
        }

        Instant now = Instant.now();
        Instant heartbeatCutoff = now.minus(heartbeatFreshnessWindow);
        Instant probeCutoff = now.minus(probeFreshnessWindow);
        return listServiceHealth().stream()
                .filter(view -> view.serviceKind() == serviceKind && normalizedServiceName.equals(view.serviceName()))
                .findFirst()
                .map(healthView -> toEffectiveHealthView(
                        healthView,
                        findMostRecentProbe(serviceKind, normalizedServiceName).orElse(null),
                        heartbeatCutoff,
                        probeCutoff
                ));
    }

    private static Integer normalizePort(Integer port) {
        if (port == null) {
            return null;
        }
        return port >= 0 && port <= 65535 ? port : null;
    }

    private static Long normalizeLatencyMillis(Long latencyMillis) {
        if (latencyMillis == null || latencyMillis < 0L) {
            return null;
        }
        return latencyMillis;
    }

    private static ServiceView toServiceView(NetworkServiceEntity entity) {
        return new ServiceView(
                entity.getServiceKind(),
                entity.getServiceName(),
                entity.getPlatform(),
                entity.getFirstSeenAt(),
                entity.getLastSeenAt()
        );
    }

    private static ServiceInstanceView toInstanceView(ServiceInstanceEntity entity) {
        NetworkServiceEntity serviceEntity = entity.getService();
        ServiceKind serviceKind = serviceEntity == null ? null : serviceEntity.getServiceKind();
        String serviceName = serviceEntity == null ? null : serviceEntity.getServiceName();
        String platform = serviceEntity == null ? null : serviceEntity.getPlatform();
        return new ServiceInstanceView(
                entity.getInstanceId(),
                serviceKind,
                serviceName,
                platform,
                entity.getStatus(),
                entity.getHost(),
                entity.getPort(),
                entity.getStartedAt(),
                entity.getLastSeenAt(),
                entity.getStoppedAt()
        );
    }

    private static ServiceProbeView toProbeView(ServiceProbeEntity entity) {
        NetworkServiceEntity serviceEntity = entity.getService();
        ServiceKind serviceKind = serviceEntity == null ? null : serviceEntity.getServiceKind();
        String serviceName = serviceEntity == null ? null : serviceEntity.getServiceName();
        String platform = serviceEntity == null ? null : serviceEntity.getPlatform();
        return new ServiceProbeView(
                serviceKind,
                serviceName,
                platform,
                entity.getObserverInstanceId(),
                entity.getStatus(),
                entity.getTargetHost(),
                entity.getTargetPort(),
                entity.getTargetInstanceId(),
                entity.getLatencyMillis(),
                entity.getErrorCode(),
                entity.getErrorDetail(),
                entity.getCheckedAt()
        );
    }

    private static ServiceEffectiveHealthView toEffectiveHealthView(
            ServiceHealthView healthView,
            ServiceProbeView latestProbe,
            Instant heartbeatCutoff,
            Instant probeCutoff
    ) {
        boolean heartbeatFresh = healthView.latestRunningSeenAt() != null
                && !healthView.latestRunningSeenAt().isBefore(heartbeatCutoff);
        boolean probeFresh = latestProbe != null
                && latestProbe.checkedAt() != null
                && !latestProbe.checkedAt().isBefore(probeCutoff);
        ServiceProbeStatus probeStatus = probeFresh ? latestProbe.status() : null;
        EffectiveServiceHealthStatus effectiveStatus = resolveEffectiveStatus(heartbeatFresh, probeFresh, probeStatus);

        return new ServiceEffectiveHealthView(
                healthView.serviceKind(),
                healthView.serviceName(),
                healthView.platform(),
                healthView.firstSeenAt(),
                healthView.lastSeenAt(),
                healthView.latestRunningSeenAt(),
                healthView.totalInstanceCount(),
                healthView.runningInstanceCount(),
                heartbeatFresh,
                probeFresh,
                probeStatus,
                latestProbe == null ? null : latestProbe.checkedAt(),
                latestProbe == null ? null : latestProbe.latencyMillis(),
                latestProbe == null ? null : latestProbe.errorCode(),
                latestProbe == null ? null : latestProbe.errorDetail(),
                effectiveStatus
        );
    }

    private static EffectiveServiceHealthStatus resolveEffectiveStatus(
            boolean heartbeatFresh,
            boolean probeFresh,
            ServiceProbeStatus probeStatus
    ) {
        boolean probeUp = probeFresh && probeStatus == ServiceProbeStatus.UP;
        boolean probeFailing = probeFresh && probeStatus != ServiceProbeStatus.UP;
        if (heartbeatFresh && probeUp) {
            return EffectiveServiceHealthStatus.HEALTHY;
        }
        if (heartbeatFresh && !probeFresh) {
            return EffectiveServiceHealthStatus.UNKNOWN;
        }
        if (!heartbeatFresh && probeFailing) {
            return EffectiveServiceHealthStatus.UNREACHABLE;
        }
        if (!heartbeatFresh && probeUp) {
            return EffectiveServiceHealthStatus.DEGRADED;
        }
        if (!heartbeatFresh && !probeFresh) {
            return EffectiveServiceHealthStatus.UNKNOWN;
        }
        return EffectiveServiceHealthStatus.DEGRADED;
    }

    private static boolean isAfter(Instant candidate, Instant baseline) {
        if (candidate == null) {
            return false;
        }
        if (baseline == null) {
            return true;
        }
        return candidate.isAfter(baseline);
    }

    private record ServiceKey(ServiceKind serviceKind, String serviceName) {
    }

    private static final class MutableHealth {
        private final String platform;
        private final Instant firstSeenAt;
        private Instant lastSeenAt;
        private Instant latestRunningSeenAt;
        private long totalInstanceCount;
        private long runningInstanceCount;

        private MutableHealth(String platform, Instant firstSeenAt, Instant lastSeenAt) {
            this.platform = platform;
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
        }
    }

    /**
     * Immutable service-level projection used by API consumers.
     */
    public record ServiceView(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            Instant firstSeenAt,
            Instant lastSeenAt
    ) {
    }

    /**
     * Immutable instance-level projection used by API consumers.
     */
    public record ServiceInstanceView(
            String instanceId,
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            ServiceInstanceStatus status,
            String host,
            Integer port,
            Instant startedAt,
            Instant lastSeenAt,
            Instant stoppedAt
    ) {
        public Optional<String> endpoint() {
            if (host == null || host.isBlank() || port == null || port < 0 || port > 65535) {
                return Optional.empty();
            }
            return Optional.of(host + ":" + port);
        }
    }

    /**
     * Immutable probe projection used by API consumers.
     */
    public record ServiceProbeView(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            String observerInstanceId,
            ServiceProbeStatus status,
            String targetHost,
            Integer targetPort,
            String targetInstanceId,
            Long latencyMillis,
            String errorCode,
            String errorDetail,
            Instant checkedAt
    ) {
    }

    /**
     * Immutable aggregate projection for quick service health checks.
     */
    public record ServiceHealthView(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant latestRunningSeenAt,
            long totalInstanceCount,
            long runningInstanceCount
    ) {
    }

    /**
     * Effective service health classification from heartbeat + probe signals.
     */
    public enum EffectiveServiceHealthStatus {
        HEALTHY,
        DEGRADED,
        UNREACHABLE,
        UNKNOWN
    }

    /**
     * Immutable projection combining service heartbeat and probe freshness.
     */
    public record ServiceEffectiveHealthView(
            ServiceKind serviceKind,
            String serviceName,
            String platform,
            Instant firstSeenAt,
            Instant lastSeenAt,
            Instant latestRunningSeenAt,
            long totalInstanceCount,
            long runningInstanceCount,
            boolean heartbeatFresh,
            boolean probeFresh,
            ServiceProbeStatus latestProbeStatus,
            Instant latestProbeCheckedAt,
            Long latestProbeLatencyMillis,
            String latestProbeErrorCode,
            String latestProbeErrorDetail,
            EffectiveServiceHealthStatus effectiveStatus
    ) {
    }
}
