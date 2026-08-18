package nl.hauntedmc.dataregistry.core.service;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Result of permanently removing one canonical player identity and its dependent rows. */
public record PlayerDeletionResult(
        PlayerIdentity deletedIdentity,
        Map<String, Integer> deletedRowsByTable
) {

    public PlayerDeletionResult {
        deletedIdentity = Objects.requireNonNull(deletedIdentity, "deletedIdentity must not be null");
        Objects.requireNonNull(deletedRowsByTable, "deletedRowsByTable must not be null");
        deletedRowsByTable = Map.copyOf(new LinkedHashMap<>(deletedRowsByTable));
        if (deletedRowsByTable.values().stream().anyMatch(count -> count == null || count < 0)) {
            throw new IllegalArgumentException("Deleted row counts must not be negative.");
        }
    }

    public int deletedDependentRows() {
        return deletedRowsByTable.values().stream().mapToInt(Integer::intValue).sum();
    }

    public long deletedTableCount() {
        return deletedRowsByTable.values().stream().filter(count -> count > 0).count();
    }
}
