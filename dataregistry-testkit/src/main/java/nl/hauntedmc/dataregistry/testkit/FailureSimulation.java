package nl.hauntedmc.dataregistry.testkit;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Failure helpers for exercising asynchronous API error paths in feature contract tests.
 */
public final class FailureSimulation {

    private FailureSimulation() {
    }

    public static <T> CompletionStage<T> failedStage(Throwable failure) {
        return CompletableFuture.failedFuture(Objects.requireNonNull(failure, "failure must not be null"));
    }

    /** Returns a stage that intentionally never completes for timeout and lifecycle tests. */
    public static <T> CompletableFuture<T> neverCompletingFuture() {
        return new CompletableFuture<>();
    }

    /** Returns an already-cancelled future for cancellation-path contract tests. */
    public static <T> CompletableFuture<T> cancelledFuture() {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.cancel(false);
        return future;
    }
}
