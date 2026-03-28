package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
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
    private static final int VERSION_MAX_LENGTH = 96;

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
            Integer port,
            String version
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
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, 36);
        if (normalizedServiceName == null || normalizedPlatform == null || normalizedInstanceId == null) {
            logger.warn("refreshRunningInstance called with invalid required service metadata.");
            return;
        }
        String normalizedHost = Sanitization.trimToLengthOrNull(host, HOST_MAX_LENGTH);
        String normalizedVersion = Sanitization.trimToLengthOrNull(version, VERSION_MAX_LENGTH);

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
                    instance.setVersion(normalizedVersion);
                    session.persist(instance);
                    return null;
                }

                instance.setStatus(ServiceInstanceStatus.RUNNING);
                instance.setLastSeenAt(now);
                instance.setStoppedAt(null);
                instance.setHost(normalizedHost);
                instance.setPort(normalizePort(port));
                instance.setVersion(normalizedVersion);
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
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, 36);
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
        String normalizedInstanceId = Sanitization.trimToLengthOrNull(instanceId, 36);
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

    private static Integer normalizePort(Integer port) {
        if (port == null) {
            return null;
        }
        return port >= 0 && port <= 65535 ? port : null;
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
                entity.getVersion(),
                entity.getStartedAt(),
                entity.getLastSeenAt(),
                entity.getStoppedAt()
        );
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
            String version,
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
}
