package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;

/**
 * Public platform capability that supplies the narrow DataRegistry API.
 *
 * <p>Platform plugins implement this interface so dependent features can obtain the public facade without
 * depending on platform implementation or persistence types.</p>
 */
public interface DataRegistryApiProvider {

    /**
     * Returns the active public DataRegistry facade.
     *
     * @return the persistence-agnostic API facade.
     */
    DataRegistryApi getDataRegistry();

    /**
     * Returns the optional vendor-neutral instrumentation capability for the active runtime.
     *
     * <p>The default remains a no-op so existing provider implementations stay source and binary compatible.</p>
     */
    default DataRegistryInstrumentation getDataRegistryInstrumentation() {
        return DataRegistryInstrumentation.noop();
    }
}
