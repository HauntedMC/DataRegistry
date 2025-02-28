package nl.hauntedmc.dataregistry.repository.impl;

import nl.hauntedmc.dataregistry.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.orm.ORMContext;
import nl.hauntedmc.dataregistry.repository.AbstractRepository;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRepository extends AbstractRepository<PlayerEntity, Long> {

    private final Map<String, PlayerEntity> activePlayers = new ConcurrentHashMap<>();

    public PlayerRepository(ORMContext ormContext) {
        super(ormContext, PlayerEntity.class);
    }

    public Optional<PlayerEntity> findByUUID(String uuid) {
        return ormContext.runInTransaction(session ->
                Optional.ofNullable(
                        session.createQuery("select p from PlayerEntity p where p.uuid = :uuid", PlayerEntity.class)
                                .setParameter("uuid", uuid)
                                .uniqueResult()
                )
        );
    }

    /**
     * Returns an active player if cached.
     */
    public Optional<PlayerEntity> getActivePlayer(String uuid) {
        return Optional.ofNullable(activePlayers.get(uuid));
    }

    /**
     * Retrieves or creates a persistent player record, updating the username if needed,
     * and then caches it in the active players map.
     *
     * @param uuid     the player's UUID.
     * @param username the current username.
     * @return the persistent PlayerEntity.
     */
    public PlayerEntity getOrCreateActivePlayer(String uuid, String username) {
        PlayerEntity player = activePlayers.get(uuid);
        if (player != null) {
            // If already active, update username if necessary.
            if (!player.getUsername().equals(username)) {
                player.setUsername(username);
                player = update(player);
                activePlayers.put(uuid, player);
            }
            return player;
        }

        // Not cached: check persistent store.
        Optional<PlayerEntity> optPlayer = findByUUID(uuid);
        if (optPlayer.isPresent()) {
            player = optPlayer.get();
            if (!player.getUsername().equals(username)) {
                player.setUsername(username);
                player = update(player);
            }
        } else {
            // Create new record.
            player = new PlayerEntity();
            player.setUuid(uuid);
            player.setUsername(username);
            player = save(player);
        }
        activePlayers.put(uuid, player);
        return player;
    }

    /**
     * Removes the player from the active cache.
     *
     * @param uuid the player's UUID.
     */
    public void removeActivePlayer(String uuid) {
        activePlayers.remove(uuid);
    }
}
