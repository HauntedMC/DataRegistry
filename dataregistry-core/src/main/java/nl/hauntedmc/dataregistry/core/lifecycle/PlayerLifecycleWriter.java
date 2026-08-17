package nl.hauntedmc.dataregistry.core.lifecycle;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.persistence.RetryableDatabaseFailure;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLifecycleOutboxEntity;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerLifecycleOutboxEventType;
import nl.hauntedmc.dataregistry.core.persistence.repository.PlayerRepository;
import nl.hauntedmc.dataregistry.core.population.PlayerPopulationService;
import nl.hauntedmc.dataregistry.core.service.PlayerActivitySummaryService;
import nl.hauntedmc.dataregistry.core.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.core.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.core.service.PlayerPlaytimeService;
import nl.hauntedmc.dataregistry.core.service.PlayerService;
import nl.hauntedmc.dataregistry.core.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.core.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Internal command service that atomically persists DataRegistry-owned player lifecycle state.
 * <p>
 * Each command runs all related mutations and the durable outbox insert inside one ORM transaction. Platform
 * listeners should call this writer rather than invoking individual lifecycle services independently.
 */
public final class PlayerLifecycleWriter {

    private static final int DEFAULT_MAX_ATTEMPTS = 3;
    private static final int MAX_LOG_VALUE_LENGTH = 256;
    private static final long BASE_RETRY_DELAY_MILLIS = 25L;

    private final DataRegistry dataRegistry;
    private final PlayerService playerService;
    private final PlayerNameHistoryService nameHistoryService;
    private final PlayerActivitySummaryService activitySummaryService;
    private final PlayerStatusService statusService;
    private final PlayerConnectionInfoService connectionService;
    private final PlayerSessionService sessionService;
    private final PlayerPlaytimeService playtimeService;
    private final PlayerPopulationService populationService;
    private final ILoggerAdapter logger;
    private final int maxAttempts;
    private final long retryBaseDelayMillis;

    public PlayerLifecycleWriter(
            DataRegistry dataRegistry,
            PlayerService playerService,
            PlayerNameHistoryService nameHistoryService,
            PlayerActivitySummaryService activitySummaryService,
            PlayerStatusService statusService,
            PlayerConnectionInfoService connectionService,
            PlayerSessionService sessionService,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger
    ) {
        this(
                dataRegistry,
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                DEFAULT_MAX_ATTEMPTS,
                BASE_RETRY_DELAY_MILLIS
        );
    }

    public PlayerLifecycleWriter(
            DataRegistry dataRegistry,
            PlayerService playerService,
            PlayerNameHistoryService nameHistoryService,
            PlayerActivitySummaryService activitySummaryService,
            PlayerStatusService statusService,
            PlayerConnectionInfoService connectionService,
            PlayerSessionService sessionService,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger,
            int maxAttempts
    ) {
        this(
                dataRegistry,
                playerService,
                nameHistoryService,
                activitySummaryService,
                statusService,
                connectionService,
                sessionService,
                playtimeService,
                logger,
                maxAttempts,
                BASE_RETRY_DELAY_MILLIS
        );
    }

    public PlayerLifecycleWriter(
            DataRegistry dataRegistry,
            PlayerService playerService,
            PlayerNameHistoryService nameHistoryService,
            PlayerActivitySummaryService activitySummaryService,
            PlayerStatusService statusService,
            PlayerConnectionInfoService connectionService,
            PlayerSessionService sessionService,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger,
            int maxAttempts,
            long retryBaseDelayMillis
    ) {
        this.dataRegistry = Objects.requireNonNull(dataRegistry, "dataRegistry must not be null");
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.nameHistoryService = Objects.requireNonNull(nameHistoryService, "nameHistoryService must not be null");
        this.activitySummaryService = Objects.requireNonNull(
                activitySummaryService,
                "activitySummaryService must not be null"
        );
        this.statusService = Objects.requireNonNull(statusService, "statusService must not be null");
        this.connectionService = Objects.requireNonNull(connectionService, "connectionService must not be null");
        this.sessionService = Objects.requireNonNull(sessionService, "sessionService must not be null");
        this.playtimeService = Objects.requireNonNull(playtimeService, "playtimeService must not be null");
        this.populationService = new PlayerPopulationService(
                dataRegistry,
                dataRegistry.isFeatureEnabled(DataRegistryFeature.POPULATION)
        );
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        if (maxAttempts < 1 || maxAttempts > 10) {
            throw new IllegalArgumentException("maxAttempts must be between 1 and 10.");
        }
        this.maxAttempts = maxAttempts;
        if (retryBaseDelayMillis < 0 || retryBaseDelayMillis > 1000) {
            throw new IllegalArgumentException("retryBaseDelayMillis must be between 0 and 1000.");
        }
        this.retryBaseDelayMillis = retryBaseDelayMillis;
    }

