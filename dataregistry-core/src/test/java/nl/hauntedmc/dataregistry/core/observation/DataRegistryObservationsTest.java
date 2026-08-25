package nl.hauntedmc.dataregistry.core.observation;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservationScope;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationContext;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DataRegistryObservationsTest {

    @Test
    void registrationIsRuntimeLocalAndDetachable() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicInteger starts = new AtomicInteger();
        AtomicReference<DataRegistryOperationContext> context = new AtomicReference<>();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();

        var registration = observations.registerObserver(operationContext -> {
            starts.incrementAndGet();
            context.set(operationContext);
            return recordingObservation(outcome, new AtomicInteger());
        });

        assertEquals(42, observations.observe("player.identity.lookup", () -> 42));
        assertEquals(1, starts.get());
        assertEquals("player.identity.lookup", context.get().operation());
        assertEquals(DataRegistryOperationOutcome.SUCCESS, outcome.get());

        registration.close();
        registration.close();
        assertEquals(7, observations.observe("player.identity.lookup", () -> 7));
        assertEquals(1, starts.get());
    }

    @Test
    void multipleObserversAndCallbackFailuresCannotChangeDataRegistryWork() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicInteger healthyStarts = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();

        observations.registerObserver(context -> {
            throw new IllegalStateException("start failure");
        });
        observations.registerObserver(context -> {
            healthyStarts.incrementAndGet();
            return new DataRegistryObservation() {
                @Override
                public DataRegistryObservationScope openScope() {
                    throw new IllegalStateException("scope failure");
                }

                @Override
                public void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure) {
                    completions.incrementAndGet();
                    throw new IllegalStateException("completion failure");
                }
            };
        });
        observations.registerObserver(context -> recordingObservation(new AtomicReference<>(), completions));

        assertEquals("ok", observations.observe("population.reconcile", () -> "ok"));
        assertEquals(1, healthyStarts.get());
        assertEquals(2, completions.get());
    }

    @Test
    void compositeCompletesEachObserverOnlyOnce() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicInteger first = new AtomicInteger();
        AtomicInteger second = new AtomicInteger();
        observations.registerObserver(context -> recordingObservation(new AtomicReference<>(), first));
        observations.registerObserver(context -> recordingObservation(new AtomicReference<>(), second));

        DataRegistryObservation observation = observations.start("registry.initialize");
        observations.complete(observation, DataRegistryOperationOutcome.SUCCESS, 1, null);
        observations.complete(observation, DataRegistryOperationOutcome.FAILURE, 2, new IllegalStateException());

        assertEquals(1, first.get());
        assertEquals(1, second.get());
    }

    @Test
    void scopeActivationWrapsTheObservedWork() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicBoolean active = new AtomicBoolean();
        AtomicBoolean closed = new AtomicBoolean();
        observations.registerObserver(context -> new DataRegistryObservation() {
            @Override
            public DataRegistryObservationScope openScope() {
                active.set(true);
                return () -> {
                    active.set(false);
                    closed.set(true);
                };
            }

            @Override
            public void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure) {
            }
        });

        assertTrue(observations.observe("registry.shutdown", active::get));
        assertFalse(active.get());
        assertTrue(closed.get());
    }

    @Test
    void futureObservationFinishesWhenTheOriginalFutureFinishes() {
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();
        AtomicInteger completions = new AtomicInteger();
        observations.registerObserver(context -> recordingObservation(outcome, completions));
        CompletableFuture<String> source = new CompletableFuture<>();

        CompletableFuture<String> returned = observations.observeFuture("player.readiness.wait", () -> source);
        assertTrue(returned == source);
        assertEquals(0, completions.get());

        source.complete("ready");
        assertEquals(1, completions.get());
        assertEquals(DataRegistryOperationOutcome.SUCCESS, outcome.get());
    }

    @Test
    void operationContextContainsOnlyTheBoundedOperationVocabulary() {
        List<String> components = Arrays.stream(DataRegistryOperationContext.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertEquals(List.of("operation"), components);
        assertEquals("player.lifecycle.login", new DataRegistryOperationContext("player.lifecycle.login").operation());
        assertThrows(IllegalArgumentException.class, () -> new DataRegistryOperationContext("Player UUID abc"));
        assertThrows(IllegalArgumentException.class, () -> new DataRegistryOperationContext("player lookup"));
    }

    private static DataRegistryObservation recordingObservation(
            AtomicReference<DataRegistryOperationOutcome> outcome,
            AtomicInteger completions
    ) {
        return new DataRegistryObservation() {
            @Override
            public void completed(
                    DataRegistryOperationOutcome completedOutcome,
                    int attempts,
                    Throwable failure
            ) {
                outcome.set(completedOutcome);
                completions.incrementAndGet();
            }
        };
    }
}
