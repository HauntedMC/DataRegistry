package nl.hauntedmc.dataregistry.api.playtime;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeCatalogTest {

    @Test
    void definitionsNormalizePolicyAndUnknownGamemodesFollowCatalogFallback() {
        PlaytimeCatalog catalog = new PlaytimeCatalog(true, List.of(
                new PlaytimeGamemodeDefinition(" Lobby ", true, true, false),
                new PlaytimeGamemodeDefinition("Staff", true, false, true),
                new PlaytimeGamemodeDefinition("Queue", false, true, true)
        ));

        assertEquals("lobby", catalog.find("LOBBY").orElseThrow().gamemodeKey());
        assertFalse(catalog.isCountedTowardsNetworkTotal("lobby"));
        assertFalse(catalog.isQueryable("staff"));
        assertFalse(catalog.find("queue").orElseThrow().tracked());
        assertTrue(catalog.isQueryable("survival"));
        assertTrue(catalog.isCountedTowardsNetworkTotal("survival"));
    }

    @Test
    void catalogRejectsDuplicateOrInvalidDefinitionsAndCanDisableUnknownFallback() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeCatalog(false, List.of(
                        new PlaytimeGamemodeDefinition("lobby", true, true, true),
                        new PlaytimeGamemodeDefinition("LOBBY", true, true, true)
                ))
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> new PlaytimeGamemodeDefinition("bad key", true, true, true)
        );

        PlaytimeCatalog catalog = PlaytimeCatalog.empty();
        assertFalse(catalog.isQueryable("survival"));
        assertTrue(catalog.find("survival").isEmpty());
    }
}
