package nl.hauntedmc.dataregistry.core.observation;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationRegistration;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationScope;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObserver;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/** Runtime-local dispatcher that isolates optional observers from DataRegistry behavior. */
public final class DataRegistryObservations implements DataRegistryInstrumentation {

    private final CopyOnWriteArrayList<DataRegistryObserver> observers = new CopyOnWriteArrayList<>();

    @Override
    public DataRegistryObservationRegistration registerObserver(DataRegistryObserver observer) {
        Objects.requireNonNull(observer, "DataRegistry observer cannot be null.");
        observers.add(observer);
        return new Registration(observers, observer);
    }

    /** Returns whether at least one observer is currently registered. */
    public boolean isEnabled() {
        return !observers.isEmpty();
    }

    /** Starts one observation without allocating context on the unobserved fast path. */
    public DataRegistryObservation start(String operation) {
        if (!isEnabled()) {
            return DataRegistryObservation.noop();
        }
        DataRegistryOperationContext context = new DataRegistryOperationContext(operation);
        List<DataRegistryObservation> started = new ArrayList<>(observers.size());
        for (DataRegistryObserver observer : observers) {
            try {
                DataRegistryObservation observation = observer.start(context);
                if (observation != null && observation != DataRegistryObservation.noop()) {
                    started.add(observation);
                }
            } catch (RuntimeException ignored) {
                // Observability must never prevent the underlying DataRegistry operation from starting.
            }
        }
        if (started.isEmpty()) {
            return DataRegistryObservation.noop();
        }
        return new CompositeObservation(started);
    }

    /** Opens observer context while isolating adapter failures. */
    public DataRegistryObservationScope openScope(DataRegistryObservation observation) {
        if (observation == null || observation == DataRegistryObservation.noop()) {
            return DataRegistryObservationScope.noop();
        }
        try {
            DataRegistryObservationScope scope = observation.openScope();
            return scope == null ? DataRegistryObservationScope.noop() : scope;
        } catch (RuntimeException ignored) {
            return DataRegistryObservationScope.noop();
        }
    }

    /** Completes an observation while preserving the underlying operation outcome. */
    public void complete(
            DataRegistryObservation observation,
            DataRegistryOperationOutcome outcome,
            int attempts,
            Throwable failure
    ) {
        if (observation == null || observation == DataRegistryObservation.noop()) {
            return;
        }
        try {
            observation.completed(
                    Objects.requireNonNull(outcome, "DataRegistry operation outcome cannot be null."),
                    Math.max(1, attempts),
                    failure
            );
        } catch (RuntimeException ignored) {
            // Observability must never replace an operation result with an adapter failure.
        }
    }

    /** Observes one synchronous operation with generic success/failure classification. */
    public <T> T observe(String operation, Supplier<T> supplier) {
        Objects.requireNonNull(supplier, "Observed supplier cannot be null.");
        if (!isEnabled()) {
            return supplier.get();
        }
        DataRegistryObservation observation = start(operation);
        try (DataRegistryObservationScope ignored = openScope(observation)) {
            try {
                T result = supplier.get();
                complete(observation, DataRegistryOperationOutcome.SUCCESS, 1, null);
                return result;
            } catch (RuntimeException | Error failure) {
                complete(observation, DataRegistryOperationOutcome.FAILURE, 1, failure);
                throw failure;
            }
        }
    }

    /** Observes one synchronous void operation. */
    public void observe(String operation, Runnable runnable) {
        Objects.requireNonNull(runnable, "Observed runnable cannot be null.");
        observe(operation, () -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Observes a future without changing the future returned to the caller.
     * Used for readiness waits whose useful duration is the wait itself.
     */
    public <T> CompletableFuture<T> observeFuture(
            String operation,
            Supplier<CompletableFuture<T>> futureSupplier
    ) {
        Objects.requireNonNull(futureSupplier, "Observed future supplier cannot be null.");
        if (!isEnabled()) {
            return futureSupplier.get();
        }
        DataRegistryObservation observation = start(operation);
        final CompletableFuture<T> future;
        try (DataRegistryObservationScope ignored = openScope(observation)) {
            try {
                future = Objects.requireNonNull(futureSupplier.get(), "Observed future cannot be null.");
            } catch (RuntimeException | Error failure) {
                complete(observation, DataRegistryOperationOutcome.FAILURE, 1, failure);
                throw failure;
            }
        }
        future.whenComplete((result, failure) -> {
            Throwable terminalFailure = unwrap(failure);
            if (terminalFailure == null) {
                complete(observation, DataRegistryOperationOutcome.SUCCESS, 1, null);
            } else if (terminalFailure instanceof CancellationException) {
                complete(observation, DataRegistryOperationOutcome.CANCELLED, 1, terminalFailure);
            } else {
                complete(observation, DataRegistryOperationOutcome.FAILURE, 1, terminalFailure);
            }
        });
        return future;
    }

    private static Throwable unwrap(Throwable failure) {
        Throwable current = failure;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class Registration implements DataRegistryObservationRegistration {
        private final CopyOnWriteArrayList<DataRegistryObserver> observers;
        private final DataRegistryObserver observer;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Registration(CopyOnWriteArrayList<DataRegistryObserver> observers, DataRegistryObserver observer) {
            this.observers = observers;
            this.observer = observer;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                observers.remove(observer);
            }
        }
    }

    private static final class CompositeObservation implements DataRegistryObservation {
        private final List<DataRegistryObservation> observations;
        private final AtomicBoolean completed = new AtomicBoolean();

        private CompositeObservation(List<DataRegistryObservation> observations) {
            this.observations = List.copyOf(observations);
        }

        @Override
        public DataRegistryObservationScope openScope() {
            List<DataRegistryObservationScope> scopes = new ArrayList<>(observations.size());
            for (DataRegistryObservation observation : observations) {
                try {
                    DataRegistryObservationScope scope = observation.openScope();
                    if (scope != null && scope != DataRegistryObservationScope.noop()) {
                        scopes.add(scope);
                    }
                } catch (RuntimeException ignored) {
                    // One observer cannot prevent another observer or DataRegistry work from running.
                }
            }
            if (scopes.isEmpty()) {
                return DataRegistryObservationScope.noop();
            }
            return new CompositeScope(scopes);
        }

        @Override
        public void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure) {
            if (!completed.compareAndSet(false, true)) {
                return;
            }
            for (DataRegistryObservation observation : observations) {
                try {
                    observation.completed(outcome, attempts, failure);
                } catch (RuntimeException ignored) {
                    // Preserve DataRegistry completion and continue notifying remaining observers.
                }
            }
        }
    }

    private static final class CompositeScope implements DataRegistryObservationScope {
        private final List<DataRegistryObservationScope> scopes;
        private final AtomicBoolean closed = new AtomicBoolean();

        private CompositeScope(List<DataRegistryObservationScope> scopes) {
            this.scopes = List.copyOf(scopes);
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            for (int index = scopes.size() - 1; index >= 0; index--) {
                try {
                    scopes.get(index).close();
                } catch (RuntimeException ignored) {
                    // Scope cleanup must never change DataRegistry operation behavior.
                }
            }
        }
    }
}
