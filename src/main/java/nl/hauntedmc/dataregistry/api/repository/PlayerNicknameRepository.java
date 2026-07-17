package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNicknameEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Objects;
import java.util.Optional;

public class PlayerNicknameRepository extends AbstractRepository<PlayerNicknameEntity, Long> {

    public PlayerNicknameRepository(ORMContext ormContext) {
        super(ormContext, PlayerNicknameEntity.class);
    }

    public Optional<PlayerNicknameEntity> findByPlayerId(Long playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return findById(playerId);
    }

    public Optional<String> findNicknameByPlayerId(Long playerId) {
        return findByPlayerId(playerId).map(PlayerNicknameEntity::getNickname);
    }

    public PlayerNicknameEntity saveOrUpdate(long playerId, String nickname) {
        String normalizedNickname = requireNickname(nickname);
        return ormContext.runInTransaction(session -> {
            PlayerNicknameEntity entity = session.find(PlayerNicknameEntity.class, playerId);
            if (entity == null) {
                entity = new PlayerNicknameEntity();
                entity.setPlayerId(playerId);
                entity.setPlayer(session.getReference(PlayerEntity.class, playerId));
                session.persist(entity);
            }
            entity.setNickname(normalizedNickname);
            return entity;
        });
    }

    public void deleteByPlayerId(Long playerId) {
        if (playerId == null) {
            return;
        }
        ormContext.runInTransaction(session -> {
            PlayerNicknameEntity entity = session.find(PlayerNicknameEntity.class, playerId);
            if (entity != null) {
                session.remove(entity);
            }
            return null;
        });
    }

    private static String requireNickname(String nickname) {
        String normalized = Objects.requireNonNull(nickname, "nickname must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("nickname must not be blank");
        }
        return normalized;
    }
}
