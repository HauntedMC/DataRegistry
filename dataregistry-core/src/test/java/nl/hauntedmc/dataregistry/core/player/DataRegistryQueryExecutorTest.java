package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.hibernate.Session;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static nl.hauntedmc.dataregistry.testutil.OrmTransactionTestSupport.executeTransactionsWithSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataRegistryQueryExecutorTest {

    @Test
    void deadlineCancelsTheActiveHibernateSessionAndUnblocksTheNextQuery() throws Exception {
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofMillis(100),
                false,
                mock(ILoggerAdapter.class)
        );
        Session session = mock(Session.class);
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch releaseBlockedQuery = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseBlockedQuery.countDown();
            return null;
        }).when(session).cancelQuery();

        try {
            CompletableFuture<String> timedOut = executor.supply("blocked", () -> {
                DataRegistryQueryExecutor.registerDatabaseSession(session);
                queryStarted.countDown();
                await(releaseBlockedQuery);
                return "late";
            });
            assertTrue(queryStarted.await(1, TimeUnit.SECONDS));

            assertThrows(ExecutionException.class, timedOut::get);
            CompletableFuture<String> next = executor.supply("next", () -> "available");
            assertEquals("available", next.get(1, TimeUnit.SECONDS));
            verify(session).cancelQuery();
        } finally {
            executor.close();
        }
    }

    @Test
    void deadlineAwareOrmContextRegistersTheTransactionSessionForCancellation() throws Exception {
        ORMContext delegate = mock(ORMContext.class);
        Session session = mock(Session.class);
        executeTransactionsWithSession(delegate, session);
        DeadlineAwareOrmContext ormContext = new DeadlineAwareOrmContext(delegate);
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofMillis(100),
                false,
                mock(ILoggerAdapter.class)
        );
        CountDownLatch transactionStarted = new CountDownLatch(1);
        CountDownLatch releaseTransaction = new CountDownLatch(1);
        doAnswer(invocation -> {
            releaseTransaction.countDown();
            return null;
        }).when(session).cancelQuery();

        try {
            CompletableFuture<Void> timedOut = executor.supply("orm", () -> ormContext.runInTransaction(ignored -> {
                transactionStarted.countDown();
                await(releaseTransaction);
                return null;
            }));
            assertTrue(transactionStarted.await(1, TimeUnit.SECONDS));

            assertThrows(ExecutionException.class, timedOut::get);
            assertTrue(releaseTransaction.await(1, TimeUnit.SECONDS));
            verify(session).cancelQuery();
        } finally {
            executor.close();
        }
    }

    @Test
    void closeCompletesQueuedAndRunningQueriesBeforeInterruptingWorkers() throws Exception {
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(30),
                false,
                mock(ILoggerAdapter.class)
        );
        CountDownLatch runningStarted = new CountDownLatch(1);
        CountDownLatch releaseRunningQuery = new CountDownLatch(1);
        AtomicBoolean queuedSupplierRan = new AtomicBoolean();
        Thread closeThread = null;
        try {
            CompletableFuture<String> running = executor.supply("running", () -> {
                runningStarted.countDown();
                awaitIgnoringInterrupts(releaseRunningQuery);
                return "late";
            });
            assertTrue(runningStarted.await(1, TimeUnit.SECONDS));
            CompletableFuture<String> queued = executor.supply("queued", () -> {
                queuedSupplierRan.set(true);
                return "unexpected";
            });

            closeThread = Thread.startVirtualThread(executor::close);

            assertClosed(running);
            assertClosed(queued);
            assertFalse(queuedSupplierRan.get());
        } finally {
            releaseRunningQuery.countDown();
            if (closeThread != null) {
                closeThread.join(3_000L);
                assertFalse(closeThread.isAlive());
            }
            executor.close();
        }
    }

    @Test
    void supplyAfterCloseReturnsAnExceptionallyCompletedFuture() {
        DataRegistryQueryExecutor executor = new DataRegistryQueryExecutor(
                1,
                Duration.ofSeconds(30),
                false,
                mock(ILoggerAdapter.class)
        );
        executor.close();

        CompletableFuture<String> query = executor.supply("after-close", () -> "unexpected");

        assertTrue(query.isDone());
        assertClosed(query);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test cancellation.", exception);
        }
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

    private static void assertClosed(CompletableFuture<?> query) {
        CompletionException failure = assertThrows(CompletionException.class, query::join);
        assertTrue(failure.getCause() instanceof DataRegistryQueryExecutorClosedException);
    }
}
