package nl.hauntedmc.dataregistry.platform.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import nl.hauntedmc.dataregistry.core.config.PlaytimeTrackingSettings;
import nl.hauntedmc.dataregistry.core.persistence.entity.PlayerEntity;
import nl.hauntedmc.dataregistry.core.lifecycle.DisconnectCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerIdentityInitializationTracker.PlayerIdentityInitialization;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriter;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import nl.hauntedmc.dataregistry.core.service.PlayerActivitySummaryService;
import nl.hauntedmc.dataregistry.core.service.PlayerConnectionInfoService;
import nl.hauntedmc.dataregistry.core.service.PlayerNameHistoryService;
import nl.hauntedmc.dataregistry.core.service.PlayerPlaytimeService;
import nl.hauntedmc.dataregistry.core.service.PlayerService;
import nl.hauntedmc.dataregistry.core.service.PlayerSessionService;
import nl.hauntedmc.dataregistry.core.session.DistributedNetworkSessionApi;
import nl.hauntedmc.dataregistry.core.session.PendingSessionClaim;
import nl.hauntedmc.dataregistry.api.session.NetworkSession;
import net.kyori.adventure.text.Component;
import nl.hauntedmc.dataregistry.core.service.PlayerStatusService;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Serializes Velocity player lifecycle persistence off the proxy event thread.
 * <p>
 * Velocity can emit login, server switch, and disconnect events in quick succession. This listener snapshots the
 * platform state synchronously, then processes all database-backed lifecycle work through a per-player queue so
 * dependent feature tables observe identity creation before later lifecycle updates. Periodic playtime writes use a
 * separate lightweight pipeline, but share a keyed write coordinator with lifecycle commands so transactions for the
 * same player cannot overlap.
 */
public class PlayerStatusListener {

    private static final int MAX_LOG_VALUE_LENGTH = 256;
    public static final short PLAYER_LIFECYCLE_EVENT_PRIORITY = 1000;
    private static final ScheduledExecutorService DEFAULT_RETRY_SCHEDULER = Executors.newSingleThreadScheduledExecutor(
            runnable -> {
                Thread thread = new Thread(runnable, "DataRegistry-velocity-lifecycle-retry-fallback");
                thread.setDaemon(true);
                return thread;
            }
    );

    private final PlayerService playerService;
    private final PlayerLifecycleWriter lifecycleWriter;
    private final PlayerPlaytimeService playtimeService;
    private final DistributedNetworkSessionApi networkSessions;
    private final ILoggerAdapter logger;
    private final Executor eventExecutor;
    private final RetainedPlayerLifecycleCommandQueue retainedCommands;
    private final PlayerWriteCoordinator playerWriteCoordinator = new PlayerWriteCoordinator();
    private final ConcurrentMap<String, CompletableFuture<Void>> playerEventPipelines = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingSessionClaim> pendingSessionClaims = new ConcurrentHashMap<>();
    private final Set<String> disconnectsAwaitingReconciliation = ConcurrentHashMap.newKeySet();
    private final ReentrantReadWriteLock playtimePolicyLock = new ReentrantReadWriteLock(true);
    /**
     * The current Velocity connection for each UUID. Presence is owned by a concrete proxy connection, rather than
     * {@link DisconnectEvent.LoginStatus}: a backend connection failure can use a non-successful login status even
     * after this player has been established at the proxy. Comparing player instances also prevents an obsolete
     * connection from taking a newer login for the same UUID offline.
     */
    private final ConcurrentMap<String, Player> currentPlayerConnections = new ConcurrentHashMap<>();
    private final AtomicBoolean acceptingEvents = new AtomicBoolean(true);

    public PlayerStatusListener(
            PlayerService playerService,
            PlayerNameHistoryService nameHistoryService,
            PlayerActivitySummaryService activitySummaryService,
            PlayerStatusService statusService,
            PlayerConnectionInfoService connectionService,
            PlayerSessionService sessionService,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger,
            Executor eventExecutor,
            DistributedNetworkSessionApi networkSessions
    ) {
        this(
                playerService,
                new PlayerLifecycleWriter(
                        Objects.requireNonNull(sessionService, "sessionService must not be null").dataRegistry(),
                        playerService,
                        nameHistoryService,
                        activitySummaryService,
                        statusService,
                        connectionService,
                        sessionService,
                        playtimeService,
                        logger
                ),
                playtimeService,
                logger,
                eventExecutor,
                DEFAULT_RETRY_SCHEDULER,
                () -> {
                },
                networkSessions
        );
    }

