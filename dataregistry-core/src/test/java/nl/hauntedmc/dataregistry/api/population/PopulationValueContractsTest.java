package nl.hauntedmc.dataregistry.api.population;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PopulationValueContractsTest {

    private static final Instant NOW = Instant.parse("2026-08-17T12:00:00Z");

    @Test
    void scopesNormalizeToStableStorageKeys() {
        assertEquals(PopulationScope.network(), new PopulationScope(PopulationScopeType.NETWORK, "NETWORK"));
        assertEquals("network", PopulationScope.network().storageKey());
        assertEquals("survival", PopulationScope.gamemode("  SURVIVAL  ").key());
        assertEquals("gamemode:survival", PopulationScope.gamemode("survival").storageKey());
        assertEquals("gamemode:skyblock-2", PopulationScope.gamemode("SkyBlock-2").storageKey());

        assertThrows(IllegalArgumentException.class, () -> PopulationScope.gamemode(""));
        assertThrows(IllegalArgumentException.class, () -> PopulationScope.gamemode("has spaces"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PopulationScope(PopulationScopeType.NETWORK, "survival")
        );
    }

    @Test
    void snapshotsRejectImpossibleCounts() {
        PopulationScope scope = PopulationScope.gamemode("survival");
        PopulationSnapshot snapshot = new PopulationSnapshot(
                scope,
                100L,
                7L,
                12L,
                NOW.minusSeconds(30),
                PopulationBaselineQuality.VERIFIED,
                PopulationBaselineQuality.TRACKED_ONLY,
                NOW
        );
        assertEquals(100L, snapshot.uniquePlayerCount());
        assertEquals(12L, snapshot.onlinePeak());

        assertThrows(IllegalArgumentException.class, () -> new PopulationSnapshot(
                scope, -1L, 0L, 0L, null,
                PopulationBaselineQuality.VERIFIED,
                PopulationBaselineQuality.VERIFIED,
                NOW
        ));
        assertThrows(IllegalArgumentException.class, () -> new PopulationSnapshot(
                scope, 1L, 3L, 2L, NOW,
                PopulationBaselineQuality.VERIFIED,
                PopulationBaselineQuality.VERIFIED,
                NOW
        ));
    }

    @Test
    void membershipRequiresStablePositiveIdentityAndOrdinal() {
        PlayerPopulationMembership membership = new PlayerPopulationMembership(
                42L,
                UUID.fromString("50000000-0000-0000-0000-000000000001"),
                "PopulationPlayer",
                PopulationScope.network(),
                1800L,
                PopulationOrdinalQuality.RECORDED_EXACT,
                NOW.minusSeconds(5),
                NOW
        );
        assertEquals(1800L, membership.ordinal());
        assertEquals(PopulationOrdinalQuality.RECORDED_EXACT, membership.ordinalQuality());

        assertThrows(IllegalArgumentException.class, () -> new PlayerPopulationMembership(
                0L,
                membership.uuid(),
                membership.username(),
                membership.scope(),
                membership.ordinal(),
                membership.ordinalQuality(),
                membership.firstJoinedAt(),
                membership.createdAt()
        ));
        assertThrows(IllegalArgumentException.class, () -> new PlayerPopulationMembership(
                membership.playerId(),
                membership.uuid(),
                membership.username(),
                membership.scope(),
                0L,
                membership.ordinalQuality(),
                membership.firstJoinedAt(),
                membership.createdAt()
        ));
    }

    @Test
    void transitionQueriesAreCursorBasedAndBounded() {
        PopulationTransitionQuery query = PopulationTransitionQuery.after(25L, 100)
                .withScope(PopulationScope.gamemode("survival"))
                .withTypes(Set.of(PopulationTransitionType.MEMBERSHIP_ADDED))
                .withCauses(Set.of(PopulationTransitionCause.LIVE));

        assertEquals(25L, query.afterId());
        assertEquals(100, query.limit());
        assertEquals(PopulationScope.gamemode("survival"), query.scope());
        assertEquals(Set.of(PopulationTransitionType.MEMBERSHIP_ADDED), query.types());
        assertEquals(Set.of(PopulationTransitionCause.LIVE), query.causes());

        assertThrows(
                IllegalArgumentException.class,
                () -> new PopulationTransitionQuery(-1L, 10, null, Set.of(), Set.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PopulationTransitionQuery(0L, 0, null, Set.of(), Set.of())
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PopulationTransitionQuery(0L, 1001, null, Set.of(), Set.of())
        );
    }

    @Test
    void transitionBatchReportsOnlyRealRetentionGaps() {
        PopulationTransition transition = new PopulationTransition(
                11L,
                PopulationTransitionType.ONLINE_CHANGED,
                PopulationTransitionCause.LIVE,
                PopulationScope.network(),
                7L,
                "survival-1",
                null,
                4L,
                5L,
                NOW
        );
        PopulationTransitionBatch batch = new PopulationTransitionBatch(11L, 20L, List.of(transition), NOW);

        assertTrue(batch.hasRetentionGapAfter(5L));
        assertFalse(batch.hasRetentionGapAfter(10L));
        assertFalse(batch.hasRetentionGapAfter(11L));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PopulationTransitionBatch(20L, 10L, List.of(), NOW)
        );
    }

    @Test
    void joinContextKeepsNetworkAndGamemodeEvidenceSeparate() {
        UUID uuid = UUID.fromString("50000000-0000-0000-0000-000000000002");
        PlayerIdentity identity = new PlayerIdentity(8L, uuid, "FirstJoinPlayer");
        PlayerPopulationMembership networkMembership = membership(identity, PopulationScope.network(), 2000L);
        PlayerPopulationMembership gamemodeMembership = membership(
                identity,
                PopulationScope.gamemode("survival"),
                500L
        );
        PopulationSnapshot networkSnapshot = snapshot(PopulationScope.network(), 2000L);
        PopulationSnapshot gamemodeSnapshot = snapshot(PopulationScope.gamemode("survival"), 500L);

        PopulationJoinContext context = new PopulationJoinContext(
                identity,
                "survival-1",
                Optional.of("survival"),
                true,
                true,
                networkMembership,
                Optional.of(gamemodeMembership),
                networkSnapshot,
                Optional.of(gamemodeSnapshot),
                NOW
        );

        assertTrue(context.networkFirstJoinThisSession());
        assertTrue(context.gamemodeFirstJoinThisVisit());
        assertEquals(2000L, context.networkMembership().ordinal());
        assertEquals(500L, context.gamemodeMembership().orElseThrow().ordinal());
    }

    private static PlayerPopulationMembership membership(
            PlayerIdentity identity,
            PopulationScope scope,
            long ordinal
    ) {
        return new PlayerPopulationMembership(
                identity.playerId(),
                identity.uuid(),
                identity.username(),
                scope,
                ordinal,
                PopulationOrdinalQuality.RECORDED_EXACT,
                NOW.minusSeconds(10),
                NOW
        );
    }

    private static PopulationSnapshot snapshot(PopulationScope scope, long uniquePlayers) {
        return new PopulationSnapshot(
                scope,
                uniquePlayers,
                5L,
                8L,
                NOW.minusSeconds(30),
                PopulationBaselineQuality.VERIFIED,
                PopulationBaselineQuality.VERIFIED,
                NOW
        );
    }
}
