package nl.hauntedmc.dataregistry.api.repository;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRepository extends AbstractRepository<PlayerEntity, Long> {

    private static final int UUID_LEN = 36;
    private static final int USERNAME_MAX_LEN = 32;

    // Cache active players keyed by their UUID.
    private final Map<String, PlayerEntity> activePlayers = new ConcurrentHashMap<>();

    public PlayerRepository(ORMContext ormContext) {
        super(ormContext, PlayerEntity.class);
    }

    public Optional<PlayerEntity> findByUUID(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }

        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", normalizedUuid)
                        .uniqueResult()
        ));
    }

    /**
     * Returns an active player if cached.
     */
    public Optional<PlayerEntity> getActivePlayer(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(activePlayers.get(normalizedUuid));
    }

    /**
     * Retrieves or creates a persistent player record (upsert) and caches it.
     *
     * @param uuid     the player's UUID.
     * @param username the current username.
     * @return the persistent PlayerEntity.
     */
    public PlayerEntity getOrCreateActivePlayer(String uuid, String username) {
        String normalizedUuid = normalizeUuid(uuid);
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUuid == null) {
            throw new IllegalArgumentException("Player UUID is required.");
        }
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("Player username is required.");
        }

        return activePlayers.compute(normalizedUuid, (key, existingPlayer) -> {
            if (existingPlayer != null) {
                // Username update if needed
                if (!Objects.equals(existingPlayer.getUsername(), normalizedUsername)) {
                    existingPlayer.setUsername(normalizedUsername);
                    existingPlayer = update(existingPlayer);
                }
                return existingPlayer;
            }

            // Not in cache, check database
            Optional<PlayerEntity> optPlayer = findByUUID(normalizedUuid);
            PlayerEntity player;
            if (optPlayer.isPresent()) {
                player = optPlayer.get();
                if (!Objects.equals(player.getUsername(), normalizedUsername)) {
                    player.setUsername(normalizedUsername);
                    player = update(player);
                }
            } else {
                // Try create
                player = new PlayerEntity();
                player.setUuid(normalizedUuid);
                player.setUsername(normalizedUsername);
                try {
                    player = save(player);
                } catch (PersistenceException ex) {
                    // Likely a duplicate key error (proxy beat us)
                    Optional<PlayerEntity> existing = findByUUID(normalizedUuid);
                    if (existing.isPresent()) {
                        player = existing.get();
                        if (!Objects.equals(player.getUsername(), normalizedUsername)) {
                            player.setUsername(normalizedUsername);
                            player = update(player);
                        }
                    } else {
                        throw ex;
                    }
                }
            }
            return player;
        });
    }

    public void removeActivePlayer(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid != null) {
            activePlayers.remove(normalizedUuid);
        }
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String value = uuid.trim();
        if (value.isEmpty() || value.length() > UUID_LEN) {
            return null;
        }
        return value;
    }

    private static String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() <= USERNAME_MAX_LEN ? value : value.substring(0, USERNAME_MAX_LEN);
    }
}
