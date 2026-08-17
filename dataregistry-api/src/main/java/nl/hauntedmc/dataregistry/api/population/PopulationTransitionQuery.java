package nl.hauntedmc.dataregistry.api.population;

import java.util.Objects;
import java.util.Set;

/** Cursor-based population transition query with optional scope/type/cause filters. */
public record PopulationTransitionQuery(
        long afterId,
        int limit,
        PopulationScope scope,
        Set<PopulationTransitionType> types,
        Set<PopulationTransitionCause> causes
) {
    public PopulationTransitionQuery {
        if (afterId < 0L) {
            throw new IllegalArgumentException("afterId must not be negative.");
        }
        if (limit < 1 || limit > 1000) {
            throw new IllegalArgumentException("limit must be between 1 and 1000.");
        }
        types = Set.copyOf(Objects.requireNonNull(types, "types must not be null"));
        causes = Set.copyOf(Objects.requireNonNull(causes, "causes must not be null"));
    }

    public static PopulationTransitionQuery after(long afterId, int limit) {
        return new PopulationTransitionQuery(afterId, limit, null, Set.of(), Set.of());
    }

    public PopulationTransitionQuery withScope(PopulationScope newScope) {
        return new PopulationTransitionQuery(afterId, limit, newScope, types, causes);
    }

    public PopulationTransitionQuery withTypes(Set<PopulationTransitionType> newTypes) {
        return new PopulationTransitionQuery(afterId, limit, scope, newTypes, causes);
    }

    public PopulationTransitionQuery withCauses(Set<PopulationTransitionCause> newCauses) {
        return new PopulationTransitionQuery(afterId, limit, scope, types, newCauses);
    }
}
