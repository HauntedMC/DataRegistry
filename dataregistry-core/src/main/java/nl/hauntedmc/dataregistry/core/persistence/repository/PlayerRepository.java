package nl.hauntedmc.dataregistry.core.persistence.repository;

import jakarta.persistence.PersistenceException;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerPage;
import nl.hauntedmc.dataregistry.api.player.PlayerPageRequest;
import nl.hauntedmc.dataregistry.core.persistence.repository.AbstractRepository;
import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import org.hibernate.Session;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Internal persistence boundary for the canonical DataRegistry player table.
 * <p>
 * This repository owns player row creation, username synchronization, and the active-player
 * cache used by platform lifecycle listeners. Public consumers should use
 * {@code DataRegistry#players()} for identity and player-data lookups.
 */
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

    public Optional<Long> findIdByUUID(String uuid) {
        return findIdentityByUUID(uuid).map(PlayerIdentity::playerId);
    }

    public Optional<PlayerIdentity> findIdentityById(long playerId) {
        if (playerId <= 0L) {
            return Optional.empty();
        }
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.find(PlayerEntity.class, playerId)
        ).map(PlayerRepository::toIdentity));
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

    public Optional<PlayerIdentity> findIdentityByUUID(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }

        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                                PlayerEntity.class
                        )
                        .setParameter("uuid", normalizedUuid)
                        .setMaxResults(1)
                        .uniqueResult()
        ).map(PlayerRepository::toIdentity));
    }

    public Optional<PlayerIdentity> findIdentityByUsername(String username) {
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
        ).map(PlayerRepository::toIdentity));
    }

    public Optional<PlayerEntity> findByUsernameIgnoreCase(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return Optional.empty();
        }
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE LOWER(p.username) = :username",
                                PlayerEntity.class
                        )
                        .setParameter("username", normalizedUsername.toLowerCase(Locale.ROOT))
                        .setMaxResults(1)
                        .uniqueResult()
        ));
    }

    public Optional<Long> findIdByUsernameIgnoreCase(String username) {
        return findIdentityByUsernameIgnoreCase(username).map(PlayerIdentity::playerId);
    }

    public Optional<PlayerIdentity> findIdentityByUsernameIgnoreCase(String username) {
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUsername == null) {
            return Optional.empty();
        }
        return ormContext.runInTransaction(session -> Optional.ofNullable(
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE LOWER(p.username) = :username",
                                PlayerEntity.class
                        )
                        .setParameter("username", normalizedUsername.toLowerCase(Locale.ROOT))
                        .setMaxResults(1)
                        .uniqueResult()
        ).map(PlayerRepository::toIdentity));
    }

    public Optional<PlayerIdentity> findIdentity(PlayerLookup lookup) {
        if (lookup == null) {
            return Optional.empty();
        }
        return switch (lookup.type()) {
            case PLAYER_ID -> findIdentityById(lookup.playerId());
            case UUID -> findIdentityByUUID(lookup.uuid().toString());
            case USERNAME -> findIdentityByUsernameIgnoreCase(lookup.text());
            case IDENTIFIER -> findIdentityByIdentifier(lookup.text());
        };
    }

    public Optional<PlayerIdentity> findIdentityByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String value = identifier.trim();
        String normalizedUuid = normalizeUuid(value);
        if (normalizedUuid != null) {
            return findIdentityByUUID(normalizedUuid);
        }
        return findIdentityByUsernameIgnoreCase(value);
    }

    public Map<PlayerLookup, Optional<PlayerIdentity>> findIdentities(Collection<PlayerLookup> lookups) {
        if (lookups == null || lookups.isEmpty()) {
            return Map.of();
        }
        List<PlayerLookup> normalizedLookups = lookups.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (normalizedLookups.isEmpty()) {
            return Map.of();
        }

        LinkedHashSet<Long> playerIds = new LinkedHashSet<>();
        LinkedHashSet<String> uuids = new LinkedHashSet<>();
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (PlayerLookup lookup : normalizedLookups) {
            switch (lookup.type()) {
                case PLAYER_ID -> playerIds.add(lookup.playerId());
                case UUID -> uuids.add(lookup.uuid().toString());
                case USERNAME -> usernames.add(lookup.text().toLowerCase(Locale.ROOT));
                case IDENTIFIER -> {
                    String normalizedUuid = normalizeUuid(lookup.text());
                    if (normalizedUuid != null) {
                        uuids.add(normalizedUuid);
                    } else {
                        usernames.add(lookup.text().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        return ormContext.runInTransaction(session -> {
            Map<Long, PlayerIdentity> byId = new LinkedHashMap<>();
            Map<String, PlayerIdentity> byUuid = new LinkedHashMap<>();
            Map<String, PlayerIdentity> byUsername = new LinkedHashMap<>();
            if (!playerIds.isEmpty()) {
                session.createQuery("SELECT p FROM PlayerEntity p WHERE p.id IN :ids", PlayerEntity.class)
                        .setParameter("ids", playerIds)
                        .list()
                        .forEach(player -> addIdentity(player, byId, byUuid, byUsername));
            }
            if (!uuids.isEmpty()) {
                session.createQuery("SELECT p FROM PlayerEntity p WHERE p.uuid IN :uuids", PlayerEntity.class)
                        .setParameter("uuids", uuids)
                        .list()
                        .forEach(player -> addIdentity(player, byId, byUuid, byUsername));
            }
            if (!usernames.isEmpty()) {
                session.createQuery(
                                "SELECT p FROM PlayerEntity p WHERE LOWER(p.username) IN :usernames",
                                PlayerEntity.class
                        )
                        .setParameter("usernames", usernames)
                        .list()
                        .forEach(player -> addIdentity(player, byId, byUuid, byUsername));
            }

            Map<PlayerLookup, Optional<PlayerIdentity>> results = new LinkedHashMap<>();
            for (PlayerLookup lookup : normalizedLookups) {
                results.put(lookup, resolveIdentity(lookup, byId, byUuid, byUsername));
            }
            return results;
        });
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
                                        "ORDER BY LOWER(p.username) ASC, p.id ASC",
                                PlayerEntity.class
                        )
                        .setParameter("prefix", normalizedPrefix.toLowerCase(Locale.ROOT) + "%")
                        .setMaxResults(resultLimit)
                        .list()
        );
    }

    /**
     * Performs a prefix search on usernames and returns immutable identity snapshots.
     */
    public List<PlayerIdentity> findIdentitiesByUsernamePrefix(String prefix, int limit) {
        return findByUsernamePrefix(prefix, limit)
                .stream()
                .map(PlayerRepository::toIdentity)
                .toList();
    }

    public PlayerPage<PlayerIdentity> findIdentitiesByUsernamePrefix(String prefix, PlayerPageRequest pageRequest) {
        String normalizedPrefix = normalizeUsername(prefix);
        if (normalizedPrefix == null) {
            return new PlayerPage<>(List.of(), Optional.empty());
        }
        PlayerPageRequest request = pageRequest == null
                ? PlayerPageRequest.firstPage(PlayerPageRequest.DEFAULT_LIMIT)
                : pageRequest;
        Cursor decodedCursor = decodeCursor(request.afterCursor());
        String normalizedPrefixLower = normalizedPrefix.toLowerCase(Locale.ROOT);
        Cursor cursor = decodedCursor == null || decodedCursor.username().startsWith(normalizedPrefixLower)
                ? decodedCursor
                : null;
        int fetchLimit = request.limit() + 1;

        List<PlayerEntity> players = ormContext.runInTransaction(session -> {
            String cursorClause = cursor == null
                    ? ""
                    : "AND (LOWER(p.username) > :cursorUsername " +
                    "OR (LOWER(p.username) = :cursorUsername AND p.id > :cursorPlayerId)) ";
            var query = session.createQuery(
                            "SELECT p FROM PlayerEntity p " +
                                    "WHERE LOWER(p.username) LIKE :prefix " +
                                    cursorClause +
                                    "ORDER BY LOWER(p.username) ASC, p.id ASC",
                            PlayerEntity.class
                    )
                    .setParameter("prefix", normalizedPrefixLower + "%")
                    .setMaxResults(fetchLimit);
            if (cursor != null) {
                query.setParameter("cursorUsername", cursor.username());
                query.setParameter("cursorPlayerId", cursor.playerId());
            }
            return query.list();
        });

        boolean hasNext = players.size() > request.limit();
        List<PlayerEntity> pageItems = hasNext ? players.subList(0, request.limit()) : players;
        List<PlayerIdentity> identities = pageItems.stream()
                .map(PlayerRepository::toIdentity)
                .toList();
        Optional<String> nextCursor = hasNext && !pageItems.isEmpty()
                ? Optional.of(encodeCursor(pageItems.getLast()))
                : Optional.empty();
        return new PlayerPage<>(identities, nextCursor);
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

    public Optional<PlayerIdentity> getActiveIdentity(String uuid) {
        return getActivePlayer(uuid).map(PlayerRepository::toIdentity);
    }

    /**
     * Retrieves or creates a persistent player record, updates the current username when it changed,
     * and caches the player as active.
     * <p>
     * This is an authoritative player-lifecycle operation. It should be called by DataRegistry's
     * platform join/status handling, not by feature-local persistence that merely wants to attach
     * its own row to an existing canonical player.
     *
     * @param uuid     the live player's UUID.
     * @param username the live player's current username.
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

    /**
     * Retrieves or creates a player row in the caller-owned transaction without changing the active cache.
     *
     * @param session  active Hibernate session supplied by the lifecycle writer.
     * @param uuid     player UUID.
     * @param username current player username.
     * @return managed persistent player row.
     */
    public PlayerEntity getOrCreatePlayer(Session session, String uuid, String username) {
        Objects.requireNonNull(session, "session must not be null");
        String normalizedUuid = normalizeUuid(uuid);
        String normalizedUsername = normalizeUsername(username);
        if (normalizedUuid == null) {
            throw new IllegalArgumentException("Player UUID is required.");
        }
        if (normalizedUsername == null) {
            throw new IllegalArgumentException("Player username is required.");
        }

        PlayerEntity player = session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                        PlayerEntity.class
                )
                .setParameter("uuid", normalizedUuid)
                .setMaxResults(1)
                .uniqueResult();
        if (player == null) {
            player = new PlayerEntity();
            player.setUuid(normalizedUuid);
            player.setUsername(normalizedUsername);
            session.persist(player);
            return player;
        }
        if (!Objects.equals(player.getUsername(), normalizedUsername)) {
            player.setUsername(normalizedUsername);
        }
        return player;
    }

    /**
     * Resolves the cached or persisted username inside a caller-owned lifecycle transaction.
     */
    public Optional<String> findKnownUsername(Session session, String uuid) {
        Objects.requireNonNull(session, "session must not be null");
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        PlayerEntity activePlayer = activePlayers.get(normalizedUuid);
        if (activePlayer != null) {
            return Optional.ofNullable(activePlayer.getUsername());
        }
        PlayerEntity persistedPlayer = session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                        PlayerEntity.class
                )
                .setParameter("uuid", normalizedUuid)
                .setMaxResults(1)
                .uniqueResult();
        return Optional.ofNullable(persistedPlayer).map(PlayerEntity::getUsername);
    }

    /**
     * Marks a committed player lifecycle identity as active in the process-local cache.
     */
    public void cacheActivePlayer(PlayerEntity player) {
        if (player == null) {
            return;
        }
        String normalizedUuid = normalizeUuid(player.getUuid());
        if (normalizedUuid != null) {
            activePlayers.put(normalizedUuid, player);
        }
    }

    /**
     * ID-focused variant of {@link #getOrCreateActivePlayer(String, String)} for authoritative
     * player-lifecycle flows that do not need a mutable entity instance.
     */
    public PlayerIdentity getOrCreateActiveIdentity(String uuid, String username) {
        return toIdentity(getOrCreateActivePlayer(uuid, username));
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

    public Map<String, PlayerIdentity> snapshotActiveIdentities() {
        return activePlayers.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> toIdentity(entry.getValue())
                ));
    }

    public static PlayerIdentity toIdentity(PlayerEntity entity) {
        return new PlayerIdentity(entity.getId(), UUID.fromString(entity.getUuid()), entity.getUsername());
    }

    private static void addIdentity(
            PlayerEntity player,
            Map<Long, PlayerIdentity> byId,
            Map<String, PlayerIdentity> byUuid,
            Map<String, PlayerIdentity> byUsername
    ) {
        PlayerIdentity identity = toIdentity(player);
        byId.putIfAbsent(identity.playerId(), identity);
        byUuid.putIfAbsent(identity.uuid().toString(), identity);
        byUsername.putIfAbsent(identity.username().toLowerCase(Locale.ROOT), identity);
    }

    private static Optional<PlayerIdentity> resolveIdentity(
            PlayerLookup lookup,
            Map<Long, PlayerIdentity> byId,
            Map<String, PlayerIdentity> byUuid,
            Map<String, PlayerIdentity> byUsername
    ) {
        return switch (lookup.type()) {
            case PLAYER_ID -> Optional.ofNullable(byId.get(lookup.playerId()));
            case UUID -> Optional.ofNullable(byUuid.get(lookup.uuid().toString()));
            case USERNAME -> Optional.ofNullable(byUsername.get(lookup.text().toLowerCase(Locale.ROOT)));
            case IDENTIFIER -> {
                String normalizedUuid = normalizeUuid(lookup.text());
                yield normalizedUuid == null
                        ? Optional.ofNullable(byUsername.get(lookup.text().toLowerCase(Locale.ROOT)))
                        : Optional.ofNullable(byUuid.get(normalizedUuid));
            }
        };
    }

    private static String encodeCursor(PlayerEntity player) {
        String raw = player.getUsername().toLowerCase(Locale.ROOT) + "\n" + player.getId();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static Cursor decodeCursor(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int split = raw.lastIndexOf('\n');
            if (split <= 0 || split == raw.length() - 1) {
                return null;
            }
            String username = raw.substring(0, split).trim().toLowerCase(Locale.ROOT);
            long playerId = Long.parseLong(raw.substring(split + 1).trim());
            return username.isEmpty() || playerId <= 0L ? null : new Cursor(username, playerId);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private record Cursor(String username, long playerId) {
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
