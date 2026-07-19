package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

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

    /**
     * Returns immutable player identities whose latest stored IP address matches the given address.
     */
    public List<PlayerIdentity> findIdentitiesByLastIpAddress(String ipAddress, Long excludePlayerId) {
        String normalizedIp = Objects.requireNonNull(ipAddress, "ipAddress must not be null").trim();
        if (normalizedIp.isEmpty()) {
            return List.of();
        }
        return ormContext.runInTransaction(session -> {
            List<Object[]> rows = session.createQuery(
                            "SELECT c.player.id, c.player.uuid, c.player.username " +
                                    "FROM PlayerConnectionInfoEntity c " +
                                    "WHERE c.ipAddress = :ip " +
                                    "AND (:excludePlayerId IS NULL OR c.player.id <> :excludePlayerId) " +
                                    "ORDER BY c.player.username ASC",
                            Object[].class
                    )
                    .setParameter("ip", normalizedIp)
                    .setParameter("excludePlayerId", excludePlayerId)
                    .list();
            List<PlayerIdentity> identities = new ArrayList<>(rows.size());
            for (Object[] row : rows) {
                Long playerId = row[0] instanceof Long value ? value : null;
                String uuid = row[1] instanceof String value ? value : null;
                String username = row[2] instanceof String value ? value : null;
                if (playerId == null || uuid == null || username == null) {
                    continue;
                }
                identities.add(new PlayerIdentity(playerId, UUID.fromString(uuid), username));
            }
            return List.copyOf(identities);
        });
    }
}