    /**
     * Atomically applies player identity, name history, activity, connection, session, population, status and outbox
     * writes for login.
     */
    public PlayerLifecycleWriteResult login(LoginCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return execute(command.eventId(), () -> dataRegistry.getORM().runInTransaction(session -> {
            PlayerLifecycleOutboxEntity existingEvent = findOutboxEvent(session, command.eventId());
            if (existingEvent != null) {
                return duplicateOutcome(
                        session,
                        existingEvent,
                        command.playerUuid(),
                        PlayerLifecycleOutboxEventType.LOGIN,
                        true
                );
            }

            Instant now = command.occurredAt();
            Optional<String> previousUsername = playerService.findKnownUsername(session, command.playerUuid());
            PlayerEntity player = playerService.getOrCreatePlayer(session, command.playerUuid(), command.username());
            flushForGeneratedId(session, player);

            nameHistoryService.recordUsernameChange(
                    session,
                    player,
                    previousUsername.orElse(null),
                    command.username(),
                    now,
                    dataRegistry.isFeatureEnabled(DataRegistryFeature.CONNECTION_INFO)
            );
            activitySummaryService.recordLogin(session, player, now);
            connectionService.updateOnLogin(
                    session,
                    player,
                    connectionService.sanitizeIpAddress(command.ipAddress()),
                    connectionService.sanitizeVirtualHost(command.virtualHost()),
                    now
            );
            sessionService.openSessionOnLogin(
                    session,
                    player,
                    sessionService.sanitizeIpAddress(command.ipAddress()),
                    sessionService.sanitizeVirtualHost(command.virtualHost()),
                    now
            );
            // Population observes the previous durable status, then login marks this proxy connection online.
            populationService.onLogin(session, player, command.eventId(), now);
            statusService.updateStatusOnLogin(session, player);
            persistOutbox(
                    session,
                    command.eventId(),
                    PlayerLifecycleOutboxEventType.LOGIN,
                    player,
                    null,
                    now
            );
            return new TransactionOutcome(false, true, player);
        }));
    }

    /**
     * Atomically applies activity, playtime, session-visit, population, online-status and outbox writes for a server
     * transfer.
     */
    public PlayerLifecycleWriteResult transfer(TransferCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return execute(command.eventId(), () -> dataRegistry.getORM().runInTransaction(session -> {
            PlayerLifecycleOutboxEntity existingEvent = findOutboxEvent(session, command.eventId());
            if (existingEvent != null) {
                return duplicateOutcome(
                        session,
                        existingEvent,
                        command.playerUuid(),
                        PlayerLifecycleOutboxEventType.TRANSFER,
                        true
                );
            }

            Instant now = command.occurredAt();
            PlayerEntity player = playerService.getOrCreatePlayer(session, command.playerUuid(), command.username());
            flushForGeneratedId(session, player);
            activitySummaryService.recordSeen(session, player, now);
            // Playtime must observe the session's previous backend before this transfer replaces it.
            playtimeService.onServerSwitch(session, player, command.serverName(), now);
            // Open/update the new durable visit before population records firstVisitId for exact join correlation.
            sessionService.updateServerOnSwitch(session, player, command.serverName(), now);
            // Population intentionally runs before status replacement so it can see the previous logical gamemode.
            populationService.onTransfer(session, player, command.serverName(), command.eventId(), now);
            statusService.updateStatus(session, player, command.serverName());
            persistOutbox(
                    session,
                    command.eventId(),
                    PlayerLifecycleOutboxEventType.TRANSFER,
                    player,
                    sessionService.sanitizeServerName(command.serverName()),
                    now
            );
            return new TransactionOutcome(false, true, player);
        }));
    }

    /**
     * Atomically applies population, offline status, activity, connection, playtime, session and outbox writes for
     * disconnect.
     */
    public PlayerLifecycleWriteResult disconnect(DisconnectCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return execute(command.eventId(), () -> dataRegistry.getORM().runInTransaction(session -> {
            PlayerLifecycleOutboxEntity existingEvent = findOutboxEvent(session, command.eventId());
            if (existingEvent != null) {
                return duplicateOutcome(
                        session,
                        existingEvent,
                        command.playerUuid(),
                        PlayerLifecycleOutboxEventType.DISCONNECT,
                        false
                );
            }

            Instant now = command.occurredAt();
            PlayerEntity player = playerService.getOrCreatePlayer(session, command.playerUuid(), command.username());
            flushForGeneratedId(session, player);
            // Population must see the player's final durable backend before status/session state is closed.
            populationService.onDisconnect(session, player, now);
            statusService.updateStatusOnQuit(session, player);
            activitySummaryService.recordDisconnect(session, player, now);
            connectionService.updateOnDisconnect(session, player, now);
            playtimeService.closeActivePlaytimeOnDisconnect(session, player, now);
            sessionService.closeSessionOnDisconnect(session, player, now);
            persistOutbox(
                    session,
                    command.eventId(),
                    PlayerLifecycleOutboxEventType.DISCONNECT,
                    player,
                    null,
                    now
            );
            return new TransactionOutcome(false, false, player);
        }));
    }