    public PlayerStatusListener(
            PlayerService playerService,
            PlayerLifecycleWriter lifecycleWriter,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger,
            Executor eventExecutor,
            DistributedNetworkSessionApi networkSessions
    ) {
        this(
                playerService,
                lifecycleWriter,
                playtimeService,
                logger,
                eventExecutor,
                DEFAULT_RETRY_SCHEDULER,
                () -> {
                },
                networkSessions
        );
    }

    public PlayerStatusListener(
            PlayerService playerService,
            PlayerLifecycleWriter lifecycleWriter,
            PlayerPlaytimeService playtimeService,
            ILoggerAdapter logger,
            Executor eventExecutor,
            ScheduledExecutorService retryScheduler,
            Runnable backendRecoveredCallback,
            DistributedNetworkSessionApi networkSessions
    ) {
        this.playerService = Objects.requireNonNull(playerService, "playerService must not be null");
        this.lifecycleWriter = Objects.requireNonNull(lifecycleWriter, "lifecycleWriter must not be null");
        this.playtimeService = Objects.requireNonNull(playtimeService, "playtimeService must not be null");
        this.networkSessions = Objects.requireNonNull(networkSessions, "networkSessions must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.eventExecutor = Objects.requireNonNull(eventExecutor, "eventExecutor must not be null");
        this.retainedCommands = new RetainedPlayerLifecycleCommandQueue(
                eventExecutor,
                retryScheduler,
                logger,
                backendRecoveredCallback
        );
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public EventTask onPlayerLogin(LoginEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        String ip = extractIp(player);
        String vhost = extractVirtualHost(player);
        if (!acceptingEvents.get()) {
            event.setResult(LoginEvent.ComponentResult.denied(
                    Component.text("Network session service is shutting down.")));
            return EventTask.resumeWhenComplete(CompletableFuture.completedFuture(null));
        }

        currentPlayerConnections.put(uuid, player);
        int protocolVersion = player.getProtocolVersion() == null ? -1 : player.getProtocolVersion().getProtocol();
        CompletableFuture<Void> gate = networkSessions.claimOwnership(player.getUniqueId(), protocolVersion)
                .thenCompose(claim -> {
                    pendingSessionClaims.put(uuid, claim);
                    return initializeLogin(player, uuid, username, ip, vhost, claim);
                })
                .toCompletableFuture()
                .orTimeout(10L, TimeUnit.SECONDS)
                .exceptionally(failure -> {
                    currentPlayerConnections.remove(uuid, player);
                    event.setResult(LoginEvent.ComponentResult.denied(
                            Component.text("Network session initialization failed.")));
                    logger.error("Could not initialize fenced network session for uuid=" + safeForLog(uuid), failure);
                    return null;
                });
        trackPipeline(uuid, gate);
        return EventTask.resumeWhenComplete(gate);
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public void onServerSwitch(ServerConnectedEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        String serverName = event.getServer().getServerInfo().getName();
        if (!isCurrentConnection(uuid, player)) {
            return;
        }

        NetworkSession session = networkSessions.cached(player.getUniqueId()).orElse(null);
        if (session == null) {
            player.disconnect(Component.text("Network session ownership was lost."));
            return;
        }
        TransferCommand command = TransferCommand.create(uuid, username, serverName, session.fence());
        retainedCommands.submit(
                uuid,
                command.eventId(),
                () -> executePlayerWrite(uuid, () -> {
                    var result = lifecycleWriter.transfer(command);
                    if (!networkSessions.changeBackend(player.getUniqueId(), session.fence(), serverName)
                            .toCompletableFuture().join()) {
                        throw new IllegalStateException("Network session was fenced during backend transfer");
                    }
                    return result;
                }),
                ignored -> {
                },
                ignored -> {
                },
                ignored -> {
                }
        );
    }

    @Subscribe(priority = PLAYER_LIFECYCLE_EVENT_PRIORITY)
    public void onPlayerQuit(DisconnectEvent event) {
        Player player = event.getPlayer();
        String uuid = player.getUniqueId().toString();
        String username = player.getUsername();
        if (!removeCurrentConnection(uuid, player)) {
            return;
        }

        NetworkSession session = networkSessions.cached(player.getUniqueId()).orElse(null);
        if (session == null) {
            PendingSessionClaim pending = pendingSessionClaims.remove(uuid);
            if (pending != null) networkSessions.abandon(pending);
            return;
        }
        DisconnectCommand command = DisconnectCommand.create(uuid, username, session.fence());
        retainedCommands.submit(
                uuid,
                command.eventId(),
                () -> executePlayerWrite(uuid, () -> {
                    var result = lifecycleWriter.disconnect(command);
                    if (session != null) {
                        networkSessions.end(player.getUniqueId(), session.fence()).toCompletableFuture().join();
                    }
                    return result;
                }),
                ignored -> {
                    disconnectsAwaitingReconciliation.remove(uuid);
                    playerService.onPlayerQuit(username, uuid);
                },
                ignored -> disconnectsAwaitingReconciliation.add(uuid),
                ignored -> {
                    disconnectsAwaitingReconciliation.remove(uuid);
                    if (session != null) networkSessions.end(player.getUniqueId(), session.fence());
                }
        );
    }

    private CompletableFuture<Void> initializeLogin(
            Player player,
            String uuid,
            String username,
            String ip,
            String vhost,
            PendingSessionClaim claim
    ) {
        PlayerIdentityInitialization initialization = playerService.beginIdentityInitialization(player.getUniqueId());
        LoginCommand command = LoginCommand.create(uuid, username, ip, vhost, claim.fence());
        CompletableFuture<nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteResult> loginWrite;
        try {
            loginWrite = CompletableFuture.supplyAsync(
                    () -> executePlayerWrite(uuid, () -> {
                        var result = lifecycleWriter.login(command);
                        var identity = result.identityOptional().orElseThrow(() ->
                                new IllegalStateException("Lifecycle login completed without an identity."));
                        try {
                            if (!isCurrentConnection(uuid, player)) {
                                throw new IllegalStateException("Player disconnected before session admission completed.");
                            }
                            networkSessions.open(claim, identity.playerId(), identity.username())
                                    .toCompletableFuture().join();
                        } catch (RuntimeException openFailure) {
                            try {
                                lifecycleWriter.disconnect(DisconnectCommand.create(uuid, username, claim.fence()));
                                playerService.onPlayerQuit(username, uuid);
                            } catch (RuntimeException compensationFailure) {
                                openFailure.addSuppressed(compensationFailure);
                            }
                            throw openFailure;
                        }
                        return result;
                    }), eventExecutor
            );
        } catch (RuntimeException schedulingFailure) {
            networkSessions.abandon(claim);
            playerService.failIdentityInitialization(initialization, schedulingFailure);
            return CompletableFuture.failedFuture(schedulingFailure);
        }
        return loginWrite.thenAccept(result -> result.identityOptional().ifPresentOrElse(
                        identity -> playerService.completeIdentityInitialization(initialization, identity),
                        () -> playerService.failIdentityInitialization(initialization,
                                new IllegalStateException("Lifecycle login completed without an identity."))
                )).exceptionallyCompose(failure -> {
                    networkSessions.abandon(claim);
                    playerService.failIdentityInitialization(initialization, failure);
                    return CompletableFuture.failedFuture(failure);
                }).whenComplete((ignored, failure) -> pendingSessionClaims.remove(uuid, claim));
    }

    private void trackPipeline(String uuid, CompletableFuture<Void> pipeline) {
        playerEventPipelines.put(uuid, pipeline);
        pipeline.whenComplete((ignored, failure) -> playerEventPipelines.remove(uuid, pipeline));
    }

    private boolean isCurrentConnection(String uuid, Player player) {
        return currentPlayerConnections.get(uuid) == player;
    }

    private boolean removeCurrentConnection(String uuid, Player player) {
        AtomicBoolean removed = new AtomicBoolean();
        currentPlayerConnections.compute(uuid, (key, currentPlayer) -> {
            if (currentPlayer == player) {
                removed.set(true);
                return null;
            }
            return currentPlayer;
        });
        return removed.get();
    }

    private Optional<CompletableFuture<Void>> enqueuePlayerEvent(String uuid, Runnable task) {
        AtomicReference<CompletableFuture<Void>> queuedPipeline = new AtomicReference<>();
        AtomicReference<String> queuedKey = new AtomicReference<>();
        AtomicReference<RuntimeException> schedulingFailure = new AtomicReference<>();
        playerEventPipelines.compute(uuid, (key, currentPipeline) -> {
            if (!acceptingEvents.get()) {
                return currentPipeline;
            }
            CompletableFuture<Void> base = currentPipeline == null
                    ? CompletableFuture.completedFuture(null)
                    : currentPipeline.exceptionally(throwable -> null);
            CompletableFuture<Void> next;
            try {
                next = base.thenRunAsync(task, eventExecutor);
            } catch (RuntimeException exception) {
                next = new CompletableFuture<>();
                schedulingFailure.set(exception);
            }
            CompletableFuture<Void> scheduledPipeline = next;
            queuedPipeline.set(scheduledPipeline);
            queuedKey.set(key);
            return scheduledPipeline;
        });

        CompletableFuture<Void> scheduledPipeline = queuedPipeline.get();
        String key = queuedKey.get();
        if (scheduledPipeline != null && key != null) {
            scheduledPipeline.whenComplete((ignored, throwable) -> {
                if (throwable != null) {
                    logger.error(
                            "Unhandled exception while processing queued player task for uuid=" + safeForLog(uuid),
                            throwable
                    );
                }
                playerEventPipelines.remove(key, scheduledPipeline);
            });
            RuntimeException failure = schedulingFailure.get();
            if (failure != null) {
                scheduledPipeline.completeExceptionally(failure);
            }
        }
        return Optional.ofNullable(scheduledPipeline);
    }

    public void beginShutdown() {
        acceptingEvents.set(false);
    }

    /**
     * Returns the UUIDs with a live proxy connection. Backend-recovery reconciliation must exclude these players so
     * it does not mistake a temporary database outage for a proxy crash.
     */
    public Set<String> snapshotCurrentPlayerUuids() {
        return Set.copyOf(currentPlayerConnections.keySet());
    }

    /**
     * Returns disconnected players whose lifecycle disconnect experienced a retained transient failure.
     */
    public Set<String> snapshotDisconnectsAwaitingReconciliation() {
        return Set.copyOf(disconnectsAwaitingReconciliation);
    }

    /**
     * Returns players that live recovery must not mutate because they are connected or still have local writes in
     * flight. This closes the race between backend recovery and a retained disconnect retry.
     */
    public Set<String> snapshotPlayersProtectedFromRecovery() {
        Set<String> protectedPlayers = new HashSet<>(currentPlayerConnections.keySet());
        protectedPlayers.addAll(retainedCommands.snapshotPlayerUuids());
        protectedPlayers.addAll(playerEventPipelines.keySet());
        return Set.copyOf(protectedPlayers);
    }

    /** Returns the number of player lifecycle pipelines currently queued or executing. */
    public int activeLifecyclePipelineCount() {
        Set<String> activePlayers = new HashSet<>(retainedCommands.snapshotPlayerUuids());
        activePlayers.addAll(playerEventPipelines.keySet());
        return activePlayers.size();
    }

    /** Returns the number of disconnected players awaiting backend-recovery reconciliation. */
    public int disconnectsAwaitingReconciliationCount() {
        return disconnectsAwaitingReconciliation.size();
    }

    /**
     * Runs destructive player maintenance under the same per-player lock as lifecycle and playtime writes.
     * The action is allowed only after this proxy has fully drained local state for the UUID.
     */
    public <T> T runOfflinePlayerMaintenance(String uuid, Supplier<T> action) {
        Objects.requireNonNull(uuid, "uuid must not be null");
        Objects.requireNonNull(action, "action must not be null");
        return playerWriteCoordinator.execute(uuid, () -> {
            if (currentPlayerConnections.containsKey(uuid)
                    || retainedCommands.hasPendingCommand(uuid)
                    || playerEventPipelines.containsKey(uuid)
                    || disconnectsAwaitingReconciliation.contains(uuid)) {
                throw new IllegalStateException(
                        "Player has an active or pending lifecycle operation and must be fully offline before maintenance."
                );
            }
            return withPlaytimePolicyReadLock(action);
        });
    }

    /**
     * Enqueues a lightweight playtime accrual flush for currently active players.
     */
    public void flushActivePlaytime() {
        queueActivePlaytimeFlushes();
    }

    /**
     * Enqueues a lightweight playtime accrual flush for currently active players.
     *
     * @return number of player queues that accepted a flush task.
     */
    public int queueActivePlaytimeFlushes() {
        int queuedPlayers = 0;
        for (Map.Entry<String, PlayerEntity> entry : playerService.snapshotActivePlayers().entrySet()) {
            PlayerEntity player = entry.getValue();
            if (player == null) {
                continue;
            }
            String uuid = entry.getKey();
            if (enqueuePlayerEvent(uuid, () -> executePlayerWrite(
                    uuid,
                    () -> {
                        // A retained lifecycle command owns the logical ordering for this player even while it is
                        // waiting for its next database retry. A maintenance flush must not overtake that command.
                        if (!retainedCommands.hasPendingCommand(uuid)) {
                            playtimeService.flushActivePlaytime(player);
                        }
                    }
            )).isPresent()) {
                queuedPlayers++;
            }
        }
        return queuedPlayers;
    }

    /** Applies updated playtime mapping and total-exclusion settings to subsequent lifecycle events. */
    public void updatePlaytimeTrackingSettings(PlaytimeTrackingSettings settings) {
        playtimeService.updatePlaytimeTrackingSettings(settings);
    }

    /**
     * Runs a policy update while preventing lifecycle writes from recreating data with the previous policy.
     */
    public <T> T runWithExclusivePlaytimePolicyLock(Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        playtimePolicyLock.writeLock().lock();
        try {
            return action.get();
        } finally {
            playtimePolicyLock.writeLock().unlock();
        }
    }

    /**
     * Enqueues full disconnect persistence for active players that did not emit a disconnect event before shutdown.
     */
    public void closeActivePresenceForShutdown() {
        for (Map.Entry<String, PlayerEntity> entry : playerService.snapshotActivePlayers().entrySet()) {
            PlayerEntity player = entry.getValue();
            if (player == null) {
                continue;
            }
            String uuid = player.getUuid() == null ? entry.getKey() : player.getUuid();
            if (retainedCommands.hasPendingCommand(uuid)) {
                continue;
            }
            NetworkSession networkSession;
            try {
                networkSession = networkSessions.cached(java.util.UUID.fromString(uuid)).orElse(null);
            } catch (IllegalArgumentException invalidUuid) {
                continue;
            }
            if (networkSession == null) {
                continue;
            }
            DisconnectCommand command = DisconnectCommand.create(
                    uuid, player.getUsername(), networkSession.fence());
            retainedCommands.submit(
                    uuid,
                    command.eventId(),
                    () -> executePlayerWrite(uuid, () -> lifecycleWriter.disconnect(command)),
                    ignored -> {
                        disconnectsAwaitingReconciliation.remove(uuid);
                        playerService.onPlayerQuit(player.getUsername(), uuid);
                    },
                    ignored -> disconnectsAwaitingReconciliation.add(uuid),
                    ignored -> disconnectsAwaitingReconciliation.remove(uuid)
            );
        }
    }

    public boolean awaitPipelineDrain(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        if (timeout < 0L) {
            throw new IllegalArgumentException("timeout must be non-negative");
        }
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        while (true) {
            CompletableFuture<?>[] pendingPipelines = playerEventPipelines.values().toArray(CompletableFuture[]::new);
            if (pendingPipelines.length == 0) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                return retainedCommands.awaitIdle(Math.max(0L, remainingNanos), TimeUnit.NANOSECONDS);
            }

            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0L) {
                return false;
            }

            try {
                CompletableFuture.allOf(pendingPipelines).get(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (ExecutionException ignored) {
                // Individual pipeline failures are logged and still count as completed work.
            } catch (TimeoutException timeoutException) {
                return false;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }

    private <T> T executePlayerWrite(String uuid, Supplier<T> action) {
        return playerWriteCoordinator.execute(uuid, () -> withPlaytimePolicyReadLock(action));
    }

    private void executePlayerWrite(String uuid, Runnable action) {
        playerWriteCoordinator.execute(uuid, () -> withPlaytimePolicyReadLock(() -> {
            action.run();
            return null;
        }));
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

    private <T> T withPlaytimePolicyReadLock(Supplier<T> action) {
        playtimePolicyLock.readLock().lock();
        try {
            return action.get();
        } finally {
            playtimePolicyLock.readLock().unlock();
        }
    }

    private String extractIp(Player player) {
        try {
            SocketAddress sa = player.getRemoteAddress();
            if (sa instanceof InetSocketAddress isa) {
                if (isa.getAddress() != null) {
                    return isa.getAddress().getHostAddress();
                }
                return isa.getHostString();
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private String extractVirtualHost(Player player) {
        try {
            return player.getVirtualHost()
                    .map(addr -> addr.getHostString() + ":" + addr.getPort())
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }
}
