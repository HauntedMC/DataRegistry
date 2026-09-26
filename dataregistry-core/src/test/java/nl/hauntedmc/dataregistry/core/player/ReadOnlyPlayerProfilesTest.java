package nl.hauntedmc.dataregistry.core.player;

import nl.hauntedmc.dataregistry.api.player.PlayerActivitySnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerConnectionSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import nl.hauntedmc.dataregistry.api.player.PlayerOnlineSnapshot;
import nl.hauntedmc.dataregistry.api.player.PlayerProfile;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ReadOnlyPlayerProfilesTest {
    @Test void projectionExcludesConnectionAndLiveState() {
        Instant firstSeen = Instant.parse("2026-01-01T00:00:00Z");
        PlayerProfile source = new PlayerProfile(
                new PlayerIdentity(42L, UUID.randomUUID(), "PlayerOne"),
                Optional.empty(), Optional.empty(),
                Optional.of(new PlayerConnectionSnapshot(42L, "192.0.2.10", firstSeen, firstSeen, null, "secret.example")),
                Optional.of(new PlayerOnlineSnapshot(42L, true, "survival", null)),
                Optional.of(new PlayerActivitySnapshot(42L, firstSeen, firstSeen, firstSeen, null)),
                Optional.empty(), List.of());
        ReadOnlyPlayerProfiles.Summary summary = ReadOnlyPlayerProfiles.summary(source);
        assertEquals("PlayerOne", summary.name());
        assertEquals(firstSeen, summary.firstSeenAt());
        assertFalse(summary.toString().contains("192.0.2.10"));
        assertFalse(summary.toString().contains("survival"));
        assertFalse(summary.toString().contains("secret.example"));
    }
}
