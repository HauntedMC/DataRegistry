package nl.hauntedmc.dataregistry.api.observation;

/** Stable, low-cardinality terminal outcome for one observed DataRegistry operation. */
public enum DataRegistryOperationOutcome {
    SUCCESS,
    DUPLICATE,
    FAILURE,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE,
    TIMEOUT,
    CANCELLED,
    REJECTED,
    CLOSED;

    /** Returns whether the outcome represents unsuccessful completion. */
    public boolean isFailure() {
        return this != SUCCESS && this != DUPLICATE;
    }
}
