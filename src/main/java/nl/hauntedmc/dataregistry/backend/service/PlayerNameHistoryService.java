package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.entities.PlayerConnectionInfoEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerNameHistoryEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.time.Instant;
import java.util.List;
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

    /**
     * Persists a historical username entry when a player's username changes.
     *
     * @param playerEntity      the persistent player row after upsert.
     * @param previousUsername  the known username before the current login.
     * @param currentUsername   the username seen on the current login.
     */
    public void recordUsernameChange(PlayerEntity playerEntity, String previousUsername, String currentUsername) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("recordUsernameChange called with an invalid player entity.");
            return;
        }
        String normalizedPreviousUsername = Sanitization.trimToLengthOrNull(previousUsername, usernameMaxLength);
        String normalizedCurrentUsername = Sanitization.trimToLengthOrNull(currentUsername, usernameMaxLength);
        if (normalizedPreviousUsername == null || normalizedCurrentUsername == null) {
            return;
        }
        if (normalizedPreviousUsername.equals(normalizedCurrentUsername)) {
            return;
        }

        Instant now = Instant.now();
        boolean useLastDisconnectTimestamp = dataRegistry.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO);
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                PlayerEntity managed = session.merge(playerEntity);
                Instant transitionTime = now;
                if (useLastDisconnectTimestamp) {
                    PlayerConnectionInfoEntity connectionInfo = session.find(PlayerConnectionInfoEntity.class, managed.getId());
                    if (connectionInfo != null && connectionInfo.getLastDisconnectAt() != null) {
                        transitionTime = connectionInfo.getLastDisconnectAt();
                    }
                }

                PlayerNameHistoryEntity history = new PlayerNameHistoryEntity();
                history.setPlayer(managed);
                history.setUsername(normalizedPreviousUsername);
                history.setLastSeenAt(transitionTime);
                session.persist(history);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to record player name history change for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    /**
     * Returns historical names for a player resolved by current username, oldest first.
     */
    public List<NameHistoryView> listChronologicalHistoryForCurrentUsername(String currentUsername, int limit) {
        if (!featureEnabled) {
            return List.of();
        }
        String normalizedCurrentUsername = Sanitization.trimToLengthOrNull(currentUsername, usernameMaxLength);
        if (normalizedCurrentUsername == null) {
            return List.of();
        }
        try {
            Optional<PlayerEntity> playerOptional = dataRegistry.getPlayerRepository().findByUsername(normalizedCurrentUsername);
            if (playerOptional.isEmpty() || playerOptional.get().getId() == null) {
                return List.of();
            }
            return dataRegistry.getPlayerNameHistoryRepository()
                    .findChronologicalByPlayer(playerOptional.get().getId(), Math.max(1, limit))
                    .stream()
                    .map(history -> new NameHistoryView(history.getUsername(), history.getLastSeenAt()))
                    .toList();
        } catch (RuntimeException exception) {
            logger.error(
                    "Failed to list chronological name history for username=" +
                            Sanitization.safeForLog(normalizedCurrentUsername),
                    exception
            );
            return List.of();
        }
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }

    public record NameHistoryView(String formerUsername, Instant lastSeenAt) {
    }
}
