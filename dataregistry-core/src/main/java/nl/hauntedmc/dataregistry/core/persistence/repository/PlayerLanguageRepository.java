package nl.hauntedmc.dataregistry.core.persistence.repository;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLanguageEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLanguageMutationEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerDataChangeOutboxEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerLanguageMutationResult;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import jakarta.persistence.LockModeType;
import org.hibernate.LockMode;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public class PlayerLanguageRepository extends AbstractRepository<PlayerLanguageEntity, Long> {

    private static final int LANGUAGE_CODE_MAX_LENGTH = 16;

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
        requirePlayerId(playerId);
        String normalizedLanguage = requireCode(language, "language");
        String normalizedEffectiveLanguage = normalizeOptionalCode(effectiveLanguage, "effectiveLanguage");
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

    /** A player-row lock serializes duplicate requests, while the language version fences other writers. */
    public PlayerLanguageMutationResult savePreference(long playerId, UUID uuid, String preference,
                                                       long expectedVersion, UUID requestId) {
        requirePlayerId(playerId);
        Objects.requireNonNull(uuid, "uuid");
        Objects.requireNonNull(requestId, "requestId");
        if (expectedVersion < -1L) throw new IllegalArgumentException("expectedVersion is invalid");
        String code = requireCode(preference, "preference").toUpperCase(java.util.Locale.ROOT);
        if (!java.util.Set.of("AUTO", "NL", "EN").contains(code)) {
            throw new IllegalArgumentException("Unsupported language preference");
        }
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = session.find(PlayerEntity.class, playerId, LockMode.PESSIMISTIC_WRITE);
            if (player == null || !uuid.toString().equalsIgnoreCase(player.getUuid())) {
                return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.STALE_IDENTITY, -1L);
            }
            PlayerLanguageMutationEntity prior = session.find(PlayerLanguageMutationEntity.class, requestId.toString());
            if (prior != null) {
                if (prior.getPlayerId() != playerId || !prior.getPlayerUuid().equalsIgnoreCase(uuid.toString())
                        || !prior.getPreference().equals(code) || prior.getExpectedVersion() != expectedVersion) {
                    return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.CONFLICT, prior.getResultVersion());
                }
                return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.REPLAYED, prior.getResultVersion());
            }
            PlayerLanguageEntity entity = session.find(PlayerLanguageEntity.class, playerId);
            long currentVersion = entity == null ? -1L : entity.getVersion();
            if (currentVersion != expectedVersion) {
                return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.CONFLICT, currentVersion);
            }
            if (entity == null) {
                entity = new PlayerLanguageEntity();
                entity.setPlayerId(playerId);
                entity.setPlayer(player);
                entity.setLanguage(code);
                entity.setEffectiveLanguage("AUTO".equals(code) ? null : code);
                session.persist(entity);
            } else {
                entity.setLanguage(code);
                if (!"AUTO".equals(code)) entity.setEffectiveLanguage(code);
            }
            session.flush();
            long version = entity.getVersion();
            long now = System.currentTimeMillis();
            session.persist(new PlayerLanguageMutationEntity(requestId.toString(), playerId, uuid.toString(),
                    code, expectedVersion, version, now));
            session.persist(new PlayerDataChangeOutboxEntity(requestId.toString(), "dataregistry.language", playerId,
                    uuid.toString(), version, now));
            return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.APPLIED, version);
        });
    }

    public PlayerLanguageMutationResult saveIfVersion(UUID uuid, String preference, String effectiveLanguage,
                                                      long expectedVersion) {
        Objects.requireNonNull(uuid, "uuid");
        if (expectedVersion < -1L) throw new IllegalArgumentException("expectedVersion is invalid");
        String code = requireCode(preference, "preference");
        String effective = normalizeOptionalCode(effectiveLanguage, "effectiveLanguage");
        return ormContext.runInTransaction(session -> {
            PlayerEntity player = session.createQuery("from PlayerEntity p where p.uuid = :uuid", PlayerEntity.class)
                    .setParameter("uuid", uuid.toString())
                    .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                    .uniqueResult();
            if (player == null) {
                return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.STALE_IDENTITY, -1L);
            }
            PlayerLanguageEntity entity = session.find(PlayerLanguageEntity.class, player.getId());
            long currentVersion = entity == null ? -1L : entity.getVersion();
            if (currentVersion != expectedVersion) {
                return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.CONFLICT, currentVersion);
            }
            if (entity == null) {
                entity = new PlayerLanguageEntity();
                entity.setPlayerId(player.getId());
                entity.setPlayer(player);
                entity.setLanguage(code);
                entity.setEffectiveLanguage(effective);
                session.persist(entity);
            } else {
                entity.setLanguage(code);
                entity.setEffectiveLanguage(effective);
            }
            session.flush();
            long version = entity.getVersion();
            String eventId = UUID.randomUUID().toString();
            session.persist(new PlayerDataChangeOutboxEntity(eventId, "dataregistry.language", player.getId(),
                    uuid.toString(), version, System.currentTimeMillis()));
            return new PlayerLanguageMutationResult(PlayerLanguageMutationResult.Status.APPLIED, version);
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
        if (normalized.length() > LANGUAGE_CODE_MAX_LENGTH) {
            throw new IllegalArgumentException(fieldName + " must be " + LANGUAGE_CODE_MAX_LENGTH + " characters or fewer");
        }
        return normalized;
    }

    private static String normalizeOptionalCode(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        return requireCode(value, fieldName);
    }

    private static void requirePlayerId(long playerId) {
        if (playerId <= 0L) {
            throw new IllegalArgumentException("playerId must be a positive database id.");
        }
    }
}
