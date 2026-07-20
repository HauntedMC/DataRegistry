package nl.hauntedmc.dataregistry.backend.service;

import nl.hauntedmc.dataregistry.api.DataRegistry;
import nl.hauntedmc.dataregistry.api.entities.PlayerActivitySummaryEntity;
import nl.hauntedmc.dataregistry.api.entities.PlayerEntity;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.time.Instant;
import java.util.Objects;

/**
 * Backend service for privacy-neutral player activity summary updates.
 */
public final class PlayerActivitySummaryService {

    private final DataRegistry dataRegistry;
    private final ILoggerAdapter logger;
    private final boolean featureEnabled;

    public PlayerActivitySummaryService(DataRegistry dataRegistry, ILoggerAdapter logger) {
        this(dataRegistry, logger, true);
    }

    public PlayerActivitySummaryService(
            DataRegistry dataRegistry,
            ILoggerAdapter logger,
            boolean featureEnabled
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.featureEnabled = featureEnabled;
    }

    public void recordLogin(PlayerEntity playerEntity) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("recordLogin called with an invalid player entity.");
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                recordLogin(session, playerEntity, now);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to record login activity summary for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    public void recordSeen(PlayerEntity playerEntity) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("recordSeen called with an invalid player entity.");
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                recordSeen(session, playerEntity, now);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to record seen activity summary for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    public void recordDisconnect(PlayerEntity playerEntity) {
        if (!featureEnabled) {
            return;
        }
        if (!isPersistedPlayer(playerEntity)) {
            logger.warn("recordDisconnect called with an invalid player entity.");
            return;
        }

        Instant now = Instant.now();
        try {
            dataRegistry.getORM().runInTransaction(session -> {
                recordDisconnect(session, playerEntity, now);
                return null;
            });
        } catch (RuntimeException exception) {
            logger.error("Failed to record disconnect activity summary for uuid=" +
                    Sanitization.safeForLog(playerEntity.getUuid()), exception);
        }
    }

    public boolean isFeatureEnabled() {
        return featureEnabled;
    }

    /**
     * Records login activity in the supplied transaction and lets persistence failures propagate.
     */
    public void recordLogin(Session session, PlayerEntity playerEntity, Instant now) {
        if (!featureEnabled) {
            return;
        }
        PlayerActivitySummaryEntity summary = findOrCreateSummary(session, playerEntity, now);
        summary.setLastSeenAt(now);
        summary.setLastLoginAt(now);
    }

    /**
     * Records a last-seen activity update in the supplied transaction.
     */
    public void recordSeen(Session session, PlayerEntity playerEntity, Instant now) {
        if (!featureEnabled) {
            return;
        }
        PlayerActivitySummaryEntity summary = findOrCreateSummary(session, playerEntity, now);
        summary.setLastSeenAt(now);
    }

    /**
     * Records disconnect activity in the supplied transaction and lets persistence failures propagate.
     */
    public void recordDisconnect(Session session, PlayerEntity playerEntity, Instant now) {
        if (!featureEnabled) {
            return;
        }
        PlayerActivitySummaryEntity summary = findOrCreateSummary(session, playerEntity, now);
        summary.setLastSeenAt(now);
        summary.setLastLogoutAt(now);
    }

    private static PlayerActivitySummaryEntity findOrCreateSummary(
            Session session,
            PlayerEntity playerEntity,
            Instant now
    ) {
        Objects.requireNonNull(session, "session must not be null");
        if (!isPersistedPlayer(playerEntity)) {
            throw new IllegalArgumentException("playerEntity must be a persisted player.");
        }
        PlayerEntity managed = session.merge(playerEntity);
        PlayerActivitySummaryEntity summary = session.find(PlayerActivitySummaryEntity.class, managed.getId());
        if (summary == null) {
            summary = new PlayerActivitySummaryEntity();
            summary.setPlayer(managed);
            summary.setFirstSeenAt(now);
            summary.setLastSeenAt(now);
            session.persist(summary);
        } else if (summary.getFirstSeenAt() == null) {
            summary.setFirstSeenAt(now);
        }
        return summary;
    }

    private static boolean isPersistedPlayer(PlayerEntity playerEntity) {
        return playerEntity != null && playerEntity.getId() != null;
    }
}
