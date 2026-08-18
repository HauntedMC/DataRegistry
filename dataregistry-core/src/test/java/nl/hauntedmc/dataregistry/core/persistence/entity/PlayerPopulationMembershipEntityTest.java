package nl.hauntedmc.dataregistry.core.persistence.entity;

import jakarta.persistence.Column;
import nl.hauntedmc.dataregistry.core.lifecycle.LoginCommand;
import nl.hauntedmc.dataregistry.core.lifecycle.TransferCommand;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerPopulationMembershipEntityTest {

    @Test
    void lifecycleCorrelationColumnSupportsLifecycleEventIdContract() throws Exception {
        Column membershipColumn = PlayerPopulationMembershipEntity.class
                .getDeclaredField("firstLifecycleEventId")
                .getAnnotation(Column.class);
        Column outboxColumn = PlayerLifecycleOutboxEntity.class
                .getDeclaredField("eventId")
                .getAnnotation(Column.class);

        String playerUuid = UUID.randomUUID().toString();
        String loginEventId = LoginCommand.create(playerUuid, "PopulationTest", null, null).eventId();
        String transferEventId = TransferCommand.create(playerUuid, "PopulationTest", "survival-1").eventId();

        assertTrue(loginEventId.length() > 64);
        assertTrue(transferEventId.length() > 64);
        assertEquals(outboxColumn.length(), membershipColumn.length());
        assertTrue(loginEventId.length() <= membershipColumn.length());
        assertTrue(transferEventId.length() <= membershipColumn.length());
    }
}
