package nl.hauntedmc.dataregistry.core.persistence.repository;

import java.util.Objects;
import java.util.Set;

/**
 * Summary of an authoritative Velocity playtime-policy reconciliation.
 */
public record PlaytimePolicyReconciliationResult(
        Set<String> ignoredGamemodeKeys,
        Set<String> excludedFromNetworkTotalGamemodeKeys
) {

    public PlaytimePolicyReconciliationResult {
        ignoredGamemodeKeys = Set.copyOf(Objects.requireNonNull(
                ignoredGamemodeKeys,
                "ignoredGamemodeKeys must not be null"
        ));
        excludedFromNetworkTotalGamemodeKeys = Set.copyOf(Objects.requireNonNull(
                excludedFromNetworkTotalGamemodeKeys,
                "excludedFromNetworkTotalGamemodeKeys must not be null"
        ));
    }
}
