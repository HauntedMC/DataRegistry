package nl.hauntedmc.dataregistry.testkit;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Supplies positive, process-local player ids for tests that must not use persisted identifiers.
 */
public final class TemporaryPlayerIds {

    private final AtomicLong nextId = new AtomicLong(1L);

    public long next() {
        return nextId.getAndIncrement();
    }
}
