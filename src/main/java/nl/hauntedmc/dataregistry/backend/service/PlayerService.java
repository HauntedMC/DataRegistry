package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.Objects;
import java.util.Optional;

/**
 * Backend service for player identity lifecycle and active cache access.
 */
public final class PlayerService {

    private final PlayerRepository playerRepository;
    private final ILoggerAdapter logger;

    public PlayerService(PlayerRepository playerRepository, ILoggerAdapter logger) {
        this.playerRepository = Objects.requireNonNull(playerRepository, "playerRepository must not be null");
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
        return playerRepository.getActivePlayer(uuid);
    }
}
