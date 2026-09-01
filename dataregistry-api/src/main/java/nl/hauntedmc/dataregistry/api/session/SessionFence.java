package nl.hauntedmc.dataregistry.api.session;

import java.util.Objects;
import java.util.UUID;

/** Exact identity of one live player session; all authoritative mutations must match every field. */
public record SessionFence(
        String proxyInstanceId,
        UUID proxyProcessEpoch,
        UUID sessionId,
        long sessionEpoch,
        long fencingToken
) {
    public SessionFence {
        proxyInstanceId = requireProxyId(proxyInstanceId);
        Objects.requireNonNull(proxyProcessEpoch, "proxyProcessEpoch");
        Objects.requireNonNull(sessionId, "sessionId");
        if (sessionEpoch < 1 || fencingToken < 1) {
            throw new IllegalArgumentException("sessionEpoch and fencingToken must be positive");
        }
    }

    static String requireProxyId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.matches("[A-Za-z0-9_.:-]{1,96}")) {
            throw new IllegalArgumentException("proxyInstanceId must match [A-Za-z0-9_.:-]{1,96}");
        }
        return normalized;
    }
}
