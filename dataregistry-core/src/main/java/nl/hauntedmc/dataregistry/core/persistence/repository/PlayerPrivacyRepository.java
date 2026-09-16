package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.api.player.PlayerDataVisibility;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerPrivacyEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Persistence for explicit player privacy settings. Missing rows are always public. */
public class PlayerPrivacyRepository extends AbstractRepository<PlayerPrivacyEntity, Long> {

    public PlayerPrivacyRepository(ORMContext ormContext) {
        super(ormContext, PlayerPrivacyEntity.class);
    }

    public PlayerDataVisibility findVisibility(long playerId) {
        requirePlayerId(playerId);
        return findById(playerId).map(PlayerPrivacyEntity::getVisibility).orElse(PlayerDataVisibility.PUBLIC);
    }

    public Map<Long, PlayerDataVisibility> findVisibilities(Collection<Long> playerIds) {
        Objects.requireNonNull(playerIds, "playerIds must not be null");
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long playerId : playerIds) {
            if (playerId == null || playerId <= 0L) {
                throw new IllegalArgumentException("playerIds must contain only positive database IDs.");
            }
            ids.add(playerId);
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return ormContext.runInTransaction(session -> {
            Map<Long, PlayerDataVisibility> result = new LinkedHashMap<>();
            for (Long playerId : ids) {
                result.put(playerId, PlayerDataVisibility.PUBLIC);
            }
            for (PlayerPrivacyEntity entity : session.createQuery(
                    "SELECT p FROM PlayerPrivacyEntity p WHERE p.playerId IN :playerIds",
                    PlayerPrivacyEntity.class
            ).setParameter("playerIds", ids).list()) {
                if (entity.getPlayerId() != null && entity.getVisibility() != null) {
                    result.put(entity.getPlayerId(), entity.getVisibility());
                }
            }
            return Map.copyOf(result);
        });
    }

    /**
     * Persists a non-public setting, or removes the explicit state to reset a player to public.
     */
    public void saveVisibility(long playerId, PlayerDataVisibility visibility) {
        requirePlayerId(playerId);
        PlayerDataVisibility requestedVisibility = Objects.requireNonNull(visibility, "visibility must not be null");
        ormContext.runInTransaction(session -> {
            PlayerPrivacyEntity entity = session.find(PlayerPrivacyEntity.class, playerId);
            if (requestedVisibility == PlayerDataVisibility.PUBLIC) {
                if (entity != null) {
                    session.remove(entity);
                }
                return null;
            }
            if (entity == null) {
                entity = new PlayerPrivacyEntity();
                entity.setPlayerId(playerId);
                entity.setPlayer(session.getReference(PlayerEntity.class, playerId));
                entity.setVisibility(requestedVisibility);
                session.persist(entity);
                return null;
            }
            entity.setVisibility(requestedVisibility);
            return null;
        });
    }

    public Optional<PlayerPrivacyEntity> findByPlayerId(Long playerId) {
        return playerId == null || playerId <= 0L ? Optional.empty() : findById(playerId);
    }

    private static void requirePlayerId(long playerId) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
    }
}
