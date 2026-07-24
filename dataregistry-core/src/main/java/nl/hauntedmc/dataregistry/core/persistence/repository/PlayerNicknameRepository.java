package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerNicknameEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Objects;
import java.util.Optional;

public class PlayerNicknameRepository extends AbstractRepository<PlayerNicknameEntity, Long> {

    private static final int NICKNAME_MAX_LENGTH = 255;

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
        requirePlayerId(playerId);
        String normalizedNickname = requireNickname(nickname);
        return ormContext.runInTransaction(session -> {
            PlayerNicknameEntity entity = session.find(PlayerNicknameEntity.class, playerId);
            if (entity == null) {
                entity = new PlayerNicknameEntity();
                entity.setPlayerId(playerId);
                entity.setPlayer(session.getReference(PlayerEntity.class, playerId));
                entity.setNickname(normalizedNickname);
                session.persist(entity);
                return entity;
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
        if (normalized.length() > NICKNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("nickname must be " + NICKNAME_MAX_LENGTH + " characters or fewer");
        }
        return normalized;
    }

    private static void requirePlayerId(long playerId) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
    }
}
