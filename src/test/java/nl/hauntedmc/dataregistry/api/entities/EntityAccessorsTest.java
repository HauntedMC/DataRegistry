package nl.hauntedmc.dataregistry.api.entities;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class EntityAccessorsTest {

    @Test
    void playerEntityAccessorsRoundTripValues() {
        PlayerEntity entity = new PlayerEntity();
        entity.setId(1L);
        entity.setUuid("f1575a14-20ce-4626-8fce-95a2576b853f");
        entity.setUsername("Alice");

        assertEquals(1L, entity.getId());
        assertEquals("f1575a14-20ce-4626-8fce-95a2576b853f", entity.getUuid());
        assertEquals("Alice", entity.getUsername());
    }

    @Test
    void playerOnlineStatusEntityAccessorsRoundTripValues() {
        PlayerEntity player = new PlayerEntity();
        player.setId(2L);
        PlayerOnlineStatusEntity status = new PlayerOnlineStatusEntity();
        status.setPlayerId(2L);
        status.setPlayer(player);
        status.setOnline(true);
        status.setCurrentServer("lobby");
        status.setPreviousServer("hub");

        assertEquals(2L, status.getPlayerId());
        assertSame(player, status.getPlayer());
        assertEquals(true, status.isOnline());
        assertEquals("lobby", status.getCurrentServer());
        assertEquals("hub", status.getPreviousServer());
    }

    @Test
    void playerConnectionInfoEntityAccessorsRoundTripValues() {
        PlayerEntity player = new PlayerEntity();
        player.setId(3L);
        PlayerConnectionInfoEntity info = new PlayerConnectionInfoEntity();
        Instant first = Instant.now().minusSeconds(60);
        Instant last = Instant.now().minusSeconds(20);
        Instant disconnect = Instant.now();
        info.setPlayerId(3L);
        info.setPlayer(player);
        info.setIpAddress("127.0.0.1");
        info.setFirstConnectionAt(first);
        info.setLastConnectionAt(last);
        info.setLastDisconnectAt(disconnect);
        info.setVirtualHost("mc.example.org:25565");

        assertEquals(3L, info.getPlayerId());
        assertSame(player, info.getPlayer());
        assertEquals("127.0.0.1", info.getIpAddress());
        assertEquals(first, info.getFirstConnectionAt());
        assertEquals(last, info.getLastConnectionAt());
        assertEquals(disconnect, info.getLastDisconnectAt());
        assertEquals("mc.example.org:25565", info.getVirtualHost());
    }

    @Test
    void playerSessionEntityAccessorsRoundTripValues() {
        PlayerEntity player = new PlayerEntity();
        player.setId(4L);
        PlayerSessionEntity session = new PlayerSessionEntity();
        Instant started = Instant.now().minusSeconds(120);
        Instant ended = Instant.now();
        session.setId(99L);
        session.setPlayer(player);
        session.setIpAddress("127.0.0.1");
        session.setVirtualHost("mc.example.org:25565");
        session.setStartedAt(started);
        session.setEndedAt(ended);
        session.setFirstServer("hub");
        session.setLastServer("lobby");

        assertEquals(99L, session.getId());
        assertSame(player, session.getPlayer());
        assertEquals("127.0.0.1", session.getIpAddress());
        assertEquals("mc.example.org:25565", session.getVirtualHost());
        assertEquals(started, session.getStartedAt());
        assertEquals(ended, session.getEndedAt());
        assertEquals("hub", session.getFirstServer());
        assertEquals("lobby", session.getLastServer());
    }

    @Test
    void playerLanguageEntityAccessorsRoundTripValues() {
        PlayerEntity player = new PlayerEntity();
        player.setId(5L);
        PlayerLanguageEntity language = new PlayerLanguageEntity();
        language.setPlayerId(5L);
        language.setPlayer(player);
        language.setLanguage("NL");

        assertEquals(5L, language.getPlayerId());
        assertSame(player, language.getPlayer());
        assertEquals("NL", language.getLanguage());
    }
}
