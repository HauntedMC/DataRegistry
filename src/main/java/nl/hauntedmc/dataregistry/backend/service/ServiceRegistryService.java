package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.Objects;

public final class ServiceRegistryService {

    private static final int SERVICE_NAME_MAX_LENGTH = 96;
    private static final int PLATFORM_MAX_LENGTH = 32;
    private static final int HOST_MAX_LENGTH = 255;
    private static final int VERSION_MAX_LENGTH = 96;

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final boolean featureEnabled;

    public ServiceRegistryService(DataRegistry dataRegistry, ILoggerAdapter logger, boolean featureEnabled) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.featureEnabled = featureEnabled;
    }

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

                instance.setService(service);
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

    private static Integer normalizePort(Integer port) {
        if (port == null) {
            return null;
        }
        return port >= 0 && port <= 65535 ? port : null;
    }
}
