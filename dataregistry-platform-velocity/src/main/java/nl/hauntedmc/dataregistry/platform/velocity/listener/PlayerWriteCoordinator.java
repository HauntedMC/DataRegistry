package nl.hauntedmc.dataregistry.platform.velocity.listener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Serializes database writes for one player without preventing writes for other players from running concurrently.
 * Lock entries are reference-counted and removed after the last owner leaves, so the coordinator does not retain every
 * UUID ever observed by a long-running proxy.
 */
final class PlayerWriteCoordinator {

    private final Map<String, LockEntry> entries = new HashMap<>();

    <T> T execute(String playerUuid, Supplier<T> action) {
        Objects.requireNonNull(playerUuid, "playerUuid must not be null");
        Objects.requireNonNull(action, "action must not be null");

        LockEntry entry = retain(playerUuid);
        entry.lock.lock();
        try {
            return action.get();
        } finally {
            entry.lock.unlock();
            release(playerUuid, entry);
        }
    }

    void execute(String playerUuid, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        execute(playerUuid, () -> {
            action.run();
            return null;
        });
    }

    synchronized int trackedPlayerCount() {
        return entries.size();
    }

    private synchronized LockEntry retain(String playerUuid) {
        LockEntry entry = entries.computeIfAbsent(playerUuid, ignored -> new LockEntry());
        entry.references++;
        return entry;
    }

    private synchronized void release(String playerUuid, LockEntry entry) {
        if (entry.references <= 0) {
            throw new IllegalStateException("Player write lock reference count underflow.");
        }
        entry.references--;
        if (entry.references == 0) {
            entries.remove(playerUuid, entry);
        }
    }

    private static final class LockEntry {
        private final ReentrantLock lock = new ReentrantLock(true);
        private int references;
    }
}
