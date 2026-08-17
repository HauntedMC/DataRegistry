package nl.hauntedmc.dataregistry.api.population;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PopulationCursorSafetyTest {

    private static final Instant NOW = Instant.parse("2026-08-18T00:00:00Z");

    @Test
    void retentionGapCheckDoesNotOverflowAtMaximumCursor() {
        PopulationTransitionBatch batch = new PopulationTransitionBatch(10L, 20L, List.of(), NOW);

        assertFalse(batch.hasRetentionGapAfter(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> batch.hasRetentionGapAfter(-1L));
    }

    @Test
    void snapshotsRejectMoreOnlinePlayersThanKnownMembers() {
        assertThrows(IllegalArgumentException.class, () -> new PopulationSnapshot(
                PopulationScope.network(),
                2L,
                3L,
                3L,
                NOW,
                PopulationBaselineQuality.VERIFIED,
                PopulationBaselineQuality.VERIFIED,
                NOW
        ));
    }
}
