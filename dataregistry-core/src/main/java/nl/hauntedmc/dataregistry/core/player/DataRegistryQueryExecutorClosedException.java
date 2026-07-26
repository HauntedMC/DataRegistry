package nl.hauntedmc.dataregistry.core.player;

/**
 * Signals that a public DataRegistry query could not complete because its executor was closed.
 */
public final class DataRegistryQueryExecutorClosedException extends IllegalStateException {

    public DataRegistryQueryExecutorClosedException() {
        super("DataRegistry query executor is closed.");
    }
}
