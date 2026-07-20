package nl.hauntedmc.dataregistry.testkit;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Failure helpers for exercising asynchronous API error paths in feature contract tests.
 */
public final class FailureSimulation {

    private FailureSimulation() {
    }

    public static <T> CompletionStage<T> failedStage(Throwable failure) {
        return CompletableFuture.failedFuture(failure);
    }
}
