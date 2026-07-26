package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataprovider.api.orm.ORMContext;

import java.util.Objects;

/**
 * Registers the active Hibernate session with the public-query deadline controller while preserving DataProvider's
 * transaction ownership.
 */
public final class DeadlineAwareOrmContext implements ORMContext {

    private final ORMContext delegate;

    public DeadlineAwareOrmContext(ORMContext delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
    }

    @Override
    public <T> T runInTransaction(TransactionCallback<T> callback) {
        Objects.requireNonNull(callback, "callback must not be null");
        return delegate.runInTransaction(session -> {
            DataRegistryQueryExecutor.registerDatabaseSession(session);
            return callback.execute(session);
        });
    }

    @Override
    public void shutdown() {
        delegate.shutdown();
    }
}
