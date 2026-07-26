package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLifecycleOutboxEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Repository for the internal lifecycle idempotency ledger.
 */
public class PlayerLifecycleOutboxRepository extends AbstractRepository<PlayerLifecycleOutboxEntity, Long> {

    public PlayerLifecycleOutboxRepository(ORMContext ormContext) {
        super(ormContext, PlayerLifecycleOutboxEntity.class);
    }

    /**
     * Deletes at most {@code limit} oldest ledger entries created before {@code cutoff}.
     */
    public int deleteCreatedBefore(Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        int boundedLimit = Math.max(1, limit);
        return ormContext.runInTransaction(session -> {
            List<Long> ids = session.createQuery(
                            "SELECT o.id FROM PlayerLifecycleOutboxEntity o " +
                                    "WHERE o.createdAt < :cutoff ORDER BY o.createdAt ASC, o.id ASC",
                            Long.class
                    )
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(boundedLimit)
                    .list();
            if (ids.isEmpty()) {
                return 0;
            }
            return session.createMutationQuery(
                            "DELETE FROM PlayerLifecycleOutboxEntity o WHERE o.id IN :ids"
                    )
                    .setParameter("ids", ids)
                    .executeUpdate();
        });
    }
}
