package nl.hauntedmc.dataregistry.api;

import nl.hauntedmc.dataregistry.api.observation.DataRegistryInstrumentation;
import nl.hauntedmc.dataregistry.api.runtime.RuntimeIdentity;

import java.util.Optional;

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
     * Returns the physical runtime identity represented by this provider when it is available.
     *
     * <p>Identity is intentionally provider metadata rather than part of {@link DataRegistryApi}: it describes
     * the hosting process, not a DataRegistry-owned data domain. The default stays empty so existing custom provider
     * implementations remain source and binary compatible.</p>
     *
     * @return the current runtime identity, or empty when the provider cannot publish one safely.
     */
    default Optional<RuntimeIdentity> getRuntimeIdentity() {
        return Optional.empty();
    }

    /**
     * Returns the optional vendor-neutral instrumentation capability for the active runtime.
     *
     * <p>The default remains a no-op so existing provider implementations stay source and binary compatible.</p>
     */
    default DataRegistryInstrumentation getDataRegistryInstrumentation() {
        return DataRegistryInstrumentation.noop();
    }
}
