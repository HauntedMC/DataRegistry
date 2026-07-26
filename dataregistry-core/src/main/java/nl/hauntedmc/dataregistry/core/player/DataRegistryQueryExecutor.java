package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;
import java.util.Set;

/**
 * Owns public DataRegistry query execution, deadlines, and cancellation plumbing.
 */
public final class DataRegistryQueryExecutor implements AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 2L;

    private final ExecutorService queryExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Semaphore querySlots;
    private final Set<CancellableQueryFuture<?>> activeQueries;
    private final Duration timeout;
    private final boolean developmentThreadChecks;
    private final ILoggerAdapter logger;
    private final boolean immediate;
    private final AtomicBoolean closed;

    public DataRegistryQueryExecutor(
            int workerThreads,
            Duration timeout,
            boolean developmentThreadChecks,
            ILoggerAdapter logger
    ) {
        if (workerThreads < 1) {
            throw new IllegalArgumentException("workerThreads must be positive.");
        }
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive.");
        }
        this.developmentThreadChecks = developmentThreadChecks;
        this.logger = Objects.requireNonNull(logger, "logger must not be null");
        this.immediate = false;
        this.queryExecutor = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("DataRegistry-query-", 0).factory()
        );
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(namedFactory("DataRegistry-query-timeout-"));
        this.querySlots = new Semaphore(workerThreads);
        this.activeQueries = ConcurrentHashMap.newKeySet();
        this.closed = new AtomicBoolean();
    }

    private DataRegistryQueryExecutor() {
        this.queryExecutor = null;
        this.timeoutExecutor = null;
        this.querySlots = null;
        this.activeQueries = Set.of();
        this.timeout = Duration.ofSeconds(30L);
        this.developmentThreadChecks = false;
        this.logger = new NoopLogger();
        this.immediate = true;
        this.closed = new AtomicBoolean();
    }

    public static DataRegistryQueryExecutor immediateForTesting() {
        return new DataRegistryQueryExecutor();
    }

    public <T> CompletableFuture<T> supply(String operation, Supplier<T> supplier) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
        if (closed.get()) {
            return closedFuture();
        }
        if (immediate) {
            try {
                return CompletableFuture.completedFuture(supplier.get());
            } catch (Throwable throwable) {
                CompletableFuture<T> failed = new CompletableFuture<>();
                failed.completeExceptionally(throwable);
                return failed;
            }
        }
        warnIfLikelyServerEventThread(
                operation,
                "was requested from likely server/event thread. The call is asynchronous; do not block on it."
        );

        CancellableQueryFuture<T> result = new CancellableQueryFuture<>(
                operation,
                developmentThreadChecks,
                logger,
                new QueryCancellation()
        );
        activeQueries.add(result);
        if (closed.get()) {
            closeFuture(result);
            activeQueries.remove(result);
            return result;
        }

        Future<?> worker;
        try {
            worker = queryExecutor.submit(() -> {
            boolean acquiredSlot = false;
            try {
                querySlots.acquire();
                acquiredSlot = true;
                if (result.isDone()) {
                    return;
                }
                CURRENT_CANCELLATION.set(result.cancellation());
                result.complete(supplier.get());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                if (!result.isDone() && !result.cancellation().isCancelled()) {
                    result.completeExceptionally(interruptedException);
                }
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            } finally {
                CURRENT_CANCELLATION.remove();
                result.cancellation().clearSessions();
                activeQueries.remove(result);
                if (acquiredSlot) {
                    querySlots.release();
                }
            }
            });
        } catch (RejectedExecutionException exception) {
            failSubmission(result, exception, null);
            return result;
        }
        result.setWorker(worker);

        Future<?> timeoutTask;
        try {
            timeoutTask = timeoutExecutor.schedule(
                    () -> {
                        TimeoutException exception = new TimeoutException(
                                "DataRegistry query '" + operation + "' exceeded " + timeout.toMillis() + "ms."
                        );
                        if (result.isDone()) {
                            return;
                        }
                        result.cancellation().cancelDatabaseWork();
                        if (result.completeExceptionally(exception)) {
                            worker.cancel(true);
                        }
                    },
                    timeout.toMillis(),
                    TimeUnit.MILLISECONDS
            );
        } catch (RejectedExecutionException exception) {
            failSubmission(result, exception, worker);
            return result;
        }
        result.whenComplete((value, failure) -> timeoutTask.cancel(false));
        return result;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (immediate) {
            return;
        }
        // Complete public stages before worker interruption so callers can never be stranded by non-cooperative work.
        activeQueries.forEach(result -> {
            closeFuture(result);
            activeQueries.remove(result);
        });
        queryExecutor.shutdownNow();
        timeoutExecutor.shutdownNow();
        awaitTermination(queryExecutor);
        awaitTermination(timeoutExecutor);
    }

    private void warnIfLikelyServerEventThread(String operation, String message) {
        if (!developmentThreadChecks) {
            return;
        }
        if (isLikelyServerEventThread()) {
            logger.warn(
                    "DataRegistry query '" + operation + "' " + message +
                            " Thread: '" + Thread.currentThread().getName() + "'."
            );
        }
    }

    private <T> CompletableFuture<T> closedFuture() {
        CompletableFuture<T> result = new CompletableFuture<>();
        result.completeExceptionally(new DataRegistryQueryExecutorClosedException());
        return result;
    }

    private void closeFuture(CancellableQueryFuture<?> result) {
        result.completeForClose();
    }

    private void failSubmission(CancellableQueryFuture<?> result, RejectedExecutionException failure, Future<?> worker) {
        Throwable completionFailure = closed.get() ? new DataRegistryQueryExecutorClosedException() : failure;
        if (!result.completeExceptionally(completionFailure)) {
            return;
        }
        result.cancellation().cancelDatabaseWork();
        activeQueries.remove(result);
        if (worker != null) {
            worker.cancel(true);
        }
    }

    private static boolean isLikelyServerEventThread() {
        String threadName = Thread.currentThread().getName().toLowerCase(Locale.ROOT);
        return threadName.contains("server thread")
                || threadName.contains("main")
                || threadName.contains("event")
                || threadName.contains("netty");
    }

    private static void awaitTermination(ExecutorService executor) {
        try {
            executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static ThreadFactory namedFactory(String prefix) {
        AtomicInteger counter = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName(prefix + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    private static final ThreadLocal<QueryCancellation> CURRENT_CANCELLATION = new ThreadLocal<>();

    /**
     * Registers the Hibernate session used by the current public-query task. A deadline or cancellation then invokes
     * Hibernate's {@link Session#cancelQuery()} instead of merely interrupting the Java worker thread.
     */
    public static void registerDatabaseSession(Session session) {
        if (session == null) {
            return;
        }
        QueryCancellation cancellation = CURRENT_CANCELLATION.get();
        if (cancellation != null) {
            cancellation.register(session);
        }
    }

    private static final class CancellableQueryFuture<T> extends CompletableFuture<T> {
        private final String operation;
        private final boolean developmentThreadChecks;
        private final ILoggerAdapter logger;
        private final QueryCancellation cancellation;
        private volatile Future<?> worker;

        CancellableQueryFuture(
                String operation,
                boolean developmentThreadChecks,
                ILoggerAdapter logger,
                QueryCancellation cancellation
        ) {
            this.operation = operation;
            this.developmentThreadChecks = developmentThreadChecks;
            this.logger = logger;
            this.cancellation = cancellation;
        }

        QueryCancellation cancellation() {
            return cancellation;
        }

        @Override
        public boolean complete(T value) {
            if (cancellation.isCancelled()) {
                return false;
            }
            return super.complete(value);
        }

        void setWorker(Future<?> worker) {
            this.worker = worker;
            if (isDone() || cancellation.isCancelled()) {
                worker.cancel(true);
            }
        }

        void completeForClose() {
            cancellation.cancelDatabaseWork();
            completeExceptionally(new DataRegistryQueryExecutorClosedException());
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancellation.cancelDatabaseWork();
            Future<?> currentWorker = worker;
            if (currentWorker != null) {
                currentWorker.cancel(mayInterruptIfRunning);
            }
            return super.cancel(mayInterruptIfRunning);
        }

        @Override
        public T join() {
            warnBeforeBlocking("join()");
            return super.join();
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            warnBeforeBlocking("get()");
            return super.get();
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            warnBeforeBlocking("get(timeout)");
            return super.get(timeout, unit);
        }

        private void warnBeforeBlocking(String method) {
            if (!developmentThreadChecks || isDone() || !isLikelyServerEventThread()) {
                return;
            }
            logger.warn(
                    "DataRegistry query '" + operation + "' is being blocked with " + method +
                            " on likely server/event thread '" + Thread.currentThread().getName() + "'."
            );
        }
    }

    private static final class QueryCancellation {
        private final Set<Session> sessions = ConcurrentHashMap.newKeySet();
        private volatile boolean cancelled;

        void register(Session session) {
            sessions.add(session);
            if (cancelled) {
                cancel(session);
            }
        }

        void cancelDatabaseWork() {
            cancelled = true;
            sessions.forEach(QueryCancellation::cancel);
        }

        void clearSessions() {
            sessions.clear();
        }

        boolean isCancelled() {
            return cancelled;
        }

        private static void cancel(Session session) {
            try {
                session.cancelQuery();
            } catch (RuntimeException ignored) {
                // A failed cancellation is still followed by worker interruption and the JDBC driver's own cleanup.
            }
        }
    }

    private static final class NoopLogger implements ILoggerAdapter {
        @Override
        public void info(String message) {
        }

        @Override
        public void warn(String message) {
        }

        @Override
        public void error(String message) {
        }

        @Override
        public void info(String message, Throwable throwable) {
        }

        @Override
        public void warn(String message, Throwable throwable) {
        }

        @Override
        public void error(String message, Throwable throwable) {
        }
    }
}
