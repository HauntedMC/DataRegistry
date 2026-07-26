package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerSessionEntity;
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
                                        "ORDER BY s.startedAt DESC, s.id DESC", PlayerSessionEntity.class)
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
                                        "WHERE s.player.id = :pid ORDER BY s.startedAt DESC, s.id DESC",
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
                                        "WHERE s.player.id = :pid ORDER BY s.startedAt DESC, s.id DESC",
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
                                        "WHERE s.endedAt IS NULL ORDER BY s.startedAt DESC, s.id DESC",
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
                                        "WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC, s.id DESC",
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

    /**
     * Deletes one bounded batch of fully closed session history, including its visits and playtime segments.
     * Sessions with any still-open child row are deliberately excluded.
     *
     * @return the number of sessions removed.
     */
    public int deleteClosedHistoryBefore(Instant cutoff, int limit) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        int boundedLimit = Math.max(1, limit);
        return ormContext.runInTransaction(session -> {
            List<Long> sessionIds = session.createQuery(
                            "SELECT s.id FROM PlayerSessionEntity s " +
                                    "WHERE s.endedAt < :cutoff " +
                                    "AND NOT EXISTS (SELECT 1 FROM PlayerSessionVisitEntity v " +
                                    "WHERE v.session.id = s.id AND v.leftAt IS NULL) " +
                                    "AND NOT EXISTS (SELECT 1 FROM PlayerPlaytimeSegmentEntity p " +
                                    "WHERE p.session.id = s.id AND p.endedAt IS NULL) " +
                                    "ORDER BY s.endedAt ASC, s.id ASC",
                            Long.class
                    )
                    .setParameter("cutoff", cutoff)
                    .setMaxResults(boundedLimit)
                    .list();
            if (sessionIds.isEmpty()) {
                return 0;
            }
            session.createMutationQuery(
                            "DELETE FROM PlayerSessionVisitEntity v WHERE v.session.id IN :sessionIds"
                    )
                    .setParameter("sessionIds", sessionIds)
                    .executeUpdate();
            session.createMutationQuery(
                            "DELETE FROM PlayerPlaytimeSegmentEntity p WHERE p.session.id IN :sessionIds"
                    )
                    .setParameter("sessionIds", sessionIds)
                    .executeUpdate();
            return session.createMutationQuery(
                            "DELETE FROM PlayerSessionEntity s WHERE s.id IN :sessionIds"
                    )
                    .setParameter("sessionIds", sessionIds)
                    .executeUpdate();
        });
    }
}
