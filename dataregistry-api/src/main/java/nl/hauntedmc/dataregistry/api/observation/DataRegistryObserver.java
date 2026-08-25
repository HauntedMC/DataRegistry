package nl.hauntedmc.dataregistry.api.observation;

/** Vendor-neutral observer for meaningful DataRegistry runtime and domain operations. */
@FunctionalInterface
public interface DataRegistryObserver {

    /**
     * Starts one observation. Implementations should be non-blocking and return a non-null handle.
     * DataRegistry isolates observer failures from the underlying runtime operation.
     */
    DataRegistryObservation start(DataRegistryOperationContext context);

    /** Returns the reusable no-op observer. */
    static DataRegistryObserver noop() {
        return NoopDataRegistryObserver.INSTANCE;
    }
}

enum NoopDataRegistryObserver implements DataRegistryObserver {
    INSTANCE;

    @Override
    public DataRegistryObservation start(DataRegistryOperationContext context) {
        return DataRegistryObservation.noop();
    }
}
