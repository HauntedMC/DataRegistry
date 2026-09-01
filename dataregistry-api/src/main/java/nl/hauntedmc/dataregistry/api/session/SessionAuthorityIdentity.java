package nl.hauntedmc.dataregistry.api.session;

import java.util.Objects;
import java.util.UUID;

/** Stable proxy address plus the unique epoch of the process currently owning that address. */
public record SessionAuthorityIdentity(String proxyInstanceId, UUID processEpoch) {
    public SessionAuthorityIdentity {
        proxyInstanceId = SessionFence.requireProxyId(proxyInstanceId);
        Objects.requireNonNull(processEpoch, "processEpoch");
    }
}
