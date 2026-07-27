package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class DataRegistryQueryExecutorEdgeCaseTest {

    @Test
    void constructorRejectsInvalidExecutionConfiguration() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);

        assertThrows(IllegalArgumentException.class, () -> new DataRegistryQueryExecutor(
                0,
                Duration.ofSeconds(1),
                false,
                logger
        ));
        assertThrows(NullPointerException.class, () -> new DataRegistryQueryExecutor(1, null, false, logger));
        assertThrows(IllegalArgumentException.class, () -> new DataRegistryQueryExecutor(
                1,
                Duration.ZERO,
                false,
                logger
        ));
        assertThrows(IllegalArgumentException.class, () -> new DataRegistryQueryExecutor(
                1,
                Duration.ofMillis(-1),
                false,
                logger
        ));
        assertThrows(NullPointerException.class, () -> new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(1),
                false,
                null
        ));
    }

    @Test
    void supplyRejectsNullOperationAndSupplier() {
        DataRegistryQueryExecutor executor = DataRegistryQueryExecutor.immediateForTesting();

        assertThrows(NullPointerException.class, () -> executor.supply(null, () -> "value"));
        assertThrows(NullPointerException.class, () -> executor.supply("operation", null));
    }

    @Test
    void immediateExecutorCompletesSuccessAndFailureWithoutWrappingSupplierCause() {
        DataRegistryQueryExecutor executor = DataRegistryQueryExecutor.immediateForTesting();
        IllegalStateException failure = new IllegalStateException("broken");

        assertEquals("value", executor.supply("success", () -> "value").join());
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> executor.supply("failure", () -> {
                    throw failure;
                }).join()
        );

        assertEquals(failure, exception.getCause());
    }

    @Test
    void immediateExecutorCloseIsIdempotentAndRejectsLaterQueries() {
        DataRegistryQueryExecutor executor = DataRegistryQueryExecutor.immediateForTesting();

        executor.close();
        executor.close();

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> executor.supply("closed", () -> "value").join()
        );
        assertTrue(exception.getCause() instanceof DataRegistryQueryExecutorClosedException);
    }

    @Test
    void explicitCancellationCancelsRegisteredDatabaseSession() throws Exception {
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(10),
                false,
                mock(ILoggerAdapter.class)
        );
        Session session = mock(Session.class);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            CompletableFuture<String> query = executor.supply("cancel", () -> {
                DataRegistryQueryExecutor.registerDatabaseSession(session);
                started.countDown();
                awaitIgnoringInterrupts(release);
                return "late";
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));

            assertTrue(query.cancel(true));

            verify(session).cancelQuery();
            assertTrue(query.isCancelled());
        } finally {
            release.countDown();
            executor.close();
        }
    }

    @Test
    void sessionRegisteredAfterCancellationIsCancelledImmediately() throws Exception {
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(10),
                false,
                mock(ILoggerAdapter.class)
        );
        Session session = mock(Session.class);
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch registerSession = new CountDownLatch(1);
        CountDownLatch registered = new CountDownLatch(1);
        try {
            CompletableFuture<Void> query = executor.supply("late-session", () -> {
                workerStarted.countDown();
                awaitIgnoringInterrupts(registerSession);
                DataRegistryQueryExecutor.registerDatabaseSession(session);
                registered.countDown();
                return null;
            });
            assertTrue(workerStarted.await(1, TimeUnit.SECONDS));

            query.cancel(false);
            registerSession.countDown();
            assertTrue(registered.await(1, TimeUnit.SECONDS));

            verify(session).cancelQuery();
        } finally {
            registerSession.countDown();
            executor.close();
        }
    }

    @Test
    void developmentChecksWarnWhenQueryStartsOnLikelyMainThread() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(2),
                true,
                logger
        );
        try {
            executor.supply("lookup", () -> "value").join();

            verify(logger, atLeastOnce()).warn(contains("lookup"));
        } finally {
            executor.close();
        }
    }

    @Test
    void disabledDevelopmentChecksDoNotWarn() {
        ILoggerAdapter logger = mock(ILoggerAdapter.class);
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(2),
                false,
                logger
        );
        try {
            assertEquals("value", executor.supply("lookup", () -> "value").join());

            verify(logger, never()).warn(contains("lookup"));
        } finally {
            executor.close();
        }
    }

    @Test
    void nullDatabaseSessionRegistrationIsANoop() {
        DataRegistryQueryExecutor.registerDatabaseSession(null);
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        boolean interrupted = false;
        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
