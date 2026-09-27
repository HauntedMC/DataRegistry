package nl.hauntedmc.dataregistry.core.player;

import java.util.UUID;

/** Value-free committed change notification. */
public record PlayerDataChange(UUID eventId, String capability, long playerId, UUID playerUuid,
                               long revision, long createdAt) { }
