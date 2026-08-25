package nl.hauntedmc.dataregistry.api.observation;

import java.util.Objects;

/**
 * Optional runtime capability for registering vendor-neutral DataRegistry observers.
 *
 * <p>Registrations are runtime-local. Implementations must not install observers globally.</p>
 */
@FunctionalInterface
public interface DataRegistryInstrumentation {

    /** Registers one observer until the returned handle is closed. */
    DataRegistryObservationRegistration registerObserver(DataRegistryObserver observer);

    /** Returns a reusable no-op instrumentation capability. */
    static DataRegistryInstrumentation noop() {
        return observer -> {
            Objects.requireNonNull(observer, "DataRegistry observer cannot be null.");
            return DataRegistryObservationRegistration.noop();
        };
    }
}
