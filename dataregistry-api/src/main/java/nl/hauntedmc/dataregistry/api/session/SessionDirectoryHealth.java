package nl.hauntedmc.dataregistry.api.session;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Operational state, completeness, and freshness of the distributed session projection. */
public record SessionDirectoryHealth(
        boolean available,
        boolean complete,
        boolean fresh,
        Instant checkedAt,
        Optional<Instant> lastCompleteSnapshotAt,
        Optional<String> failure
) {
    public SessionDirectoryHealth {
        Objects.requireNonNull(checkedAt, "checkedAt");
        lastCompleteSnapshotAt = lastCompleteSnapshotAt == null ? Optional.empty() : lastCompleteSnapshotAt;
        failure = failure == null ? Optional.empty() : failure;
        if (available && failure.isPresent()) throw new IllegalArgumentException("healthy directory cannot have failure");
        if ((complete || fresh) && lastCompleteSnapshotAt.isEmpty()) {
            throw new IllegalArgumentException("complete/fresh health requires a completed snapshot timestamp");
        }
        if (fresh && !complete) throw new IllegalArgumentException("a fresh directory must be complete");
    }
}
