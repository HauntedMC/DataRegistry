package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerLookup;
import nl.hauntedmc.dataregistry.api.player.PlayerPage;
import nl.hauntedmc.dataregistry.api.player.PlayerPageRequest;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Repository-backed implementation of the public read-only player directory.
 */
public final class RepositoryPlayerDirectory implements PlayerDirectory {

    private final PlayerRepository playerRepository;
    private final PlayerIdentityInitializationTracker identityInitializationTracker;
    private final DataRegistryQueryExecutor queryExecutor;

    public RepositoryPlayerDirectory(
            PlayerRepository playerRepository,
            PlayerIdentityInitializationTracker identityInitializationTracker
    ) {
        this(playerRepository, identityInitializationTracker, DataRegistryQueryExecutor.immediateForTesting());
    }

    public RepositoryPlayerDirectory(
            PlayerRepository playerRepository,
            PlayerIdentityInitializationTracker identityInitializationTracker,
            DataRegistryQueryExecutor queryExecutor
    ) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.identityInitializationTracker = Objects.requireNonNull(
                identityInitializationTracker,
                "identityInitializationTracker must not be null"
        );
        this.queryExecutor = Objects.requireNonNull(queryExecutor, "queryExecutor must not be null");
    }

    @Override
    public Optional<PlayerIdentity> findActiveIdentityCached(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.getActiveIdentity(normalizedUuid);
    }

    @Override
    public Optional<PlayerIdentity> findActiveIdentityCached(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.getActiveIdentity(normalizedUuid);
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findIdentity(PlayerLookup lookup) {
        return queryExecutor.supply("player.identity.lookup", () -> playerRepository.findIdentity(lookup));
    }

    @Override
    public CompletionStage<Map<PlayerLookup, Optional<PlayerIdentity>>> findIdentities(Collection<PlayerLookup> lookups) {
        return queryExecutor.supply("player.identity.bulk", () -> playerRepository.findIdentities(lookups));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUuid(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return queryExecutor.supply("player.identity.uuid", () -> playerRepository.findIdentityByUUID(normalizedUuid));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByPlayerId(long playerId) {
        return queryExecutor.supply("player.identity.id", () -> playerRepository.findIdentityById(playerId));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUuid(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return queryExecutor.supply("player.identity.uuid-string", () -> playerRepository.findIdentityByUUID(normalizedUuid));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUsername(String username) {
        return queryExecutor.supply("player.identity.username", () -> playerRepository.findIdentityByUsername(username));
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByUsernameIgnoreCase(String username) {
        return queryExecutor.supply(
                "player.identity.username-ignore-case",
                () -> playerRepository.findIdentityByUsernameIgnoreCase(username)
        );
    }

    @Override
    public CompletionStage<Optional<PlayerIdentity>> findByIdentifier(String identifier) {
        return queryExecutor.supply(
                "player.identity.identifier",
                () -> playerRepository.findIdentityByIdentifier(identifier)
        );
    }

    @Override
    public CompletionStage<List<PlayerIdentity>> findByUsernamePrefix(String prefix, int limit) {
        return queryExecutor.supply(
                "player.identity.username-prefix",
                () -> playerRepository.findIdentitiesByUsernamePrefix(prefix, limit)
        );
    }

    @Override
    public CompletionStage<PlayerPage<PlayerIdentity>> findByUsernamePrefix(String prefix, PlayerPageRequest pageRequest) {
        return queryExecutor.supply(
                "player.identity.username-prefix-page",
                () -> playerRepository.findIdentitiesByUsernamePrefix(prefix, pageRequest)
        );
    }

    @Override
    public Map<String, PlayerIdentity> snapshotActiveIdentities() {
        return playerRepository.snapshotActiveIdentities();
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return identityInitializationTracker.whenReady(uuid, () -> findActiveIdentityCached(uuid));
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        return whenReady(UUID.fromString(normalizedUuid));
    }

    private static String normalizeUuid(UUID uuid) {
        return uuid == null ? null : uuid.toString();
    }

    private static String normalizeUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(uuid.trim()).toString();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
