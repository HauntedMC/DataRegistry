package nl.hauntedmc.dataregistry.api.repository;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRepository extends AbstractRepository<PlayerEntity, Long> {

    // Cache active players keyed by their UUID.
    private final Map<String, PlayerEntity> activePlayers = new ConcurrentHashMap<>();

    public PlayerRepository(ORMContext ormContext) {
        super(ormContext, PlayerEntity.class);
    }

    public Optional<PlayerEntity> findByUUID(String uuid) {
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid", PlayerEntity.class)
                        .setParameter("uuid", uuid)
                        .uniqueResult()
        ));
    }

    /**
     * Returns an active player if cached.
     */
    public Optional<PlayerEntity> getActivePlayer(String uuid) {
        return Optional.ofNullable(activePlayers.get(uuid));
    }

    /**
     * Retrieves or creates a persistent player record (upsert) and caches it.
     *
     * @param uuid     the player's UUID.
     * @param username the current username.
     * @return the persistent PlayerEntity.
     */
    public PlayerEntity getOrCreateActivePlayer(String uuid, String username) {
        return activePlayers.compute(uuid, (key, existingPlayer) -> {
            if (existingPlayer != null) {
                // Username update if needed
                if (!existingPlayer.getUsername().equals(username)) {
                    existingPlayer.setUsername(username);
                    existingPlayer = update(existingPlayer);
                }
                return existingPlayer;
            }

            // Not in cache, check database
            Optional<PlayerEntity> optPlayer = findByUUID(uuid);
            PlayerEntity player;
            if (optPlayer.isPresent()) {
                player = optPlayer.get();
                if (!player.getUsername().equals(username)) {
                    player.setUsername(username);
                    player = update(player);
                }
            } else {
                // Try create
                player = new PlayerEntity();
                player.setUuid(uuid);
                player.setUsername(username);
                try {
                    player = save(player);
                } catch (PersistenceException ex) {
                    // Likely a duplicate key error (proxy beat us)
                    Optional<PlayerEntity> existing = findByUUID(uuid);
                    if (existing.isPresent()) {
                        player = existing.get();
                        if (!player.getUsername().equals(username)) {
                            player.setUsername(username);
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
        activePlayers.remove(uuid);
    }
}
