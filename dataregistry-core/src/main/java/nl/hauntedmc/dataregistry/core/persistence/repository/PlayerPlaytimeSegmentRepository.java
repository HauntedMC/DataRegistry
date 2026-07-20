package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPlaytimeSegmentEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerPlaytimeSegmentRepository extends AbstractRepository<PlayerPlaytimeSegmentEntity, Long> {

    public PlayerPlaytimeSegmentRepository(ORMContext ormContext) {
        super(ormContext, PlayerPlaytimeSegmentEntity.class);
    }

    public Optional<PlayerPlaytimeSegmentEntity> findOpenSegmentForPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.player.id = :playerId AND s.endedAt IS NULL " +
                                        "AND s.session.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public List<PlayerPlaytimeSegmentEntity> findRecentSegmentsForPlayer(Long playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.player.id = :playerId ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerPlaytimeSegmentEntity> findRecentSegmentsForPlayerAndGamemode(
            Long playerId,
            String gamemodeKey,
            int limit
    ) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        String normalizedGamemodeKey = requireNormalizedGamemodeKey(gamemodeKey);
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.player.id = :playerId AND s.gamemodeKey = :gamemodeKey " +
                                        "ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setParameter("gamemodeKey", normalizedGamemodeKey)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerPlaytimeSegmentEntity> findOpenSegments(int limit) {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.endedAt IS NULL AND s.session.endedAt IS NULL " +
                                        "ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerPlaytimeSegmentEntity> findSegmentsStartedAfter(Instant startedAfter, int limit) {
        Objects.requireNonNull(startedAfter, "startedAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT s FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.startedAt >= :startedAfter ORDER BY s.startedAt DESC, s.id DESC",
                                PlayerPlaytimeSegmentEntity.class
                        )
                        .setParameter("startedAfter", startedAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public long countOpenSegments() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(s) FROM PlayerPlaytimeSegmentEntity s " +
                                        "WHERE s.endedAt IS NULL AND s.session.endedAt IS NULL",
                                Long.class
                        )
                        .getSingleResult()
        );
    }

    private static String requireNormalizedGamemodeKey(String value) {
        String normalized = normalizeGamemodeKey(value);
        if (normalized == null) {
            throw new IllegalArgumentException("gamemodeKey must not be blank");
        }
        return normalized;
    }

    private static String normalizeGamemodeKey(String value) {
        Objects.requireNonNull(value, "gamemodeKey must not be null");
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isEmpty() ? null : normalized;
    }
}
