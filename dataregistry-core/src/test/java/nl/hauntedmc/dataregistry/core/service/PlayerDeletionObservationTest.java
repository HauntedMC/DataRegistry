package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.api.DataRegistryFeature;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryObservation;
import nl.hauntedmc.dataregistry.api.observation.DataRegistryOperationOutcome;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.core.DataRegistry;
import nl.hauntedmc.dataregistry.core.observation.DataRegistryObservations;
import nl.hauntedmc.dataregistry.platform.common.logger.ILoggerAdapter;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlayerDeletionObservationTest {

    @Test
    void deletionFailureIsOnePayloadFreeSemanticObservation() {
        DataRegistry dataRegistry = mock(DataRegistry.class);
        DataRegistryObservations observations = new DataRegistryObservations();
        AtomicReference<String> operation = new AtomicReference<>();
        AtomicReference<DataRegistryOperationOutcome> outcome = new AtomicReference<>();
        AtomicReference<Throwable> observedFailure = new AtomicReference<>();
        observations.registerObserver(context -> {
            operation.set(context.operation());
            return new DataRegistryObservation() {
                @Override
                public void completed(
                        DataRegistryOperationOutcome completedOutcome,
                        int attempts,
                        Throwable failure
                ) {
                    outcome.set(completedOutcome);
                    observedFailure.set(failure);
                }
            };
        });
        when(dataRegistry.internalObservations()).thenReturn(observations);
        when(dataRegistry.isFeatureEnabled(DataRegistryFeature.ONLINE_STATUS)).thenReturn(false);

        PlayerDeletionService service = new PlayerDeletionService(
                dataRegistry,
                mock(PlayerService.class),
                mock(ILoggerAdapter.class)
        );
        PlayerIdentity identity = new PlayerIdentity(42L, UUID.randomUUID(), "Alice");

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> service.delete(identity));

        assertEquals("player.delete", operation.get());
        assertEquals(DataRegistryOperationOutcome.FAILURE, outcome.get());
        assertSame(failure, observedFailure.get());
    }
}
