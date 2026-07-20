package nl.hauntedmc.dataregistry.api;

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
}
