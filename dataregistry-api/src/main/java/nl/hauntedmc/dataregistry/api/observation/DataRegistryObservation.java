package nl.hauntedmc.dataregistry.api.observation;

/** One in-flight DataRegistry operation returned by a {@link DataRegistryObserver}. */
public interface DataRegistryObservation {

    /**
     * Activates context for work that belongs to this observation.
     *
     * <p>This hook exists so adapters can propagate context across DataRegistry worker-thread boundaries
     * without DataRegistry depending on a tracing implementation.</p>
     */
    default DataRegistryObservationScope openScope() {
        return DataRegistryObservationScope.noop();
    }

    /**
     * Completes the observation exactly once.
     *
     * @param outcome stable terminal outcome
     * @param attempts number of attempts made by DataRegistry; at least one
     * @param failure terminal failure when one exists, otherwise {@code null}
     */
    void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure);

    /** Returns the reusable no-op observation. */
    static DataRegistryObservation noop() {
        return NoopDataRegistryObservation.INSTANCE;
    }
}

enum NoopDataRegistryObservation implements DataRegistryObservation {
    INSTANCE;

    @Override
    public void completed(DataRegistryOperationOutcome outcome, int attempts, Throwable failure) {
        // Intentionally empty.
    }
}
