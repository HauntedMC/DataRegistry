package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public final class PlayerNameHistoryService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final int usernameMaxLength;
    private final boolean featureEnabled;

    public PlayerNameHistoryService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            int usernameMaxLength,
            boolean featureEnabled
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        if (usernameMaxLength < 1 || usernameMaxLength > 32) {
            throw new IllegalArgumentException("usernameMaxLength must be between 1 and 32.");
        }
        this.usernameMaxLength = usernameMaxLength;
        this.featureEnabled = featureEnabled;
    }

    public void recordSeenUsername(PlayerEntity playerEntity, String username) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("recordSeenUsername called with an invalid player entity.");
            return;
        }
        String normalizedUsername = Sanitization.trimToLengthOrNull(username, usernameMaxLength);
        if (normalizedUsername == null) {
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                Optional<PlayerNameHistoryEntity> latestOptional = session.createQuery(
                                "SELECT h FROM PlayerNameHistoryEntity h " +
                                        "WHERE h.player.id = :playerId ORDER BY h.lastSeenAt DESC",
                                PlayerNameHistoryEntity.class
                        )
                        .setParameter("playerId", managed.getId())
                        .setMaxResults(1)
                        .uniqueResultOptional();

                if (latestOptional.isPresent()) {
                    PlayerNameHistoryEntity latest = latestOptional.get();
                    if (normalizedUsername.equals(latest.getUsername())) {
                        latest.setLastSeenAt(now);
                        return null;
                    }
                }

                PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();
                history.setPlayer(managed);
                history.setUsername(normalizedUsername);
                history.setFirstSeenAt(now);
                history.setLastSeenAt(now);
                session.persist(history);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to update player name history for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
