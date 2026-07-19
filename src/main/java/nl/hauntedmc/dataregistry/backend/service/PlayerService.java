package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.backend.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.backend.lifecycle.PlayerIdentityReadiness;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Backend service for player identity lifecycle and active cache access.
 */
public final class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerIdentityReadiness identityReadiness;
    private final ILoggerAdapter logger;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerIdentityReadiness identityReadiness,
            ILoggerAdapter logger
    ) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.identityReadiness = Objects.requireNonNull(identityReadiness, "identityReadiness must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
    }

    /**
     * On player join, delegate to the repository to retrieve or create the persistent record,
     * update the username if necessary, and cache it.
     *
     * @param tempEntity a temporary PlayerEntity built from live data.
     * @return the persistent PlayerEntity (with generated ID).
     */
    public PlayerEntity onPlayerJoin(PlayerEntity tempEntity) {
        if (tempEntity == null) {
            throw new IllegalArgumentException("tempEntity must not be null");
        }
        String uuid = tempEntity.getUuid();
        String username = tempEntity.getUsername();
        PlayerEntity player = playerRepository.getOrCreateActivePlayer(uuid, username);
        logger.info("Added " + Sanitization.safeForLog(username) + " (" +
                Sanitization.safeForLog(uuid) + ") to the local player repository.");
        return player;
    }

    /**
     * On player quit, remove the player from the active cache.
     *
     * @param uuid the player's UUID.
     */
    public void onPlayerQuit(String username, String uuid) {
        playerRepository.removeActivePlayer(uuid);
        logger.info("Removed " + Sanitization.safeForLog(username) + " (" +
                Sanitization.safeForLog(uuid) + ") from the local player repository.");
    }

    /**
     * Retrieve an active player (if present) from the repository's cache.
     *
     * @param uuid the player's UUID.
     * @return an Optional containing the PlayerEntity.
     */
    public Optional<PlayerEntity> getActivePlayer(String uuid) {
        Optional<PlayerEntity> activePlayer = playerRepository.getActivePlayer(uuid);
        return activePlayer != null ? activePlayer : Optional.empty();
    }

    /**
     * Resolves the most recently known username for a UUID from active cache first, then persistence.
     */
    public Optional<String> findKnownUsername(String uuid) {
        Optional<PlayerEntity> activePlayer = playerRepository.getActivePlayer(uuid);
        if (activePlayer != null && activePlayer.isPresent()) {
            return Optional.ofNullable(activePlayer.get().getUsername());
        }
        Optional<PlayerEntity> persistedPlayer = playerRepository.findByUUID(uuid);
        if (persistedPlayer == null) {
            return Optional.empty();
        }
        return persistedPlayer.map(PlayerEntity::getUsername);
    }

    /**
     * Returns an immutable snapshot of active players keyed by normalized UUID.
     */
    public Map<String, PlayerEntity> snapshotActivePlayers() {
        return playerRepository.snapshotActivePlayers();
    }

    /**
     * Starts identity readiness tracking for a platform join lifecycle.
     *
     * @param uuid player UUID being prepared by DataRegistry.
     * @return a defensive future copy for lifecycle tests and diagnostics.
     */
    public CompletableFuture<Optional<PlayerIdentity>> beginIdentityInitialization(UUID uuid) {
        return identityReadiness.begin(uuid);
    }

    /**
     * Publishes a successfully initialized player identity to waiting feature code.
     *
     * @param player persistent player row returned by {@link #onPlayerJoin(PlayerEntity)}.
     */
    public void completeIdentityReady(PlayerEntity player) {
        toIdentity(player).ifPresent(identityReadiness::complete);
    }

    /**
     * Completes readiness waiters with an empty result because the player left or cannot be initialized.
     *
     * @param uuid player UUID whose identity is unavailable.
     */
    public void completeIdentityUnavailable(UUID uuid) {
        identityReadiness.completeUnavailable(uuid);
    }

    /**
     * Completes readiness waiters exceptionally when identity preparation fails.
     *
     * @param uuid    player UUID whose identity failed to initialize.
     * @param failure failure cause exposed to waiters.
     */
    public void failIdentityInitialization(UUID uuid, Throwable failure) {
        identityReadiness.fail(uuid, failure);
    }

    /**
     * Cancels all outstanding readiness waiters during plugin shutdown.
     */
    public void shutdownIdentityReadiness() {
        identityReadiness.shutdown();
    }

    private static Optional<PlayerIdentity> toIdentity(PlayerEntity player) {
        if (player == null || player.getId() == null || player.getUuid() == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(new PlayerIdentity(
                    player.getId(),
                    UUID.fromString(player.getUuid()),
                    player.getUsername()
            ));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
