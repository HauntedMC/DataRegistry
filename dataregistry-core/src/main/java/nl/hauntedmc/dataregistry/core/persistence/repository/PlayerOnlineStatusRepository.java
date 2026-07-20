package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerOnlineStatusEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.List;
import java.util.Optional;

public class PlayerOnlineStatusRepository extends AbstractRepository<PlayerOnlineStatusEntity, Long> {

    public PlayerOnlineStatusRepository(ORMContext ormContext) {
        super(ormContext, PlayerOnlineStatusEntity.class);
    }

    public Optional<PlayerOnlineStatusEntity> findByPlayerId(Long playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return findById(playerId);
    }

    public List<PlayerOnlineStatusEntity> findOnlinePlayers(int limit) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerOnlineStatusEntity s " +
                                        "WHERE s.online = true " +
                                        "ORDER BY s.currentServer ASC, s.playerId ASC",
                                PlayerOnlineStatusEntity.class
                        )
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerOnlineStatusEntity> findOnlinePlayersByServer(String currentServer, int limit) {
        if (currentServer == null || currentServer.isBlank()) {
            return List.of();
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerOnlineStatusEntity s " +
                                        "WHERE s.online = true AND s.currentServer = :currentServer " +
                                        "ORDER BY s.playerId ASC",
                                PlayerOnlineStatusEntity.class
                        )
                        .setParameter("currentServer", currentServer.trim())
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }
}
