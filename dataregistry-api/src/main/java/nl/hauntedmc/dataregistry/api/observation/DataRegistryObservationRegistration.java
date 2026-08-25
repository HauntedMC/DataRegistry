package nl.hauntedmc.dataregistry.api.observation;

/** Registration handle for one DataRegistry observer. Closing it detaches only that observer. */
@FunctionalInterface
public interface DataRegistryObservationRegistration extends AutoCloseable {

    @Override
    void close();

    /** Returns a reusable no-op registration. */
    static DataRegistryObservationRegistration noop() {
        return NoopDataRegistryObservationRegistration.INSTANCE;
    }
}

enum NoopDataRegistryObservationRegistration implements DataRegistryObservationRegistration {
    INSTANCE;

    @Override
    public void close() {
        // Intentionally empty.
    }
}
