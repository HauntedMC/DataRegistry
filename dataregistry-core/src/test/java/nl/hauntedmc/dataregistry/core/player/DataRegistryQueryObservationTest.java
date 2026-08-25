package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationScope;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.dataregistry.core.observation.DataRegistryObservations;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class DataRegistryQueryObservationTest {

    @Test
    void workerScopeIsActiveAroundTheActualVirtualThreadWork() throws Exception {
        DataRegistryObservations observations = new DataRegistryObservations();
        ThreadLocal<Boolean> active = ThreadLocal.withInitial(() -> false);
        AtomicReference<Thread> scopeThread = new AtomicReference<>();
        AtomicReference<Thread> supplierThread = new AtomicReference<>();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();
        observations.registerObserver(context -> new DataRegistryObservation() {
            @Override
            public DataRegistryObservationScope openScope() {
                scopeThread.set(Thread.currentThread());
                active.set(true);
                return active::remove;
            }

            @Override
            public void completed(DataRegistryOperationOutcome completedOutcome, int attempts, Throwable failure) {
                outcome.set(completedOutcome);
            }
        });
        DataRegistryQueryExecutor executor = executor(observations, Duration.ofSeconds(2));

        try {
            int result = executor.supply("player.identity.lookup", () -> {
                supplierThread.set(Thread.currentThread());
                assertTrue(active.get());
                return 42;
            }).get(1, TimeUnit.SECONDS);

            assertEquals(42, result);
            assertEquals(supplierThread.get(), scopeThread.get());
            assertTrue(supplierThread.get().isVirtual());
            assertEquals(DataRegistryOperationOutcome.SUCCESS, outcome.get());
        } finally {
            executor.close();
        }
    }

    @Test
    void timeoutAndCancellationHaveStableTerminalOutcomes() throws Exception {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();
        observations.registerObserver(context -> recording(outcome));
        DataRegistryQueryExecutor executor = executor(observations, Duration.ofMillis(50));

        try {
            CompletableFuture<String> timedOut = executor.supply("player.identity.lookup", () -> {
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return "late";
            });
            assertThrows(ExecutionException.class, timedOut::get);
            assertEquals(DataRegistryOperationOutcome.TIMEOUT, outcome.get());

            CountDownLatch started = new CountDownLatch(1);
            CompletableFuture<String> cancelled = executor.supply("player.identity.bulk", () -> {
                started.countDown();
                try {
                    Thread.sleep(5_000L);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
                return "late";
            });
            assertTrue(started.await(1, TimeUnit.SECONDS));
            cancelled.cancel(true);
            assertEquals(DataRegistryOperationOutcome.CANCELLED, outcome.get());
        } finally {
            executor.close();
        }
    }

    @Test
    void supplyAfterCloseIsObservedAsClosed() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();
        observations.registerObserver(context -> recording(outcome));
        DataRegistryQueryExecutor executor = executor(observations, Duration.ofSeconds(1));
        executor.close();

        executor.supply("player.identity.lookup", () -> "unexpected");

        assertEquals(DataRegistryOperationOutcome.CLOSED, outcome.get());
    }

    private static DataRegistryQueryExecutor executor(
            DataRegistryObservations observations,
            Duration timeout
    ) {
        return new DataRegistryQueryExecutor(1, timeout, false, mock(ILoggerAdapter.class), observations);
    }

    private static DataRegistryObservation recording(AtomicReference<DataRegistryOperationOutcome> outcome) {
        return new DataRegistryObservation() {
            @Override
            public void completed(DataRegistryOperationOutcome completedOutcome, int attempts, Throwable failure) {
                outcome.set(completedOutcome);
            }
        };
    }
}
