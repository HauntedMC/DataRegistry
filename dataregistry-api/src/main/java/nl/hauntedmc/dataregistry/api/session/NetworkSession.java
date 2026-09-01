package nl.hauntedmc.dataregistry.api.session;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Canonical live ownership and location of one connected player. */
public record NetworkSession(
        long playerId,
        UUID playerUuid,
        String username,
        String proxyInstanceId,
        UUID proxyProcessEpoch,
        UUID sessionId,
        long sessionEpoch,
        long fencingToken,
        Optional<String> currentBackend,
        Optional<String> previousBackend,
        Optional<String> logicalDestination,
        Optional<String> logicalGroup,
        Instant connectedAt,
        Optional<Instant> serverConnectedAt,
        int protocolVersion,
        Set<SessionMetadataReference> metadataReferences,
        long revision,
        Instant leaseExpiresAt
) {
    public NetworkSession {
        if (playerId < 1) throw new IllegalArgumentException("playerId must be positive");
        Objects.requireNonNull(playerUuid, "playerUuid");
        username = text(username, "username");
        proxyInstanceId = SessionFence.requireProxyId(proxyInstanceId);
        Objects.requireNonNull(proxyProcessEpoch, "proxyProcessEpoch");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionEpoch < 1 || fencingToken < 1) {
            throw new IllegalArgumentException("sessionEpoch and fencingToken must be positive");
        }
        currentBackend = clean(currentBackend);
        previousBackend = clean(previousBackend);
        logicalDestination = clean(logicalDestination);
        logicalGroup = clean(logicalGroup);
        Objects.requireNonNull(connectedAt, "connectedAt");
        serverConnectedAt = serverConnectedAt == null ? Optional.empty() : serverConnectedAt;
        metadataReferences = metadataReferences == null ? Set.of() : Set.copyOf(metadataReferences);
        if (revision < 1) throw new IllegalArgumentException("revision must be positive");
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
    }

    public boolean ownedBy(String proxyId, UUID processEpoch, long expectedSessionEpoch) {
        return proxyInstanceId.equals(proxyId) && proxyProcessEpoch.equals(processEpoch)
                && sessionEpoch == expectedSessionEpoch;
    }

    public SessionFence fence() {
        return new SessionFence(proxyInstanceId, proxyProcessEpoch, sessionId, sessionEpoch, fencingToken);
    }

    public boolean matches(SessionFence expected) {
        return expected != null && fence().equals(expected);
    }

    private static Optional<String> clean(Optional<String> value) {
        if (value == null || value.isEmpty() || value.orElseThrow().isBlank()) return Optional.empty();
        return Optional.of(value.orElseThrow().trim());
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
}
