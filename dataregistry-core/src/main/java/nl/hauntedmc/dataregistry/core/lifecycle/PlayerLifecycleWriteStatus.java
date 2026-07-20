package nl.hauntedmc.dataregistry.core.lifecycle;

/**
 * Final outcome category for a lifecycle command persistence attempt.
 */
public enum PlayerLifecycleWriteStatus {
    SUCCESS,
    DUPLICATE,
    TRANSIENT_FAILURE,
    PERMANENT_FAILURE
}
