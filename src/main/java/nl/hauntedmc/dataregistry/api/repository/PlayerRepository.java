package nl.hauntedmc.dataregistry.api.repository;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerRepository extends AbstractRepository<PlayerEntity, Long> {

    // Cache active players keyed by their UUID.
    private final Map<String, PlayerEntity> activePlayers = new ConcurrentHashMap<>();
    private final int usernameMaxLength;

    public PlayerRepository(ORMContext ormContext) {
        this(ormContext, 32);
    }

    public PlayerRepository(ORMContext ormContext, int usernameMaxLength) {
        super(ormContext, PlayerEntity.class);
        if (usernameMaxLength < 1 || usernameMaxLength > 32) {
            throw new IllegalArgumentException("usernameMaxLength must be between 1 and 32.");
        }
        this.usernameMaxLength = usernameMaxLength;
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

    public Optional<PlayerEntity> findByUsername(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return Optional.empty();
        }
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE p.username = :username",
                                PlayerEntity.class
                        )
                        .setParameter("username", normalizedUsername)
                        .setMaxResults(1)
                        .uniqueResult()
        ));
    }

    /**
     * Performs a prefix search on usernames (case-insensitive), ordered alphabetically.
     */
    public List<PlayerEntity> findByUsernamePrefix(String prefix, int limit) {
        String normalizedPrefix = normalizeUsername(prefix);
        if (normalizedPrefix == null) {
            return List.of();
        }
        int resultLimit = Math.max(1, limit);
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM PlayerEntity p " +
                                        "WHERE LOWER(p.username) LIKE :prefix " +
                                        "ORDER BY p.username ASC",
                                PlayerEntity.class
                        )
                        .setParameter("prefix", normalizedPrefix.toLowerCase() + "%")
                        .setMaxResults(resultLimit)
                        .list()
        );
    }

    /**
     * Resolves a batch of UUIDs to persistent players, ignoring invalid UUID values.
     */
    public List<PlayerEntity> findByUUIDs(Collection<String> uuids) {
        if (uuids == null || uuids.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> normalizedUuids = new LinkedHashSet<>();
        for (String uuid : uuids) {
            String normalizedUuid = normalizeUuid(uuid);
            if (normalizedUuid != null) {
                normalizedUuids.add(normalizedUuid);
            }
        }
        if (normalizedUuids.isEmpty()) {
            return List.of();
        }
        return ormContext.runInTransaction(session ->
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE p.uuid IN :uuids ORDER BY p.username ASC",
                                PlayerEntity.class
                        )
                        .setParameter("uuids", normalizedUuids)
                        .list()
        );
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

    /**
     * Returns the number of currently cached active players.
     */
    public int countActivePlayers() {
        return activePlayers.size();
    }

    /**
     * Clears all active-player cache entries.
     */
    public void clearActivePlayers() {
        activePlayers.clear();
    }

    /**
     * Returns an immutable snapshot of the active-player cache keyed by normalized UUID.
     */
    public Map<String, PlayerEntity> snapshotActivePlayers() {
        return Map.copyOf(activePlayers);
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null) {
            return null;
        }
        String value = uuid.trim();
        if (value.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String normalizeUsername(String username) {
        if (username == null) {
            return null;
        }
        String value = username.trim();
        if (value.isEmpty()) {
            return null;
        }
        return value.length() <= usernameMaxLength ? value : value.substring(0, usernameMaxLength);
    }
}
