package nl.hauntedmc.dataregistry.api.service;

/**
 * Handle for one feature service registration.
 */
public interface FeatureServiceHandle extends AutoCloseable {

    /**
     * Returns immutable metadata for this registration.
     */
    FeatureServiceInfo info();

    /**
     * Unregisters this exact registration when it is still current.
     */
    @Override
    void close();
}
