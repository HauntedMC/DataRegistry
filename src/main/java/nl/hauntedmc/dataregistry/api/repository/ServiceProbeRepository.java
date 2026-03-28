package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.ServiceProbeEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceProbeStatus;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
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
     * Deletes up to {@code limit} oldest probes older than the given timestamp.
     */
    public int deleteCheckedBefore(Instant checkedBefore, int limit) {
        Objects.requireNonNull(checkedBefore, "checkedBefore must not be null");
        int boundedLimit = Math.max(1, limit);
        return ormContext.runInTransaction(session -> {
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
                return 0;
            }
            return session.createMutationQuery(
                            "DELETE FROM ServiceProbeEntity p WHERE p.id IN :ids"
                    )
                    .setParameter("ids", ids)
                    .executeUpdate();
        });
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
