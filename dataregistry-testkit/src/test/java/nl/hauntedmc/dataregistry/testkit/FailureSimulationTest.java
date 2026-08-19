package nl.hauntedmc.dataregistry.testkit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureSimulationTest {

    @Test
    void neverCompletingFutureStartsPendingAndCanBeCancelledByTheTest() {
        var future = FailureSimulation.<String>neverCompletingFuture();

        assertFalse(future.isDone());
        assertTrue(future.cancel(false));
        assertTrue(future.isCancelled());
    }

    @Test
    void cancelledFutureIsAlreadyCancelled() {
        var future = FailureSimulation.<String>cancelledFuture();

        assertTrue(future.isDone());
        assertTrue(future.isCancelled());
        assertThrows(CancellationException.class, future::join);
    }

    @Test
    void cancellationFailureIsExceptionalWithoutReportingFutureCancellation() {
        var future = FailureSimulation.<String>cancellationFailure().toCompletableFuture();

        assertFalse(future.isCancelled());
        CompletionException failure = assertThrows(CompletionException.class, future::join);
        assertInstanceOf(CancellationException.class, failure.getCause());
    }
}