    private PlayerLifecycleWriteResult execute(String eventId, LifecycleTransaction transaction) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                TransactionOutcome outcome = transaction.execute();
                PlayerIdentity identity = outcome.player() == null ? null : PlayerRepository.toIdentity(outcome.player());
                if (identity != null && outcome.activeAfterCommit()) {
                    playerService.cacheActivePlayer(outcome.player());
                }
                return outcome.duplicate()
                        ? PlayerLifecycleWriteResult.duplicate(eventId, identity)
                        : PlayerLifecycleWriteResult.success(eventId, identity);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                boolean transientFailure = RetryableDatabaseFailure.isRetryable(exception);
                if (!transientFailure || attempt == maxAttempts) {
                    PlayerLifecycleWriteStatus status = transientFailure
                            ? PlayerLifecycleWriteStatus.TRANSIENT_FAILURE
                            : PlayerLifecycleWriteStatus.PERMANENT_FAILURE;
                    logger.error(
                            "Failed to durably persist player lifecycle event eventId=" +
                                    safeForLog(eventId) + " status=" + status,
                            exception
                    );
                    return PlayerLifecycleWriteResult.failure(eventId, status, exception);
                }
                logger.warn(
                        "Transient player lifecycle persistence failure for eventId=" +
                                safeForLog(eventId) + "; retrying attempt " + (attempt + 1) +
                                " of " + maxAttempts + ".",
                        exception
                );
                pauseBeforeRetry(attempt);
            }
        }
        return PlayerLifecycleWriteResult.failure(
                eventId,
                PlayerLifecycleWriteStatus.TRANSIENT_FAILURE,
                lastFailure
        );
    }

    private static PlayerLifecycleOutboxEntity findOutboxEvent(Session session, String eventId) {
        return session.createQuery(
                        "SELECT o FROM PlayerLifecycleOutboxEntity o WHERE o.eventId = :eventId",
                        PlayerLifecycleOutboxEntity.class
                )
                .setParameter("eventId", eventId)
                .setMaxResults(1)
                .uniqueResult();
    }

    private static TransactionOutcome duplicateOutcome(
            Session session,
            PlayerLifecycleOutboxEntity existingEvent,
            String playerUuid,
            PlayerLifecycleOutboxEventType eventType,
            boolean activeAfterCommit
    ) {
        if (existingEvent.getEventType() != eventType
                || !Objects.equals(existingEvent.getPlayerUuid(), playerUuid)) {
            throw new IllegalArgumentException(
                    "Lifecycle event id is already associated with a different event type or player: " +
                            safeForLog(existingEvent.getEventId()) + "."
            );
        }
        PlayerEntity player = session.createQuery(
                        "SELECT p FROM PlayerEntity p WHERE p.uuid = :uuid",
                        PlayerEntity.class
                )
                .setParameter("uuid", playerUuid)
                .setMaxResults(1)
                .uniqueResult();
        if (player == null) {
            throw new IllegalStateException(
                    "Lifecycle outbox event exists without a player row for eventId=" + existingEvent.getEventId() + "."
            );
        }
        return new TransactionOutcome(true, activeAfterCommit, player);
    }

    private static void persistOutbox(
            Session session,
            String eventId,
            PlayerLifecycleOutboxEventType eventType,
            PlayerEntity player,
            String serverName,
            Instant occurredAt
    ) {
        PlayerLifecycleOutboxEntity outbox = new PlayerLifecycleOutboxEntity();
        outbox.setEventId(eventId);
        outbox.setEventType(eventType);
        outbox.setPlayerId(player.getId());
        outbox.setPlayerUuid(player.getUuid());
        outbox.setUsername(player.getUsername());
        outbox.setServerName(serverName);
        outbox.setOccurredAt(occurredAt);
        outbox.setCreatedAt(Instant.now());
        session.persist(outbox);
    }

    private static void flushForGeneratedId(Session session, PlayerEntity player) {
        if (player.getId() == null) {
            session.flush();
        }
        if (player.getId() == null) {
            throw new IllegalStateException("Player lifecycle write did not produce a player id.");
        }
    }

    private void pauseBeforeRetry(int completedAttempts) {
        try {
            TimeUnit.MILLISECONDS.sleep(retryBaseDelayMillis * completedAttempts);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private static String safeForLog(String value) {
        if (value == null) {
            return "<null>";
        }
        int outputLimit = Math.min(value.length(), MAX_LOG_VALUE_LENGTH);
        StringBuilder sanitized = new StringBuilder(outputLimit + 3);
        for (int i = 0; i < value.length() && sanitized.length() < outputLimit; i++) {
            char character = value.charAt(i);
            sanitized.append(Character.isISOControl(character) ? '_' : character);
        }
        if (value.length() > outputLimit) {
            sanitized.append("...");
        }
        return sanitized.toString();
    }

    @FunctionalInterface
    private interface LifecycleTransaction {
        TransactionOutcome execute();
    }

    private record TransactionOutcome(boolean duplicate, boolean activeAfterCommit, PlayerEntity player) {
    }
}
