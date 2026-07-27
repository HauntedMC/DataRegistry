package nl.hauntedmc.dataregistry.core.lifecycle;

import nl.hauntedmc.dataregistry.api.player.PlayerIdentity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerLifecycleContractsTest {

    @Test
    void loginCommandNormalizesFieldsAndPreservesOptionalConnectionData() {
        UUID uuid = UUID.randomUUID();
        Instant occurredAt = Instant.parse("2026-07-27T12:00:00Z");

        LoginCommand command = new LoginCommand(
                "  event-1  ",
                "  " + uuid + "  ",
                "  Alice  ",
                "1.2.3.4",
                "play.example.net",
                occurredAt
        );

        assertEquals("event-1", command.eventId());
        assertEquals(uuid.toString(), command.playerUuid());
        assertEquals("Alice", command.username());
        assertEquals("1.2.3.4", command.ipAddress());
        assertEquals("play.example.net", command.virtualHost());
        assertEquals(occurredAt, command.occurredAt());
    }

    @Test
    void lifecycleCommandsDefaultMissingTimestamp() {
        UUID uuid = UUID.randomUUID();

        assertNotNull(new LoginCommand("login", uuid.toString(), "Alice", null, null, null).occurredAt());
        assertNotNull(new TransferCommand("transfer", uuid.toString(), "Alice", "hub", null).occurredAt());
        assertNotNull(new DisconnectCommand("disconnect", uuid.toString(), "Alice", null).occurredAt());
    }

    @Test
    void transferAndDisconnectCommandsNormalizeRequiredText() {
        UUID uuid = UUID.randomUUID();

        TransferCommand transfer = new TransferCommand(
                " transfer ",
                uuid.toString(),
                " Alice ",
                " Survival-01 ",
                Instant.EPOCH
        );
        DisconnectCommand disconnect = new DisconnectCommand(
                " disconnect ",
                uuid.toString(),
                " Alice ",
                Instant.EPOCH
        );

        assertEquals("transfer", transfer.eventId());
        assertEquals("Alice", transfer.username());
        assertEquals("Survival-01", transfer.serverName());
        assertEquals("disconnect", disconnect.eventId());
        assertEquals("Alice", disconnect.username());
    }

    @Test
    void lifecycleCommandsRejectInvalidIdentifiersAndRequiredText() {
        UUID uuid = UUID.randomUUID();

        assertThrows(NullPointerException.class, () -> new LoginCommand(null, uuid.toString(), "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LoginCommand(" ", uuid.toString(), "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LoginCommand("x".repeat(97), uuid.toString(), "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LoginCommand("event", "bad-uuid", "Alice", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new LoginCommand("event", uuid.toString(), " ", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> new TransferCommand("event", uuid.toString(), "Alice", " ", null));
        assertThrows(NullPointerException.class, () -> new DisconnectCommand("event", uuid.toString(), null, null));
    }

    @Test
    void eventIdAcceptsExactSchemaBoundary() {
        UUID uuid = UUID.randomUUID();
        String boundary = "x".repeat(96);

        assertEquals(boundary, new DisconnectCommand(boundary, uuid.toString(), "Alice", Instant.EPOCH).eventId());
    }

    @Test
    void factoryMethodsGenerateCanonicalUniqueTypedEventIds() {
        UUID uuid = UUID.randomUUID();

        LoginCommand firstLogin = LoginCommand.create(uuid.toString(), "Alice", null, null);
        LoginCommand secondLogin = LoginCommand.create(uuid.toString(), "Alice", null, null);
        TransferCommand transfer = TransferCommand.create(uuid.toString(), "Alice", "hub");
        DisconnectCommand disconnect = DisconnectCommand.create(uuid.toString(), "Alice");

        assertTrue(firstLogin.eventId().startsWith("login:" + uuid + ":"));
        assertTrue(transfer.eventId().startsWith("transfer:" + uuid + ":"));
        assertTrue(disconnect.eventId().startsWith("disconnect:" + uuid + ":"));
        assertNotEquals(firstLogin.eventId(), secondLogin.eventId());
    }

    @Test
    void successfulAndDuplicateResultsExposeIdentity() {
        PlayerIdentity identity = new PlayerIdentity(7L, UUID.randomUUID(), "Alice");
        PlayerLifecycleWriteResult success = PlayerLifecycleWriteResult.success("event", identity);
        PlayerLifecycleWriteResult duplicate = PlayerLifecycleWriteResult.duplicate("event", identity);

        assertTrue(success.succeeded());
        assertTrue(duplicate.succeeded());
        assertEquals(identity, success.identityOptional().orElseThrow());
        assertEquals(identity, duplicate.identityOptional().orElseThrow());
        assertNull(success.failure());
    }

    @Test
    void failureResultsPreserveCauseAndRejectSuccessfulStatuses() {
        RuntimeException failure = new RuntimeException("database unavailable");
        PlayerLifecycleWriteResult transientFailure = PlayerLifecycleWriteResult.failure(
                "event",
                PlayerLifecycleWriteStatus.TRANSIENT_FAILURE,
                failure
        );

        assertFalse(transientFailure.succeeded());
        assertTrue(transientFailure.identityOptional().isEmpty());
        assertEquals(failure, transientFailure.failure());
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerLifecycleWriteResult.failure("event", PlayerLifecycleWriteStatus.SUCCESS, failure)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> PlayerLifecycleWriteResult.failure("event", PlayerLifecycleWriteStatus.DUPLICATE, failure)
        );
    }
}
