package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerNameHistoryRepository extends AbstractRepository<PlayerNameHistoryEntity, Long> {

    public PlayerNameHistoryRepository(ORMContext ormContext) {
        super(ormContext, PlayerNameHistoryEntity.class);
    }

    public Optional<PlayerNameHistoryEntity> findLatestForPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
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

    /**
     * Returns the name-history row for a specific player/username pair when present.
     */
    public Optional<PlayerNameHistoryEntity> findByPlayerAndUsername(Long playerId, String username) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(username, "username must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT h FROM PlayerNameHistoryEntity h " +
                                        "WHERE h.player.id = :playerId AND h.username = :username",
                                PlayerNameHistoryEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setParameter("username", username)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns the most recently seen name-history rows for a player.
     */
    public List<PlayerNameHistoryEntity> findRecentByPlayer(Long playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT h FROM PlayerNameHistoryEntity h " +
                                        "WHERE h.player.id = :playerId ORDER BY h.lastSeenAt DESC",
                                PlayerNameHistoryEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns recent sightings of a username across players.
     */
    public List<PlayerNameHistoryEntity> findRecentByUsername(String username, int limit) {
        Objects.requireNonNull(username, "username must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT h FROM PlayerNameHistoryEntity h " +
                                        "WHERE h.username = :username ORDER BY h.lastSeenAt DESC",
                                PlayerNameHistoryEntity.class
                        )
                        .setParameter("username", username)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }
}
