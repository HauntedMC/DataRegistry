package nl.hauntedmc.dataregistry.api.observation;

/**
 * Activates observer-specific context while DataRegistry executes work for an observation.
 *
 * <p>Implementations must be safe to close from the same thread that opened the scope.</p>
 */
@FunctionalInterface
public interface DataRegistryObservationScope extends AutoCloseable {

    @Override
    void close();

    /** Returns the reusable no-op scope. */
    static DataRegistryObservationScope noop() {
        return NoopDataRegistryObservationScope.INSTANCE;
    }
}

enum NoopDataRegistryObservationScope implements DataRegistryObservationScope {
    INSTANCE;

    @Override
    public void close() {
        // Intentionally empty.
    }
}
