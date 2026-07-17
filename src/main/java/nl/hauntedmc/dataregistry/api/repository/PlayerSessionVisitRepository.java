package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerSessionVisitEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerSessionVisitRepository extends AbstractRepository<PlayerSessionVisitEntity, Long> {

    public PlayerSessionVisitRepository(ORMContext ormContext) {
        super(ormContext, PlayerSessionVisitEntity.class);
    }

    public Optional<PlayerSessionVisitEntity> findOpenVisitForPlayer(Long playerId) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT v FROM PlayerSessionVisitEntity v " +
                                        "WHERE v.player.id = :playerId AND v.leftAt IS NULL " +
                                        "ORDER BY v.enteredAt DESC, v.id DESC",
                                PlayerSessionVisitEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(1)
                        .uniqueResultOptional()
        );
    }

    public List<PlayerSessionVisitEntity> findRecentVisitsForPlayer(Long playerId, int limit) {
        Objects.requireNonNull(playerId, "playerId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT v FROM PlayerSessionVisitEntity v " +
                                        "WHERE v.player.id = :playerId ORDER BY v.enteredAt DESC, v.id DESC",
                                PlayerSessionVisitEntity.class
                        )
                        .setParameter("playerId", playerId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerSessionVisitEntity> findRecentVisitsForSession(Long sessionId, int limit) {
        Objects.requireNonNull(sessionId, "sessionId must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT v FROM PlayerSessionVisitEntity v " +
                                        "WHERE v.session.id = :sessionId ORDER BY v.enteredAt ASC, v.id ASC",
                                PlayerSessionVisitEntity.class
                        )
                        .setParameter("sessionId", sessionId)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public List<PlayerSessionVisitEntity> findVisitsEnteredAfter(Instant enteredAfter, int limit) {
        Objects.requireNonNull(enteredAfter, "enteredAfter must not be null");
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT v FROM PlayerSessionVisitEntity v " +
                                        "WHERE v.enteredAt >= :enteredAfter ORDER BY v.enteredAt DESC, v.id DESC",
                                PlayerSessionVisitEntity.class
                        )
                        .setParameter("enteredAfter", enteredAfter)
                        .setMaxResults(Math.max(1, limit))
                        .list()
        );
    }

    public long countOpenVisits() {
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT COUNT(v) FROM PlayerSessionVisitEntity v WHERE v.leftAt IS NULL",
                                Long.class
                        )
                        .getSingleResult()
        );
    }
}
