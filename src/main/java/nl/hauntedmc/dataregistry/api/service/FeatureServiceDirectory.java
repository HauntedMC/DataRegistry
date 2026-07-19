package nl.hauntedmc.dataregistry.api.service;

import java.util.List;
import java.util.Optional;

/**
 * Typed process-local catalog for APIs exported by enabled feature plugins.
 * <p>
 * DataRegistry owns the catalog, not the feature data behind each API. Feature plugins register narrow interfaces
 * for their own services, and consumers discover those interfaces without querying feature-owned tables directly.
 */
public interface FeatureServiceDirectory {

    /**
     * Registers or replaces a feature-owned service implementation for an API type.
     *
     * @param ownerPlugin owning plugin name, for diagnostics and cleanup.
     * @param ownerFeature owning feature name, for diagnostics and cleanup.
     * @param apiType interface or API class exposed to consumers.
     * @param service implementation instance owned by the feature.
     * @param <T> API type.
     * @return handle that unregisters this exact service instance when closed.
     */
    <T> FeatureServiceHandle register(String ownerPlugin, String ownerFeature, Class<T> apiType, T service);

    /**
     * Finds the currently registered service for the supplied API type.
     *
     * @param apiType API type to resolve.
     * @param <T> API type.
     * @return registered service, or empty when the owning feature is disabled or unavailable.
     */
    <T> Optional<T> find(Class<T> apiType);

    /**
     * Finds a required service and fails with a diagnostic message when it is unavailable.
     *
     * @param apiType API type to resolve.
     * @param <T> API type.
     * @return registered service.
     */
    <T> T require(Class<T> apiType);

    /**
     * Returns whether a service is registered for the supplied API type.
     */
    boolean contains(Class<?> apiType);

    /**
     * Returns metadata for the currently registered service for an API type.
     */
    Optional<FeatureServiceInfo> describe(Class<?> apiType);

    /**
     * Returns a stable snapshot of all currently registered feature services.
     */
    List<FeatureServiceInfo> list();

    /**
     * Unregisters an exact service instance.
     *
     * @return true when the registration was removed.
     */
    boolean unregister(Class<?> apiType, Object service);

    /**
     * Unregisters every service exported by the owner.
     *
     * @return number of registrations removed.
     */
    int unregisterOwner(String ownerPlugin, String ownerFeature);

    /**
     * Clears all registrations. Intended for DataRegistry shutdown and tests.
     */
    void clear();
}
