package nl.hauntedmc.dataregistry.backend.player;

import nl.hauntedmc.dataregistry.api.player.PlayerDirectory;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityReadiness;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Backend implementation of the public read-only player directory.
 * <p>
 * It adapts lifecycle readiness and repository snapshots into immutable identity records.
 */
public final class DefaultPlayerDirectory implements PlayerDirectory {

    private final PlayerRepository playerRepository;
    private final PlayerIdentityReadiness identityReadiness;

    public DefaultPlayerDirectory(PlayerRepository playerRepository, PlayerIdentityReadiness identityReadiness) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.identityReadiness = Objects.requireNonNull(identityReadiness, "identityReadiness must not be null");
    }

    @Override
    public Optional<PlayerIdentity> getActiveIdentity(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.getActiveIdentity(normalizedUuid);
    }

    @Override
    public Optional<PlayerIdentity> getActiveIdentity(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.getActiveIdentity(normalizedUuid);
    }

    @Override
    public Optional<PlayerIdentity> findByUuid(UUID uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.findIdentityByUUID(normalizedUuid);
    }

    @Override
    public Optional<PlayerIdentity> findByUuid(String uuid) {
        String normalizedUuid = normalizeUuid(uuid);
        if (normalizedUuid == null) {
            return Optional.empty();
        }
        return playerRepository.findIdentityByUUID(normalizedUuid);
    }

    @Override
    public Optional<PlayerIdentity> findByUsername(String username) {
        return playerRepository.findIdentityByUsername(username);
    }

    @Override
    public Optional<PlayerIdentity> findByUsernameIgnoreCase(String username) {
        return playerRepository.findIdentityByUsernameIgnoreCase(username);
    }

    @Override
    public Map<String, PlayerIdentity> snapshotActiveIdentities() {
        return playerRepository.snapshotActiveIdentities();
    }

    @Override
    public CompletableFuture<Optional<PlayerIdentity>> whenReady(UUID uuid) {
        return identityReadiness.whenReady(uuid, () -> getActiveIdentity(uuid));
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
