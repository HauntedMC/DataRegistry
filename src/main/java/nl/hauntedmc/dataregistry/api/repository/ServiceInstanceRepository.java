package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceInstanceStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ServiceInstanceRepository extends AbstractRepository<ServiceInstanceEntity, Long> {

    public ServiceInstanceRepository(ORMContext ormContext) {
        super(ormContext, ServiceInstanceEntity.class);
    }

    public Optional<ServiceInstanceEntity> findByInstanceId(String instanceId) {
        String normalizedInstanceId = normalizeNonBlank(instanceId, "instanceId");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i WHERE i.instanceId = :instanceId",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("instanceId", normalizedInstanceId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns whether a concrete runtime instance exists.
     */
    public boolean existsByInstanceId(String instanceId) {
        return findByInstanceId(instanceId).isPresent();
    }

    public List<ServiceInstanceEntity> findAllOrdered() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .list()
        );
    }

    public List<ServiceInstanceEntity> findByStatus(ServiceInstanceStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.status = :status " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("status", status)
                        .list()
        );
    }

    public List<ServiceInstanceEntity> findByService(ServiceKind kind, String serviceName) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.service.serviceKind = :kind " +
                                        "AND i.service.serviceName = :serviceName " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .list()
        );
    }

    /**
     * Returns running instances for a specific logical service.
     */
    public List<ServiceInstanceEntity> findRunningByService(ServiceKind kind, String serviceName, int limit) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.service.serviceKind = :kind " +
                                        "AND i.service.serviceName = :serviceName " +
                                        "AND i.status = :status " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setParameter("status", ServiceInstanceStatus.RUNNING)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns service instances seen after the given timestamp, newest first.
     */
    public List<ServiceInstanceEntity> findSeenAfter(Instant seenAfter, int limit) {
        Objects.requireNonNull(seenAfter, "seenAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.lastSeenAt >= :seenAfter " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("seenAfter", seenAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns running instances whose last heartbeat is older than the given threshold.
     */
    public List<ServiceInstanceEntity> findRunningSeenBefore(Instant seenBefore, int limit) {
        Objects.requireNonNull(seenBefore, "seenBefore must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.status = :status AND i.lastSeenAt < :seenBefore " +
                                        "ORDER BY i.lastSeenAt ASC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("status", ServiceInstanceStatus.RUNNING)
                        .setParameter("seenBefore", seenBefore)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns all running instances whose last heartbeat is older than the given threshold.
     */
    public List<ServiceInstanceEntity> findRunningSeenBefore(Instant seenBefore) {
        Objects.requireNonNull(seenBefore, "seenBefore must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.status = :status AND i.lastSeenAt < :seenBefore " +
                                        "ORDER BY i.lastSeenAt ASC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("status", ServiceInstanceStatus.RUNNING)
                        .setParameter("seenBefore", seenBefore)
                        .list()
        );
    }

    /**
     * Returns the number of instances in the given status.
     */
    public long countByStatus(ServiceInstanceStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(i) FROM ServiceInstanceEntity i WHERE i.status = :status",
                                Long.class
                        )
                        .setParameter("status", status)
                        .getSingleResult()
        );
    }

    /**
     * Returns the number of instances for one logical service in the given status.
     */
    public long countByServiceAndStatus(ServiceKind kind, String serviceName, ServiceInstanceStatus status) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(i) FROM ServiceInstanceEntity i " +
                                        "WHERE i.service.serviceKind = :kind " +
                                        "AND i.service.serviceName = :serviceName " +
                                        "AND i.status = :status",
                                Long.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setParameter("status", status)
                        .getSingleResult()
        );
    }

    public Optional<ServiceInstanceEntity> findMostRecentByServiceAndStatus(
            ServiceKind kind,
            String serviceName,
            ServiceInstanceStatus status
    ) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.service.serviceKind = :kind " +
                                        "AND i.service.serviceName = :serviceName " +
                                        "AND i.status = :status " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setParameter("status", status)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns the newest running instance for one kind + endpoint.
     */
    public Optional<ServiceInstanceEntity> findMostRecentRunningByEndpoint(ServiceKind kind, String host, Integer port) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedHost = normalizeNonBlank(host, "host");
        Objects.requireNonNull(port, "port must not be null");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT i FROM ServiceInstanceEntity i " +
                                        "WHERE i.service.serviceKind = :kind " +
                                        "AND i.status = :status " +
                                        "AND i.host = :host " +
                                        "AND i.port = :port " +
                                        "ORDER BY i.lastSeenAt DESC",
                                ServiceInstanceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("status", ServiceInstanceStatus.RUNNING)
                        .setParameter("host", normalizedHost)
                        .setParameter("port", port)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    private static String normalizeNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
