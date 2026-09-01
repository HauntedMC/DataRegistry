package nl.hauntedmc.dataregistry.api.session;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/** Platform SPI for the authoritative proxy; ordinary consumers use {@link NetworkSessionApi}. */
public interface NetworkSessionController {
    Optional<SessionAuthorityIdentity> localAuthority();

    CompletionStage<Boolean> updateLogicalRoute(
            UUID playerUuid, SessionFence expectedSession, String logicalDestination, String logicalGroup);

    CompletionStage<Boolean> updateMetadataReferences(
            UUID playerUuid, SessionFence expectedSession, Set<SessionMetadataReference> references);
}
