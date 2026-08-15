package nl.hauntedmc.dataregistry.platform.velocity.listener;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerWriteCoordinatorTest {

    @Test
    void serializesSamePlayerAndReleasesUnusedLockEntry() throws Exception {
        PlayerWriteCoordinator coordinator = new PlayerWriteCoordinator();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondEntered = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> coordinator.execute("player-1", () -> {
                firstEntered.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            Future<?> second = executor.submit(() -> coordinator.execute(
                    "player-1",
                    secondEntered::countDown
            ));
            assertFalse(secondEntered.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);

            assertEquals(0L, secondEntered.getCount());
            assertEquals(0, coordinator.trackedPlayerCount());
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void permitsDifferentPlayersToRunConcurrently() throws Exception {
        PlayerWriteCoordinator coordinator = new PlayerWriteCoordinator();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch bothEntered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);

        try {
            Future<?> first = executor.submit(() -> coordinator.execute("player-1", () -> {
                bothEntered.countDown();
                await(release);
            }));
            Future<?> second = executor.submit(() -> coordinator.execute("player-2", () -> {
                bothEntered.countDown();
                await(release);
            }));

            assertTrue(bothEntered.await(1, TimeUnit.SECONDS));
            release.countDown();
            first.get(1, TimeUnit.SECONDS);
            second.get(1, TimeUnit.SECONDS);
            assertEquals(0, coordinator.trackedPlayerCount());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void releasesLockEntryWhenActionFails() {
        PlayerWriteCoordinator coordinator = new PlayerWriteCoordinator();

        assertThrows(IllegalStateException.class, () -> coordinator.execute(
                "player-1",
                () -> {
                    throw new IllegalStateException("write failed");
                }
        ));

        assertEquals(0, coordinator.trackedPlayerCount());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting in test.", exception);
        }
    }
}
