package nl.hauntedmc.dataregistry.api.player;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerValueContractsTest {

    @Test
    void lookupFactoriesNormalizeAndClearIrrelevantFields() {
        UUID uuid = UUID.randomUUID();

        PlayerLookup byId = PlayerLookup.playerId(42L);
        PlayerLookup byUuid = PlayerLookup.uuid("  " + uuid + "  ");
        PlayerLookup byUsername = PlayerLookup.username("  Alice  ");
        PlayerLookup byIdentifier = PlayerLookup.identifier("  Alice-or-uuid  ");
        PlayerLookup hashIdentifier = PlayerLookup.identifier("  #42  ");

        assertEquals(PlayerLookup.Type.PLAYER_ID, byId.type());
        assertEquals(42L, byId.playerId());
        assertEquals(null, byId.uuid());
        assertEquals(null, byId.text());

        assertEquals(PlayerLookup.Type.UUID, byUuid.type());
        assertEquals(uuid, byUuid.uuid());
        assertEquals(uuid.toString(), byUuid.text());
        assertEquals(null, byUuid.playerId());

        assertEquals("Alice", byUsername.text());
        assertEquals(null, byUsername.playerId());
        assertEquals(null, byUsername.uuid());
        assertEquals(PlayerLookup.Type.IDENTIFIER, byIdentifier.type());
        assertEquals("Alice-or-uuid", byIdentifier.text());
        assertEquals(PlayerLookup.Type.IDENTIFIER, hashIdentifier.type());
        assertEquals("#42", hashIdentifier.text());
        assertEquals(null, hashIdentifier.playerId());
    }

    @Test
    void lookupRejectsInvalidConstructionAndInvalidUuidHelpersReturnEmpty() {
        assertThrows(NullPointerException.class, () -> new PlayerLookup(null, 1L, null, null));
        assertThrows(IllegalArgumentException.class, () -> PlayerLookup.playerId(0L));
        assertThrows(IllegalArgumentException.class, () -> PlayerLookup.playerId(-1L));
        assertThrows(NullPointerException.class, () -> PlayerLookup.uuid((UUID) null));
        assertThrows(IllegalArgumentException.class, () -> PlayerLookup.uuid("not-a-uuid"));
        assertThrows(IllegalArgumentException.class, () -> PlayerLookup.username("  "));
        assertThrows(IllegalArgumentException.class, () -> PlayerLookup.identifier(null));

        assertTrue(PlayerLookup.uuidIfValid(null).isEmpty());
        assertTrue(PlayerLookup.uuidIfValid(" ").isEmpty());
        assertTrue(PlayerLookup.uuidIfValid("not-a-uuid").isEmpty());
    }

    @Test
    void uuidIfValidNormalizesValidUuid() {
        UUID uuid = UUID.randomUUID();

        Optional<PlayerLookup> lookup = PlayerLookup.uuidIfValid("  " + uuid + "  ");

        assertEquals(PlayerLookup.uuid(uuid), lookup.orElseThrow());
    }

    @Test
    void pageRequestNormalizesCursorAndAcceptsBoundaryLimits() {
        assertEquals(null, new PlayerPageRequest("  ", 1).afterCursor());
        assertEquals("cursor", new PlayerPageRequest("  cursor  ", PlayerPageRequest.MAX_LIMIT).afterCursor());
        assertEquals(1, PlayerPageRequest.firstPage(1).limit());
        assertEquals(PlayerPageRequest.DEFAULT_LIMIT, PlayerPageRequest.firstPage(PlayerPageRequest.DEFAULT_LIMIT).limit());
    }

    @Test
    void pageRequestRejectsLimitsOutsideTheDocumentedRange() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerPageRequest(null, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlayerPageRequest(null, PlayerPageRequest.MAX_LIMIT + 1)
        );
    }

    @Test
    void playerPageDefensivelyCopiesItemsAndReportsCursorPresence() {
        List<String> mutableItems = new ArrayList<>(List.of("Alice"));
        PlayerPage<String> page = new PlayerPage<>(mutableItems, Optional.of("next"));
        mutableItems.add("Bob");

        assertEquals(List.of("Alice"), page.items());
        assertTrue(page.hasNext());
        assertThrows(UnsupportedOperationException.class, () -> page.items().add("Charlie"));
        assertFalse(new PlayerPage<>(List.of(), Optional.empty()).hasNext());
    }

    @Test
    void playerPageRejectsNullContainers() {
        assertThrows(NullPointerException.class, () -> new PlayerPage<>(null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PlayerPage<>(List.of(), null));
    }

    @Test
    void playerIdentityRequiresStablePositiveIdentityFields() {
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(1L, uuid, "Alice");

        assertEquals(1L, identity.playerId());
        assertEquals(uuid, identity.uuid());
        assertEquals("Alice", identity.username());

        assertThrows(IllegalArgumentException.class, () -> new PlayerIdentity(null, uuid, "Alice"));
        assertThrows(IllegalArgumentException.class, () -> new PlayerIdentity(0L, uuid, "Alice"));
        assertThrows(NullPointerException.class, () -> new PlayerIdentity(1L, null, "Alice"));
        assertThrows(NullPointerException.class, () -> new PlayerIdentity(1L, uuid, null));
    }

    @Test
    void profileQueryPreservesExplicitTimestampAndDefaultsNullTimestamp() {
        Instant timestamp = Instant.parse("2026-07-27T10:15:30Z");

        PlayerProfileQuery explicit = new PlayerProfileQuery(0, timestamp);
        PlayerProfileQuery defaulted = new PlayerProfileQuery(3, null);

        assertEquals(0, explicit.nameHistoryLimit());
        assertEquals(timestamp, explicit.asOf());
        assertNotNull(defaulted.asOf());
        assertThrows(IllegalArgumentException.class, () -> new PlayerProfileQuery(-1, timestamp));
    }

    @Test
    void profileResultRequiresEnvelopeFieldsAndReportsFoundState() {
        UUID uuid = UUID.randomUUID();
        PlayerLookup lookup = PlayerLookup.uuid(uuid);
        PlayerProfileQuery query = new PlayerProfileQuery(0, Instant.EPOCH);
        PlayerProfile profile = emptyProfile(new PlayerIdentity(1L, uuid, "Alice"));

        assertTrue(new PlayerProfileResult(lookup, query, Optional.of(profile)).found());
        assertFalse(new PlayerProfileResult(lookup, query, Optional.empty()).found());
        assertThrows(NullPointerException.class, () -> new PlayerProfileResult(null, query, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PlayerProfileResult(lookup, null, Optional.empty()));
        assertThrows(NullPointerException.class, () -> new PlayerProfileResult(lookup, query, null));
    }

    @Test
    void profileDefensivelyCopiesHistoryAndDerivesOnlineServerState() {
        UUID uuid = UUID.randomUUID();
        PlayerIdentity identity = new PlayerIdentity(1L, uuid, "Alice");
        PlayerNameHistoryEntry entry = new PlayerNameHistoryEntry(1L, 1L, "OldAlice", Instant.EPOCH);
        List<PlayerNameHistoryEntry> mutableHistory = new ArrayList<>(List.of(entry));
        PlayerProfile profile = new PlayerProfile(
                identity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new PlayerOnlineSnapshot(1L, true, "survival", "hub")),
                Optional.empty(),
                Optional.empty(),
                mutableHistory
        );
        mutableHistory.clear();

        assertTrue(profile.isOnline());
        assertEquals(Optional.of("survival"), profile.currentServer());
        assertEquals(List.of(entry), profile.nameHistory());
        assertThrows(UnsupportedOperationException.class, () -> profile.nameHistory().clear());
    }

    @Test
    void profileTreatsMissingOrOfflineStatusAsOfflineWithoutServer() {
        PlayerIdentity identity = new PlayerIdentity(1L, UUID.randomUUID(), "Alice");
        PlayerProfile missing = emptyProfile(identity);
        PlayerProfile offline = new PlayerProfile(
                identity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new PlayerOnlineSnapshot(1L, false, null, "hub")),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );

        assertFalse(missing.isOnline());
        assertTrue(missing.currentServer().isEmpty());
        assertFalse(offline.isOnline());
        assertTrue(offline.currentServer().isEmpty());
    }

    @Test
    void profileRejectsNullAggregateContainers() {
        PlayerIdentity identity = new PlayerIdentity(1L, UUID.randomUUID(), "Alice");

        assertThrows(NullPointerException.class, () -> new PlayerProfile(
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        ));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(
                identity,
                null,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        ));
        assertThrows(NullPointerException.class, () -> new PlayerProfile(
                identity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                null
        ));
    }

    private static PlayerProfile emptyProfile(PlayerIdentity identity) {
        return new PlayerProfile(
                identity,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of()
        );
    }
}
