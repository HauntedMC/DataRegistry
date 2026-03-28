package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Optional;

public class PlayerNameHistoryRepository extends AbstractRepository<PlayerNameHistoryEntity, Long> {

    public PlayerNameHistoryRepository(ORMContext ormContext) {
        super(ormContext, PlayerNameHistoryEntity.class);
    }

    public Optional<PlayerNameHistoryEntity> findLatestForPlayer(Long playerId) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT h FROM PlayerNameHistoryEntity h " +
                                        "WHERE h.player.id = :playerId ORDER BY h.lastSeenAt DESC",
                                PlayerNameHistoryEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }
}
