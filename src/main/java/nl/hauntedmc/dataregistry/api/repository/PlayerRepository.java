package nl.hauntedmc.dataregistry.api.repository;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.orm.ORMContext;
import nl.hauntedmc.dataregistry.repository.AbstractRepository;

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
        // Atomic operation on the map to avoid race conditions.
        return activePlayers.compute(uuid, (key, existingPlayer) -> {
            if (existingPlayer != null) {
                // If username changed, update in DB
                if (!existingPlayer.getUsername().equals(username)) {
                    existingPlayer.setUsername(username);
                    existingPlayer = update(existingPlayer);
                }
                return existingPlayer;
            }

            // If not in cache, we need to check the database
            Optional<PlayerEntity> optPlayer = findByUUID(uuid);
            PlayerEntity player;
            if (optPlayer.isPresent()) {
                player = optPlayer.get();
                if (!player.getUsername().equals(username)) {
                    player.setUsername(username);
                    player = update(player);
                }
            } else {
                // Create a new record
                player = new PlayerEntity();
                player.setUuid(uuid);
                player.setUsername(username);
                player = save(player);
            }
            return player;
        });
    }

    public void removeActivePlayer(String uuid) {
        activePlayers.remove(uuid);
    }
}
