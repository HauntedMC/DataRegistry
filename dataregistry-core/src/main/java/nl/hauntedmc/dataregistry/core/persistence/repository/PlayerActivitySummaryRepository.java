package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerActivitySummaryRepository extends AbstractRepository<PlayerActivitySummaryEntity, Long> {

    public PlayerActivitySummaryRepository(ORMContext ormContext) {
        super(ormContext, PlayerActivitySummaryEntity.class);
    }

    public Optional<PlayerActivitySummaryEntity> findByPlayerId(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return findById(playerId);
    }

    public List<PlayerActivitySummaryEntity> findRecentlySeen(int limit) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerActivitySummaryEntity s " +
                                        "ORDER BY s.lastSeenAt DESC, s.playerId DESC",
                                PlayerActivitySummaryEntity.class
                        )
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }
}
