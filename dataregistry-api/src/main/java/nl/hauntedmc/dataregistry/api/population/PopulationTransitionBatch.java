package nl.hauntedmc.dataregistry.api.population;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** One cursor page plus the retained transition range, allowing consumers to detect retention gaps. */
public record PopulationTransitionBatch(
        long earliestAvailableId,
        long latestAvailableId,
        List<PopulationTransition> transitions,
        Instant generatedAt
) {
    public PopulationTransitionBatch {
        if (earliestAvailableId < 0L || latestAvailableId < 0L) {
            throw new IllegalArgumentException("Transition bounds must not be negative.");
        }
        if ((earliestAvailableId == 0L) != (latestAvailableId == 0L)) {
            throw new IllegalArgumentException("Transition bounds must both be zero or both be positive.");
        }
        if (earliestAvailableId > 0L && latestAvailableId < earliestAvailableId) {
            throw new IllegalArgumentException("latestAvailableId must not precede earliestAvailableId.");
        }
        transitions = List.copyOf(Objects.requireNonNull(transitions, "transitions must not be null"));
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
    }

    public boolean hasRetentionGapAfter(long consumerCursor) {
        if (consumerCursor < 0L) {
            throw new IllegalArgumentException("consumerCursor must not be negative.");
        }
        return earliestAvailableId > 0L && consumerCursor < earliestAvailableId - 1L;
    }
}
