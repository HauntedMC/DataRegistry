package nl.hauntedmc.dataregistry.platform.velocity.listener;

import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteResult;
import nl.hauntedmc.dataregistry.core.lifecycle.PlayerLifecycleWriteStatus;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Retains lifecycle commands which exhausted the writer's immediate retries.
 * <p>
 * Commands are strictly serialized per player. A transiently failed head stays in place, so a later transfer or
 * disconnect cannot overtake the login or transfer it depends on. Transient failures retry indefinitely with capped
 * exponential backoff; permanent failures and capacity rejections are retained as observable terminal failures.
 */
final class RetainedPlayerLifecycleCommandQueue {

    static final int DEFAULT_MAX_PENDING_PER_PLAYER = 32;
    private static final int MAX_RETAINED_TERMINAL_FAILURES = 1_024;
    static final long INITIAL_RETRY_DELAY_MILLIS = 250L;
    static final long MAX_RETRY_DELAY_MILLIS = TimeUnit.MINUTES.toMillis(1L);

    private final Map<String, PlayerQueue> queues = new ConcurrentHashMap<>();
    private final Map<String, TerminalFailure> terminalFailures = new ConcurrentHashMap<>();
    private final Deque<String> terminalFailureOrder = new ArrayDeque<>();
    private final Executor commandExecutor;
    private final ScheduledExecutorService retryScheduler;
    private final ILoggerAdapter logger;
    private final Runnable backendRecoveredCallback;
    private final int maxPendingPerPlayer;
    private boolean backendRecoveryPending;

    RetainedPlayerLifecycleCommandQueue(
            Executor commandExecutor,
            ScheduledExecutorService retryScheduler,
            ILoggerAdapter logger,
            Runnable backendRecoveredCallback
    ) {
        this(
                commandExecutor,
                retryScheduler,
                logger,
                backendRecoveredCallback,
                DEFAULT_MAX_PENDING_PER_PLAYER
        );
    }

    RetainedPlayerLifecycleCommandQueue(
            Executor commandExecutor,
            ScheduledExecutorService retryScheduler,
            ILoggerAdapter logger,
            Runnable backendRecoveredCallback,
            int maxPendingPerPlayer
    ) {
        this.commandExecutor = Objects.requireNonNull(commandExecutor, "commandExecutor must not be null");
        this.retryScheduler = Objects.requireNonNull(retryScheduler, "retryScheduler must not be null");
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.backendRecoveredCallback = Objects.requireNonNull(
                backendRecoveredCallback,
                "backendRecoveredCallback must not be null"
        );
        if (maxPendingPerPlayer < 1) {
            throw new IllegalArgumentException("maxPendingPerPlayer must be positive.");
        }
        this.maxPendingPerPlayer = maxPendingPerPlayer;
    }

    CompletableFuture<PlayerLifecycleWriteResult> submit(
            String playerUuid,
            String eventId,
            Supplier<PlayerLifecycleWriteResult> write,
            Consumer<PlayerLifecycleWriteResult> onSuccess,
            Consumer<Throwable> onTransientFailure,
            Consumer<Throwable> onTerminalFailure
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(eventId, "eventId must not be null");
        PendingCommand command = new PendingCommand(
                eventId,
                Objects.requireNonNull(write, "write must not be null"),
                Objects.requireNonNull(onSuccess, "onSuccess must not be null"),
                Objects.requireNonNull(onTransientFailure, "onTransientFailure must not be null"),
                Objects.requireNonNull(onTerminalFailure, "onTerminalFailure must not be null")
        );

        boolean start;
        synchronized (this) {
            PlayerQueue queue = queues.computeIfAbsent(playerUuid, ignored -> new PlayerQueue());
            if (queue.commands.size() >= maxPendingPerPlayer) {
                IllegalStateException failure = new IllegalStateException(
                        "Lifecycle retry queue capacity exceeded for player " + playerUuid + "."
                );
                recordTerminalFailure(playerUuid, command, failure);
                command.result.complete(PlayerLifecycleWriteResult.failure(
                        eventId,
                        PlayerLifecycleWriteStatus.PERMANENT_FAILURE,
                        failure
                ));
                runTerminalCallback(command, failure);
                return command.result;
            }
            queue.commands.addLast(command);
            start = queue.commands.size() == 1;
        }
        if (start) {
            dispatch(playerUuid);
        }
        return command.result;
    }

