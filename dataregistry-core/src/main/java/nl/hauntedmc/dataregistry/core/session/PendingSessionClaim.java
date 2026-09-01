package nl.hauntedmc.dataregistry.core.session;

import nl.hauntedmc.dataprovider.database.coordination.FencedLease;
import nl.hauntedmc.dataregistry.api.session.SessionFence;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Ownership claim acquired before the durable player identity is opened as a live session. */
public record PendingSessionClaim(
        UUID playerUuid,
        UUID sessionId,
        Instant connectedAt,
        int protocolVersion,
        FencedLease lease,
        SessionFence fence,
        Optional<String> previousOwner,
        long previousFencingToken
) {
    public PendingSessionClaim {
        Objects.requireNonNull(playerUuid, "playerUuid");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(connectedAt, "connectedAt");
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(fence, "fence");
        previousOwner = previousOwner == null ? Optional.empty() : previousOwner;
    }
}
