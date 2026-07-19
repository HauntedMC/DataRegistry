package nl.hauntedmc.dataregistry.api.service;

/**
 * Handle for a feature service registration.
 */
public interface FeatureServiceHandle extends AutoCloseable {

    /**
     * Returns immutable metadata for this registration.
     */
    FeatureServiceInfo info();

    /**
     * Unregisters this exact service instance.
     */
    @Override
    void close();
}
