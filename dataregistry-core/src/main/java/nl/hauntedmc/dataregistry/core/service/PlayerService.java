package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.util.Objects;
import java.util.Optional;
import java.util.Map;
import java.util.UUID;

/**
 * Backend service for player identity lifecycle and active cache access.
 */
public final class PlayerService {

    private final PlayerRepository playerRepository;
    private final PlayerIdentityInitializationTracker identityInitializationTracker;
    private final ILoggerAdapter logger;

    public PlayerService(
            PlayerRepository playerRepository,
            PlayerIdentityInitializationTracker identityInitializationTracker,
            ILoggerAdapter logger
    ) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
        this.identityInitializationTracker = Objects.requireNonNull(
                identityInitializationTracker,
                "identityInitializationTracker must not be null"
        );
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
     * Retrieves or creates the persistent player row inside a caller-owned lifecycle transaction.
     * The active cache is intentionally not changed until the outer transaction has committed.
     *
     * @param session  active Hibernate session supplied by {@code PlayerLifecycleWriter}.
     * @param uuid     player UUID.
     * @param username current player username.
     * @return managed persistent player row.
     */
    public PlayerEntity getOrCreatePlayer(Session session, String uuid, String username) {
        return playerRepository.getOrCreatePlayer(session, uuid, username);
    }

    /**
     * Resolves the known username inside a caller-owned lifecycle transaction.
     */
    public Optional<String> findKnownUsername(Session session, String uuid) {
        return playerRepository.findKnownUsername(session, uuid);
    }

    /**
     * Marks a player as active after lifecycle persistence has committed successfully.
     */
    public void cacheActivePlayer(PlayerEntity player) {
        playerRepository.cacheActivePlayer(player);
        if (player != null) {
            logger.info("Added " + Sanitization.safeForLog(player.getUsername()) + " (" +
                    Sanitization.safeForLog(player.getUuid()) + ") to the local player repository.");
        }
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
     * Starts identity initialization tracking for a platform join lifecycle.
     *
     * @param uuid player UUID being prepared by DataRegistry.
     * @return handle that must be used to complete this specific initialization attempt.
     */
    public PlayerIdentityInitialization beginIdentityInitialization(UUID uuid) {
        return identityInitializationTracker.begin(uuid);
    }

    /**
     * Publishes a successfully initialized player identity to waiting feature code.
     *
     * @param initialization initialization handle returned by {@link #beginIdentityInitialization(UUID)}.
     * @param player         persistent player row returned by {@link #onPlayerJoin(PlayerEntity)}.
     */
    public void completeIdentityInitialization(PlayerIdentityInitialization initialization, PlayerEntity player) {
        Optional<PlayerIdentity> identity = toIdentity(player);
        if (identity.isPresent()) {
            completeIdentityInitialization(initialization, identity.get());
            return;
        }
        identityInitializationTracker.fail(
                initialization,
                new IllegalStateException("Player initialization did not produce a valid DataRegistry identity.")
        );
    }

    /**
     * Publishes a successfully initialized player identity to waiting feature code.
     *
     * @param initialization initialization handle returned by {@link #beginIdentityInitialization(UUID)}.
     * @param identity       immutable identity snapshot returned by lifecycle persistence.
     */
    public void completeIdentityInitialization(PlayerIdentityInitialization initialization, PlayerIdentity identity) {
        if (identity == null) {
            identityInitializationTracker.fail(
                    initialization,
                    new IllegalStateException("Player initialization did not produce a valid DataRegistry identity.")
            );
            return;
        }
        identityInitializationTracker.complete(initialization, identity);
    }

    /**
     * Completes identity waiters with an empty result because the player left or cannot be initialized.
     *
     * @param initialization initialization handle returned by {@link #beginIdentityInitialization(UUID)}.
     */
    public void completeIdentityInitializationUnavailable(PlayerIdentityInitialization initialization) {
        identityInitializationTracker.completeUnavailable(initialization);
    }

    /**
     * Completes identity waiters exceptionally when identity preparation fails.
     *
     * @param initialization initialization handle returned by {@link #beginIdentityInitialization(UUID)}.
     * @param failure        failure cause exposed to waiters.
     */
    public void failIdentityInitialization(PlayerIdentityInitialization initialization, Throwable failure) {
        identityInitializationTracker.fail(initialization, failure);
    }

    /**
     * Cancels all outstanding identity initialization waiters during plugin shutdown.
     */
    public void shutdownIdentityInitialization() {
        identityInitializationTracker.shutdown();
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
