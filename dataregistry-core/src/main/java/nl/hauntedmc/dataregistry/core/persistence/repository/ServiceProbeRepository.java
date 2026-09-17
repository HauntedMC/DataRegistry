package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceProbeStatus;
import nl.hauntedmc.dataregistry.core.persistence.entity.ServiceKind;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ServiceProbeRepository extends AbstractRepository<ServiceProbeEntity, Long> {

    public ServiceProbeRepository(ORMContext ormContext) {
        super(ormContext, ServiceProbeEntity.class);
    }

    /**
     * Returns the newest probe for one logical service.
     */
    public Optional<ServiceProbeEntity> findMostRecentByService(ServiceKind kind, String serviceName) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM ServiceProbeEntity p " +
                                        "WHERE p.service.serviceKind = :kind " +
                                        "AND p.service.serviceName = :serviceName " +
                                        "ORDER BY p.checkedAt DESC, p.id DESC",
                                ServiceProbeEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns recent probes for one logical service, newest first.
     */
    public List<ServiceProbeEntity> findRecentByService(ServiceKind kind, String serviceName, int limit) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM ServiceProbeEntity p " +
                                        "WHERE p.service.serviceKind = :kind " +
                                        "AND p.service.serviceName = :serviceName " +
                                        "ORDER BY p.checkedAt DESC, p.id DESC",
                                ServiceProbeEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns the newest probe for one logical service as observed by one proxy/runtime instance.
     */
    public Optional<ServiceProbeEntity> findMostRecentByServiceAndObserver(
            ServiceKind kind,
            String serviceName,
            String observerInstanceId
    ) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        String normalizedObserverInstanceId = normalizeNonBlank(observerInstanceId, "observerInstanceId");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM ServiceProbeEntity p " +
                                        "WHERE p.service.serviceKind = :kind " +
                                        "AND p.service.serviceName = :serviceName " +
                                        "AND p.observerInstanceId = :observerInstanceId " +
                                        "ORDER BY p.checkedAt DESC, p.id DESC",
                                ServiceProbeEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setParameter("observerInstanceId", normalizedObserverInstanceId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns probes newer than the given timestamp across all services.
     */
    public List<ServiceProbeEntity> findCheckedAfter(Instant checkedAfter, int limit) {
        Objects.requireNonNull(checkedAfter, "checkedAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM ServiceProbeEntity p " +
                                        "WHERE p.checkedAt >= :checkedAfter " +
                                        "ORDER BY p.checkedAt DESC, p.id DESC",
                                ServiceProbeEntity.class
                        )
                        .setParameter("checkedAfter", checkedAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns probes written by one observer instance, newest first.
     */
    public List<ServiceProbeEntity> findByObserverInstanceId(String observerInstanceId, int limit) {
        String normalizedObserverInstanceId = normalizeNonBlank(observerInstanceId, "observerInstanceId");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM ServiceProbeEntity p " +
                                        "WHERE p.observerInstanceId = :observerInstanceId " +
                                        "ORDER BY p.checkedAt DESC, p.id DESC",
                                ServiceProbeEntity.class
                        )
                        .setParameter("observerInstanceId", normalizedObserverInstanceId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns the number of probe rows by status.
     */
    public long countByStatus(ServiceProbeStatus status) {
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(p) FROM ServiceProbeEntity p WHERE p.status = :status",
                                Long.class
                        )
                        .setParameter("status", status)
                        .getSingleResult()
        );
    }

    /**
     * Returns the number of probes by service and status.
     */
    public long countByServiceAndStatus(ServiceKind kind, String serviceName, ServiceProbeStatus status) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        Objects.requireNonNull(status, "status must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(p) FROM ServiceProbeEntity p " +
                                        "WHERE p.service.serviceKind = :kind " +
                                        "AND p.service.serviceName = :serviceName " +
                                        "AND p.status = :status",
                                Long.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setParameter("status", status)
                        .getSingleResult()
        );
    }

    /**
     * Deletes every probe older than the given timestamp while bounding each database transaction to {@code limit}
     * selected rows. The cutoff is fixed before draining starts, so newly arriving probes cannot extend the work
     * indefinitely.
     *
     * <p>This method intentionally drains the complete eligible backlog. The old one-batch behavior made retention
     * throughput dependent on the maintenance cadence and could permanently fall behind the probe write rate.</p>
     *
     * <p>Completion is based on the number of rows selected rather than the number actually deleted. Another proxy
     * may concurrently delete some of the selected IDs; that must not make this replica mistake a full batch for the
     * end of the backlog.</p>
     */
    public int deleteCheckedBefore(Instant checkedBefore, int limit) {
        Objects.requireNonNull(checkedBefore, "checkedBefore must not be null");
        int boundedLimit = Math.max(1, limit);
        int totalDeleted = 0;

        while (true) {
            ProbeDeleteBatch batch = ormContext.runInTransaction(session -> {
                List<Long> ids = session.createQuery(
                                "SELECT p.id FROM ServiceProbeEntity p " +
                                        "WHERE p.checkedAt < :checkedBefore " +
                                        "ORDER BY p.checkedAt ASC, p.id ASC",
                                Long.class
                        )
                        .setParameter("checkedBefore", checkedBefore)
                        .setMaxResults(boundedLimit)
                        .list();
                if (ids.isEmpty()) {
                    return new ProbeDeleteBatch(0, 0);
                }
                int deleted = session.createMutationQuery(
                                "DELETE FROM ServiceProbeEntity p WHERE p.id IN :ids"
                        )
                        .setParameter("ids", ids)
                        .executeUpdate();
                return new ProbeDeleteBatch(ids.size(), deleted);
            });
            totalDeleted = Math.addExact(totalDeleted, batch.deleted());
            if (batch.selected() < boundedLimit) {
                return totalDeleted;
            }
        }
    }

    private static String normalizeNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " must not be null");
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private record ProbeDeleteBatch(int selected, int deleted) {
    }
}
