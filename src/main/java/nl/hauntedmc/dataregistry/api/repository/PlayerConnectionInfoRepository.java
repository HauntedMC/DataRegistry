package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class PlayerConnectionInfoRepository extends AbstractRepository<PlayerConnectionInfoEntity, Long> {

    public PlayerConnectionInfoRepository(ORMContext ormContext) {
        super(ormContext, PlayerConnectionInfoEntity.class);
    }

    public Optional<PlayerConnectionInfoEntity> findByPlayerId(Long playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return findById(playerId);
    }

    public List<String> findUsernamesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        String normalizedIp = Objects.requireNonNull(ipAddress, "ipAddress must not be null").trim();
        if (normalizedIp.isEmpty()) {
            return List.of();
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT c.player.username FROM PlayerConnectionInfoEntity c " +
                                        "WHERE c.ipAddress = :ip " +
                                        "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) " +
                                        "ORDER BY c.player.username ASC",
                                String.class
                        )
                        .setParameter("ip", normalizedIp)
                        .setParameter("excludePlayerId", excludePlayerId)
                        .list()
        );
    }

    public List<Long> findPlayerIdsByLastIpAddress(String ipAddress, Long excludePlayerId) {
        String normalizedIp = Objects.requireNonNull(ipAddress, "ipAddress must not be null").trim();
        if (normalizedIp.isEmpty()) {
            return List.of();
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT c.player.id FROM PlayerConnectionInfoEntity c " +
                                        "WHERE c.ipAddress = :ip " +
                                        "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) " +
                                        "ORDER BY c.player.id ASC",
                                Long.class
                        )
                        .setParameter("ip", normalizedIp)
                        .setParameter("excludePlayerId", excludePlayerId)
                        .list()
        );
    }
}
