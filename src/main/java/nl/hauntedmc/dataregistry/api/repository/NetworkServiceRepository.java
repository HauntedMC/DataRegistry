package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.NetworkServiceEntity;
import nl.hauntedmc.dataregistry.api.entities.ServiceKind;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NetworkServiceRepository extends AbstractRepository<NetworkServiceEntity, Long> {

    public NetworkServiceRepository(ORMContext ormContext) {
        super(ormContext, NetworkServiceEntity.class);
    }

    public Optional<NetworkServiceEntity> findByKindAndName(ServiceKind kind, String serviceName) {
        Objects.requireNonNull(kind, "kind must not be null");
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceKind = :kind AND s.serviceName = :serviceName",
                                NetworkServiceEntity.class
                        )
                        .setParameter("kind", kind)
                        .setParameter("serviceName", normalizedServiceName)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns whether a logical service exists for the given kind and name.
     */
    public boolean existsByKindAndName(ServiceKind kind, String serviceName) {
        return findByKindAndName(kind, serviceName).isPresent();
    }

    public List<NetworkServiceEntity> findAllOrdered() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "ORDER BY s.serviceKind ASC, s.serviceName ASC",
                                NetworkServiceEntity.class
                        )
                        .list()
        );
    }

    public List<NetworkServiceEntity> findByKind(ServiceKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceKind = :kind " +
                                        "ORDER BY s.serviceName ASC",
                                NetworkServiceEntity.class
                        )
                        .setParameter("kind", kind)
                        .list()
        );
    }

    /**
     * Returns all logical services with the same service name across kinds.
     */
    public List<NetworkServiceEntity> findByServiceName(String serviceName) {
        String normalizedServiceName = normalizeNonBlank(serviceName, "serviceName");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.serviceName = :serviceName " +
                                        "ORDER BY s.serviceKind ASC",
                                NetworkServiceEntity.class
                        )
                        .setParameter("serviceName", normalizedServiceName)
                        .list()
        );
    }

    /**
     * Returns logical services seen after the given timestamp, newest first.
     */
    public List<NetworkServiceEntity> findSeenAfter(Instant seenAfter, int limit) {
        Objects.requireNonNull(seenAfter, "seenAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM NetworkServiceEntity s " +
                                        "WHERE s.lastSeenAt >= :seenAfter " +
                                        "ORDER BY s.lastSeenAt DESC, s.serviceKind ASC, s.serviceName ASC",
                                NetworkServiceEntity.class
                        )
                        .setParameter("seenAfter", seenAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns the number of logical services for a specific kind.
     */
    public long countByKind(ServiceKind kind) {
        Objects.requireNonNull(kind, "kind must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(s) FROM NetworkServiceEntity s WHERE s.serviceKind = :kind",
                                Long.class
                        )
                        .setParameter("kind", kind)
                        .getSingleResult()
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
