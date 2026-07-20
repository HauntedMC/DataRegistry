package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;

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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Owns public DataRegistry query execution, deadlines, and cancellation plumbing.
 */
public final class DataRegistryQueryExecutor implements AutoCloseable {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 2L;

    private final ExecutorService queryExecutor;
    private final ScheduledExecutorService timeoutExecutor;
    private final Duration timeout;
    private final boolean developmentThreadChecks;
    private final ILoggerAdapter logger;
    private final boolean immediate;

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
        this.queryExecutor = Executors.newFixedThreadPool(workerThreads, namedFactory("DataRegistry-query-"));
        this.timeoutExecutor = Executors.newSingleThreadScheduledExecutor(namedFactory("DataRegistry-query-timeout-"));
    }

    private DataRegistryQueryExecutor() {
        this.queryExecutor = null;
        this.timeoutExecutor = null;
        this.timeout = Duration.ofSeconds(30L);
        this.developmentThreadChecks = false;
        this.logger = new NoopLogger();
        this.immediate = true;
    }

    public static DataRegistryQueryExecutor immediateForTesting() {
        return new DataRegistryQueryExecutor();
    }

    public <T> CompletableFuture<T> supply(String operation, Supplier<T> supplier) {
        Objects.requireNonNull(operation, "operation must not be null");
        Objects.requireNonNull(supplier, "supplier must not be null");
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
                logger
        );
        Future<?> worker = queryExecutor.submit(() -> {
            if (result.isDone()) {
                return;
            }
            try {
                result.complete(supplier.get());
            } catch (Throwable throwable) {
                result.completeExceptionally(throwable);
            }
        });
        result.setWorker(worker);

        Future<?> timeoutTask = timeoutExecutor.schedule(
                () -> {
                    TimeoutException exception = new TimeoutException(
                            "DataRegistry query '" + operation + "' exceeded " + timeout.toMillis() + "ms."
                    );
                    if (result.completeExceptionally(exception)) {
                        worker.cancel(true);
                    }
                },
                timeout.toMillis(),
                TimeUnit.MILLISECONDS
        );
        result.whenComplete((value, failure) -> timeoutTask.cancel(false));
        return result;
    }

    @Override
    public void close() {
        if (immediate) {
            return;
        }
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

    private static final class CancellableQueryFuture<T> extends CompletableFuture<T> {
        private final String operation;
        private final boolean developmentThreadChecks;
        private final ILoggerAdapter logger;
        private volatile Future<?> worker;

        CancellableQueryFuture(String operation, boolean developmentThreadChecks, ILoggerAdapter logger) {
            this.operation = operation;
            this.developmentThreadChecks = developmentThreadChecks;
            this.logger = logger;
        }

        void setWorker(Future<?> worker) {
            this.worker = worker;
            if (isCancelled()) {
                worker.cancel(true);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
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