    boolean hasPendingCommand(String playerUuid) {
        synchronized (this) {
            PlayerQueue queue = queues.get(playerUuid);
            return queue != null && !queue.commands.isEmpty();
        }
    }

    int pendingCommandCount() {
        synchronized (this) {
            return queues.values().stream().mapToInt(queue -> queue.commands.size()).sum();
        }
    }

    TerminalFailure terminalFailure(String playerUuid) {
        return terminalFailures.get(playerUuid);
    }

    boolean awaitIdle(long timeout, TimeUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        long deadlineNanos = System.nanoTime() + unit.toNanos(timeout);
        synchronized (this) {
            while (!queues.isEmpty()) {
                long remainingNanos = deadlineNanos - System.nanoTime();
                if (remainingNanos <= 0L) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(this, remainingNanos);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
            return true;
        }
    }

    private void dispatch(String playerUuid) {
        try {
            commandExecutor.execute(() -> executeHead(playerUuid));
        } catch (RuntimeException exception) {
            terminallyFailHead(playerUuid, exception);
        }
    }

    private void executeHead(String playerUuid) {
        PendingCommand command;
        synchronized (this) {
            PlayerQueue queue = queues.get(playerUuid);
            if (queue == null || queue.commands.isEmpty()) {
                return;
            }
            command = queue.commands.getFirst();
        }

        PlayerLifecycleWriteResult outcome;
        try {
            outcome = command.write.get();
        } catch (RuntimeException exception) {
            outcome = PlayerLifecycleWriteResult.failure(
                    command.eventId,
                    PlayerLifecycleWriteStatus.PERMANENT_FAILURE,
                    exception
            );
        }

        if (outcome.succeeded()) {
            completeHead(playerUuid, command, outcome);
        } else if (outcome.status() == PlayerLifecycleWriteStatus.TRANSIENT_FAILURE) {
            retryHead(playerUuid, command, outcome.failure());
        } else {
            terminallyFailHead(playerUuid, outcome.failure());
        }
    }

    private void completeHead(String playerUuid, PendingCommand command, PlayerLifecycleWriteResult outcome) {
        boolean invokeRecovery;
        boolean dispatchNext;
        synchronized (this) {
            removeHead(playerUuid, command);
            command.result.complete(outcome);
            invokeRecovery = backendRecoveryPending;
            backendRecoveryPending = false;
            dispatchNext = hasHead(playerUuid);
        }
        if (invokeRecovery) {
            runBackendRecoveryCallback();
        }
        runSuccessCallback(command, outcome);
        if (dispatchNext) {
            dispatch(playerUuid);
        }
    }

    private void retryHead(String playerUuid, PendingCommand command, Throwable failure) {
        long delayMillis;
        synchronized (this) {
            if (!isHead(playerUuid, command)) {
                return;
            }
            command.transientAttempts++;
            backendRecoveryPending = true;
            delayMillis = retryDelayMillis(command.transientAttempts);
        }
        logger.warn(
                "Retaining transiently failed player lifecycle event eventId=" + command.eventId +
                        " for retry " + command.transientAttempts + " in " + delayMillis + "ms.",
                failure
        );
        runTransientFailureCallback(command, failure);
        try {
            retryScheduler.schedule(() -> dispatch(playerUuid), delayMillis, TimeUnit.MILLISECONDS);
        } catch (RuntimeException exception) {
            terminallyFailHead(playerUuid, exception);
        }
    }

    private void terminallyFailHead(String playerUuid, Throwable failure) {
        PendingCommand command;
        boolean dispatchNext;
        synchronized (this) {
            PlayerQueue queue = queues.get(playerUuid);
            if (queue == null || queue.commands.isEmpty()) {
                return;
            }
            command = queue.commands.getFirst();
            removeHead(playerUuid, command);
            Throwable resolvedFailure = failure == null
                    ? new IllegalStateException("Lifecycle command failed without an exception.")
                    : failure;
            recordTerminalFailure(playerUuid, command, resolvedFailure);
            command.result.complete(PlayerLifecycleWriteResult.failure(
                    command.eventId,
                    PlayerLifecycleWriteStatus.PERMANENT_FAILURE,
                    resolvedFailure
            ));
            dispatchNext = hasHead(playerUuid);
        }
        Throwable resolvedFailure = failure == null
                ? new IllegalStateException("Lifecycle command failed without an exception.")
                : failure;
        runTerminalCallback(command, resolvedFailure);
        if (dispatchNext) {
            dispatch(playerUuid);
        }
    }

    private void removeHead(String playerUuid, PendingCommand command) {
        PlayerQueue queue = queues.get(playerUuid);
        if (queue == null || queue.commands.peekFirst() != command) {
            return;
        }
        queue.commands.removeFirst();
        if (queue.commands.isEmpty()) {
            queues.remove(playerUuid, queue);
            notifyAll();
        }
    }

    private boolean isHead(String playerUuid, PendingCommand command) {
        PlayerQueue queue = queues.get(playerUuid);
        return queue != null && queue.commands.peekFirst() == command;
    }

    private boolean hasHead(String playerUuid) {
        PlayerQueue queue = queues.get(playerUuid);
        return queue != null && !queue.commands.isEmpty();
    }

    private void recordTerminalFailure(String playerUuid, PendingCommand command, Throwable failure) {
        TerminalFailure terminalFailure = new TerminalFailure(playerUuid, command.eventId, failure);
        terminalFailures.put(playerUuid, terminalFailure);
        terminalFailureOrder.remove(playerUuid);
        terminalFailureOrder.addLast(playerUuid);
        while (terminalFailureOrder.size() > MAX_RETAINED_TERMINAL_FAILURES) {
            String expiredPlayerUuid = terminalFailureOrder.removeFirst();
            terminalFailures.remove(expiredPlayerUuid);
        }
        logger.error(
                "Player lifecycle event reached terminal failure eventId=" + command.eventId +
                        " uuid=" + playerUuid + ".",
                failure
        );
    }

    private void runSuccessCallback(PendingCommand command, PlayerLifecycleWriteResult outcome) {
        try {
            command.onSuccess.accept(outcome);
        } catch (RuntimeException exception) {
            logger.error("Lifecycle command success callback failed for eventId=" + command.eventId + ".", exception);
        }
    }

    private void runTerminalCallback(PendingCommand command, Throwable failure) {
        try {
            command.onTerminalFailure.accept(failure);
        } catch (RuntimeException exception) {
            logger.error("Lifecycle command terminal callback failed for eventId=" + command.eventId + ".", exception);
        }
    }

    private void runTransientFailureCallback(PendingCommand command, Throwable failure) {
        try {
            command.onTransientFailure.accept(failure);
        } catch (RuntimeException exception) {
            logger.error("Lifecycle command transient callback failed for eventId=" + command.eventId + ".", exception);
        }
    }

    private void runBackendRecoveryCallback() {
        try {
            backendRecoveredCallback.run();
        } catch (RuntimeException exception) {
            logger.error("Failed to reconcile player presence after backend recovery.", exception);
        }
    }

    private static long retryDelayMillis(int transientAttempts) {
        int shift = Math.min(transientAttempts - 1, 8);
        return Math.min(INITIAL_RETRY_DELAY_MILLIS << shift, MAX_RETRY_DELAY_MILLIS);
    }

    record TerminalFailure(String playerUuid, String eventId, Throwable cause) {
    }

    private static final class PlayerQueue {
        private final Deque<PendingCommand> commands = new ArrayDeque<>();
    }

    private static final class PendingCommand {
        private final String eventId;
        private final Supplier<PlayerLifecycleWriteResult> write;
        private final Consumer<PlayerLifecycleWriteResult> onSuccess;
        private final Consumer<Throwable> onTransientFailure;
        private final Consumer<Throwable> onTerminalFailure;
        private final CompletableFuture<PlayerLifecycleWriteResult> result = new CompletableFuture<>();
        private int transientAttempts;

        private PendingCommand(
                String eventId,
                Supplier<PlayerLifecycleWriteResult> write,
                Consumer<PlayerLifecycleWriteResult> onSuccess,
                Consumer<Throwable> onTransientFailure,
                Consumer<Throwable> onTerminalFailure
        ) {
            this.eventId = eventId;
            this.write = write;
            this.onSuccess = onSuccess;
            this.onTransientFailure = onTransientFailure;
            this.onTerminalFailure = onTerminalFailure;
        }
    }
}
