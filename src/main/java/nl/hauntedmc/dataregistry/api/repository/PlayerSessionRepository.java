package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerSessionEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerSessionRepository extends AbstractRepository<PlayerSessionEntity, Long> {

    public PlayerSessionRepository(ORMContext ormContext) {
        super(ormContext, PlayerSessionEntity.class);
    }

    /**
     * Returns the currently open session for a player, if any.
     */
    public Optional<PlayerSessionEntity> findOpenSessionForPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :pid AND s.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC", PlayerSessionEntity.class)
                        .setParameter("pid", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Closes all open sessions for a player (safety in case of missed disconnects).
     */
    public int closeAllOpenSessions(Long playerId, Instant endTime) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(endTime, "endTime must not be null");
        return ormContext.runInTransaction(session ->
                session.createMutationQuery(
                                "UPDATE PlayerSessionEntity s SET s.endedAt = :end " +
                                        "WHERE s.player.id = :pid AND s.endedAt IS NULL")
                        .setParameter("pid", playerId)
                        .setParameter("end", endTime)
                        .executeUpdate()
        );
    }

    /**
     * Returns recent sessions for a player (for dashboards/admin).
     */
    public List<PlayerSessionEntity> findRecentSessions(Long playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :pid ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class)
                        .setParameter("pid", playerId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns the latest session (open or closed) for a player.
     */
    public Optional<PlayerSessionEntity> findLatestSessionForPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.player.id = :pid ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class
                        )
                        .setParameter("pid", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    /**
     * Returns most-recent open sessions across all players.
     */
    public List<PlayerSessionEntity> findOpenSessions(int limit) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.endedAt IS NULL ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class
                        )
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns sessions that started after the given timestamp.
     */
    public List<PlayerSessionEntity> findSessionsStartedAfter(Instant startedAfter, int limit) {
        Objects.requireNonNull(startedAfter, "startedAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerSessionEntity s " +
                                        "WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC",
                                PlayerSessionEntity.class
                        )
                        .setParameter("startedAfter", startedAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    /**
     * Returns the total number of currently open sessions.
     */
    public long countOpenSessions() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(s) FROM PlayerSessionEntity s WHERE s.endedAt IS NULL",
                                Long.class
                        )
                        .getSingleResult()
        );
    }
}
