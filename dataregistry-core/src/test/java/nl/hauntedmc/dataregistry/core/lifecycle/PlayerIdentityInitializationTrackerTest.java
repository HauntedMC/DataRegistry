package nl.hauntedmc.dataregistry.core.lifecycle;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerIdentityInitializationTrackerTest {

    @Test
    void nullUuidIsImmediatelyUnavailableWithoutConsultingSupplier() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();

        Optional<PlayerIdentity> result = tracker.whenReady(null, () -> {
            throw new AssertionError("supplier must not be invoked");
        }).join();

        assertTrue(result.isEmpty());
    }

    @Test
    void activeIdentityIsReturnedImmediately() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        PlayerIdentity identity = identity(UUID.randomUUID(), 7L);

        assertEquals(Optional.of(identity), tracker.whenReady(identity.uuid(), () -> Optional.of(identity)).join());
    }

    @Test
    void supplierFailureIsExposedToCaller() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        IllegalStateException failure = new IllegalStateException("cache unavailable");

        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> tracker.whenReady(UUID.randomUUID(), () -> {
                    throw failure;
                }).join()
        );

        assertEquals(failure, exception.getCause());
    }

    @Test
    void pendingInitializationCompletesAllDefensiveFutureCopies() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = identity(uuid, 8L);
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization initialization = tracker.begin(uuid);

        CompletableFuture<Optional<PlayerIdentity>> first = tracker.whenReady(uuid, Optional::empty);
        CompletableFuture<Optional<PlayerIdentity>> second = initialization.future();

        assertNotSame(first, second);
        assertFalse(first.isDone());
        tracker.complete(initialization, identity);

        assertEquals(Optional.of(identity), first.join());
        assertEquals(Optional.of(identity), second.join());
        assertTrue(tracker.whenReady(uuid, Optional::empty).join().isEmpty());
    }

    @Test
    void newerAttemptSupersedesOldAttemptWithoutAllowingStaleCompletion() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization oldAttempt = tracker.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> oldFuture = oldAttempt.future();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization currentAttempt = tracker.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> currentFuture = currentAttempt.future();

        assertTrue(oldFuture.join().isEmpty());
        tracker.complete(oldAttempt, identity(uuid, 1L));
        assertFalse(currentFuture.isDone());

        PlayerIdentity currentIdentity = identity(uuid, 2L);
        tracker.complete(currentAttempt, currentIdentity);
        assertEquals(Optional.of(currentIdentity), currentFuture.join());
    }

    @Test
    void mismatchedIdentityCannotCompleteInitialization() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID expectedUuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization initialization = tracker.begin(expectedUuid);
        CompletableFuture<Optional<PlayerIdentity>> future = initialization.future();

        tracker.complete(initialization, identity(UUID.randomUUID(), 1L));

        assertFalse(future.isDone());
        tracker.completeUnavailable(initialization);
        assertTrue(future.join().isEmpty());
    }

    @Test
    void unavailableAndFailureOnlyAffectCurrentAttempt() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization first = tracker.begin(uuid);
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization second = tracker.begin(uuid);
        RuntimeException failure = new RuntimeException("failed");

        tracker.fail(first, failure);
        assertFalse(second.future().isDone());
        tracker.fail(second, failure);

        CompletionException exception = assertThrows(CompletionException.class, () -> second.future().join());
        assertEquals(failure, exception.getCause());
    }

    @Test
    void nullFailureUsesExplicitDefaultCause() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization initialization = tracker.begin(UUID.randomUUID());

        tracker.fail(initialization, null);

        CompletionException exception = assertThrows(CompletionException.class, () -> initialization.future().join());
        assertTrue(exception.getCause() instanceof IllegalStateException);
        assertTrue(exception.getCause().getMessage().contains("initialization failed"));
    }

    @Test
    void nullAndPostShutdownBeginsProduceUntrackedUnavailableHandles() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        assertTrue(tracker.begin(null).future().join().isEmpty());

        tracker.shutdown();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization afterShutdown = tracker.begin(UUID.randomUUID());

        assertTrue(afterShutdown.future().join().isEmpty());
        tracker.completeUnavailable(afterShutdown);
        assertTrue(afterShutdown.future().join().isEmpty());
    }

    @Test
    void shutdownCancelsPendingWaitersAndRejectsFutureWaits() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID uuid = UUID.randomUUID();
        tracker.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> pending = tracker.whenReady(uuid, Optional::empty);

        tracker.shutdown();
        tracker.shutdown();

        assertThrows(CancellationException.class, pending::join);
        CompletionException exception = assertThrows(
                CompletionException.class,
                () -> tracker.whenReady(uuid, Optional::empty).join()
        );
        assertTrue(exception.getCause() instanceof IllegalStateException);
    }

    @Test
    void callerCannotCompleteOrCancelInternalInitializationFuture() {
        PlayerIdentityInitializationTracker tracker = new PlayerIdentityInitializationTracker();
        UUID uuid = UUID.randomUUID();
        PlayerIdentityInitializationTracker.PlayerIdentityInitialization initialization = tracker.begin(uuid);
        CompletableFuture<Optional<PlayerIdentity>> callerFuture = initialization.future();

        callerFuture.complete(Optional.empty());
        callerFuture.cancel(true);
        PlayerIdentity identity = identity(uuid, 3L);
        tracker.complete(initialization, identity);

        assertEquals(Optional.of(identity), initialization.future().join());
    }

    private static PlayerIdentity identity(UUID uuid, long playerId) {
        return new PlayerIdentity(playerId, uuid, "Player" + playerId);
    }
}
