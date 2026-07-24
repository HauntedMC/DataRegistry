package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLanguageEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Objects;
import java.util.Optional;

public class PlayerLanguageRepository extends AbstractRepository<PlayerLanguageEntity, Long> {

    public PlayerLanguageRepository(ORMContext ormContext) {
        super(ormContext, PlayerLanguageEntity.class);
    }

    public Optional<PlayerLanguageEntity> findByPlayerId(Long playerId) {
        if (playerId == null) {
            return Optional.empty();
        }
        return findById(playerId);
    }

    public PlayerLanguageEntity saveOrUpdate(long playerId, String language, String effectiveLanguage) {
        String normalizedLanguage = requireCode(language, "language");
        String normalizedEffectiveLanguage = requireCode(effectiveLanguage, "effectiveLanguage");
        return ormContext.runInTransaction(session -> {
            PlayerLanguageEntity entity = session.find(PlayerLanguageEntity.class, playerId);
            if (entity == null) {
                entity = new PlayerLanguageEntity();
                entity.setPlayerId(playerId);
                entity.setPlayer(session.getReference(PlayerEntity.class, playerId));
                entity.setLanguage(normalizedLanguage);
                entity.setEffectiveLanguage(normalizedEffectiveLanguage);
                session.persist(entity);
                return entity;
            }
            entity.setLanguage(normalizedLanguage);
            entity.setEffectiveLanguage(normalizedEffectiveLanguage);
            return entity;
        });
    }

    public void deleteByPlayerId(Long playerId) {
        if (playerId == null) {
            return;
        }
        ormContext.runInTransaction(session -> {
            PlayerLanguageEntity entity = session.find(PlayerLanguageEntity.class, playerId);
            if (entity != null) {
                session.remove(entity);
            }
            return null;
        });
    }

    private static String requireCode(String value, String fieldName) {
        String normalized = Objects.requireNonNull(value, fieldName + " must not be null").trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
