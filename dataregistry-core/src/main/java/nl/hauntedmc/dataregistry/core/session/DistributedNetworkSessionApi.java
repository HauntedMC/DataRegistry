package nl.hauntedmc.dataregistry.core.session;

import nl.hauntedmc.dataprovider.database.coordination.CoordinationDataAccess;
import nl.hauntedmc.dataprovider.database.coordination.FencedLease;
import nl.hauntedmc.dataprovider.database.coordination.LeaseClaim;
import nl.hauntedmc.dataprovider.database.keyvalue.KeyValueDataAccess;
import nl.hauntedmc.dataprovider.database.keyvalue.KeyValueDatabaseProvider;
import nl.hauntedmc.dataregistry.api.session.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/** Redis-leased implementation shared by Velocity authorities and Paper readers. */
public class DistributedNetworkSessionApi implements NetworkSessionApi, NetworkSessionController {
    private final KeyValueDataAccess values;
    private final CoordinationDataAccess coordination;
    private final Duration leaseTtl;
    private final Duration expirySafetyMargin;
    private final Duration directoryFreshness;
    private final String sessionPrefix;
    private final String sessionIndex;
    private final String leasePrefix;
    private final String proxyInstanceId;
    private final UUID processEpoch;
    private final String owner;
    private final NetworkSessionCodec codec = new NetworkSessionCodec();
    private final ConcurrentMap<UUID, NetworkSession> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, OwnedSession> owned = new ConcurrentHashMap<>();
    private volatile boolean available = true;
    private volatile Instant checkedAt = Instant.now();
    private volatile Instant lastCompleteSnapshotAt;
    private volatile String failure;

    /** Creates a read-only directory, used by Paper. */
    public DistributedNetworkSessionApi(
            KeyValueDatabaseProvider provider,
            String namespace,
            Duration expirySafetyMargin,
            Duration directoryFreshness
    ) {
        this(provider, namespace, null, null, Duration.ofSeconds(15), expirySafetyMargin, directoryFreshness);
    }

    /** Creates an authoritative directory owner, used by a uniquely identified Velocity process. */
    public DistributedNetworkSessionApi(
            KeyValueDatabaseProvider provider,
            String namespace,
            String proxyInstanceId,
            UUID processEpoch,
            Duration leaseTtl,
            Duration expirySafetyMargin,
            Duration directoryFreshness
    ) {
        Objects.requireNonNull(provider, "provider");
        if (!provider.isConnected()) throw new IllegalStateException("Session Redis provider is not connected");
        values = Objects.requireNonNull(provider.getDataAccess(), "key-value access");
        coordination = Objects.requireNonNull(provider.getCoordinationDataAccess(), "coordination access");
        this.leaseTtl = requireTtl(leaseTtl);
        this.expirySafetyMargin = requireNonNegative(expirySafetyMargin, "expirySafetyMargin");
        this.directoryFreshness = requireTtl(directoryFreshness);
        if (this.expirySafetyMargin.compareTo(this.leaseTtl) >= 0) {
            throw new IllegalArgumentException("expirySafetyMargin must be shorter than leaseTtl");
        }
        String normalizedNamespace = requireNamespace(namespace);
        sessionPrefix = "dataregistry:" + normalizedNamespace + ":sessions:player:";
        sessionIndex = "dataregistry/" + normalizedNamespace + "/sessions";
        leasePrefix = "dataregistry/" + normalizedNamespace + "/session/player/";
        if ((proxyInstanceId == null) != (processEpoch == null)) {
            throw new IllegalArgumentException("proxyInstanceId and processEpoch must be supplied together");
        }
        this.proxyInstanceId = proxyInstanceId == null ? null
                : new SessionAuthorityIdentity(proxyInstanceId, processEpoch).proxyInstanceId();
        this.processEpoch = processEpoch;
        owner = proxyInstanceId == null ? null : this.proxyInstanceId + "/" + processEpoch;
    }

    @Override public SessionDirectoryHealth health() {
        Instant snapshot = lastCompleteSnapshotAt;
        boolean complete = snapshot != null;
        boolean fresh = complete && snapshot.plus(directoryFreshness).isAfter(Instant.now());
        return new SessionDirectoryHealth(available, complete, fresh, checkedAt,
                Optional.ofNullable(snapshot), Optional.ofNullable(failure));
    }
    @Override public Optional<SessionAuthorityIdentity> localAuthority() {
        return proxyInstanceId == null
                ? Optional.empty()
                : Optional.of(new SessionAuthorityIdentity(proxyInstanceId, processEpoch));
    }

    public CompletionStage<PendingSessionClaim> claimOwnership(UUID playerUuid, int protocolVersion) {
        requireAuthority();
        Objects.requireNonNull(playerUuid, "playerUuid");
        Instant connectedAt = Instant.now();
        UUID sessionId = UUID.randomUUID();
        return observe(coordination.claim(leaseResource(playerUuid), owner, leaseTtl))
                .thenApply(claim -> pending(playerUuid, protocolVersion, connectedAt, sessionId, claim));
    }

    public CompletionStage<NetworkSession> open(
            PendingSessionClaim claim,
            long playerId,
            String username
    ) {
        Objects.requireNonNull(claim, "claim");
        requireAuthority();
        if (!claim.lease().owner().equals(owner)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("Claim belongs to another process"));
        }
        NetworkSession session = new NetworkSession(
                playerId, claim.playerUuid(), username, proxyInstanceId, processEpoch, claim.sessionId(),
                claim.lease().fencingToken(), claim.lease().fencingToken(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), claim.connectedAt(), Optional.empty(), claim.protocolVersion(),
                Set.of(), 1, claim.lease().expiresAt()
        );
        return observe(coordination.writeFencedIndexed(claim.lease(), sessionKey(claim.playerUuid()),
                        codec.encode(session), leaseTtl, sessionIndex, claim.playerUuid().toString()))
                .thenCompose(written -> written ? completedOpen(claim.lease(), session)
                        : CompletableFuture.failedFuture(new IllegalStateException("Session claim was fenced before open")));
    }

    /** Releases a claim that never reached {@link #open(PendingSessionClaim, long, String)}. */
    public CompletionStage<Boolean> abandon(PendingSessionClaim claim) {
        Objects.requireNonNull(claim, "claim");
        requireAuthority();
        if (!claim.lease().owner().equals(owner)) {
            return CompletableFuture.completedFuture(false);
        }
        return observe(coordination.release(claim.lease()));
    }

    public CompletionStage<Boolean> changeBackend(UUID playerUuid, SessionFence expectedSession, String backend) {
        String normalizedBackend = requireText(backend, "backend");
        return mutateOwned(playerUuid, expectedSession, (current, renewed) -> new NetworkSession(
                current.playerId(), current.playerUuid(), current.username(), current.proxyInstanceId(),
                current.proxyProcessEpoch(), current.sessionId(), current.sessionEpoch(), current.fencingToken(),
                Optional.of(normalizedBackend), current.currentBackend(), current.logicalDestination(),
                current.logicalGroup(), current.connectedAt(), Optional.of(Instant.now()), current.protocolVersion(),
                current.metadataReferences(), current.revision() + 1, renewed.expiresAt()
        ));
    }

    public CompletionStage<Boolean> renew(UUID playerUuid, SessionFence expectedSession) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        OwnedSession entry = owned.get(playerUuid);
        if (entry == null || !entry.session.matches(expectedSession)) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.submit(() -> {
            if (owned.get(playerUuid) != entry || !entry.session.matches(expectedSession)) {
                return CompletableFuture.completedFuture(false);
            }
            return observe(coordination.renew(entry.lease, leaseTtl)).thenCompose(renewed -> {
                if (renewed.isEmpty()) return fenced(entry);
                NetworkSession current = entry.session;
                NetworkSession next = copy(current, current.logicalDestination(), current.logicalGroup(),
                        current.metadataReferences(), current.revision() + 1, renewed.orElseThrow().expiresAt());
                return write(entry, renewed.orElseThrow(), next);
            });
        });
    }

    public CompletionStage<Boolean> end(UUID playerUuid, SessionFence expectedSession) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        OwnedSession entry = owned.get(playerUuid);
        if (entry == null || !entry.session.matches(expectedSession)) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.submit(() -> {
            if (owned.get(playerUuid) != entry || !entry.session.matches(expectedSession)) {
                return CompletableFuture.completedFuture(false);
            }
            return observe(coordination.deleteFencedIndexed(entry.lease, sessionKey(playerUuid),
                    sessionIndex, playerUuid.toString())).thenCompose(deleted -> {
                if (!deleted) return fenced(entry);
                return observe(coordination.release(entry.lease)).thenApply(ignored -> {
                    owned.remove(playerUuid, entry);
                    cache.remove(playerUuid, entry.session);
                    return true;
                });
            });
        });
    }

    public Collection<NetworkSession> locallyOwnedSessions() {
        return owned.values().stream().map(value -> value.session).toList();
    }

    @Override
    public CompletionStage<Boolean> updateLogicalRoute(
            UUID playerUuid, SessionFence expectedSession, String destination, String group
    ) {
        Optional<String> normalizedDestination = optional(destination);
        Optional<String> normalizedGroup = optional(group);
        return mutateOwned(playerUuid, expectedSession, (current, renewed) -> copy(
                current, normalizedDestination, normalizedGroup, current.metadataReferences(),
                current.revision() + 1, renewed.expiresAt()));
    }

    @Override
    public CompletionStage<Boolean> updateMetadataReferences(
            UUID playerUuid, SessionFence expectedSession, Set<SessionMetadataReference> references
    ) {
        Set<SessionMetadataReference> safe = references == null ? Set.of() : Set.copyOf(references);
        return mutateOwned(playerUuid, expectedSession, (current, renewed) -> copy(
                current, current.logicalDestination(), current.logicalGroup(), safe,
                current.revision() + 1, renewed.expiresAt()));
    }

    @Override
    public Optional<NetworkSession> cached(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        NetworkSession session = cache.get(playerUuid);
        if (session != null && usable(session, Instant.now())) return Optional.of(session);
        if (session != null) cache.remove(playerUuid, session);
        return Optional.empty();
    }

    @Override
    public CompletionStage<Optional<NetworkSession>> find(UUID playerUuid) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        return observe(values.getKey(sessionKey(playerUuid))).thenApply(value -> decodeCurrent(playerUuid, value));
    }

    @Override
    public CompletionStage<Optional<NetworkSession>> findByUsername(String username) {
        String normalized = requireText(username, "username");
        return snapshot().thenApply(snapshot -> snapshot.sessions().values().stream()
                .filter(session -> session.username().equalsIgnoreCase(normalized)).findFirst());
    }

    @Override
    public CompletionStage<SessionDirectorySnapshot> snapshot() {
        return observe(coordination.readIndexedValues(sessionIndex)).thenApply(rows -> {
            Map<UUID, NetworkSession> sessions = new LinkedHashMap<>();
            Instant now = Instant.now();
            for (Map.Entry<String, String> row : rows.entrySet()) {
                String encoded = row.getValue();
                try {
                    NetworkSession session = codec.decode(encoded);
                    if (row.getKey().equals(session.playerUuid().toString()) && usable(session, now)) {
                        sessions.put(session.playerUuid(), session);
                        cache.merge(session.playerUuid(), session,
                                (oldValue, newValue) -> oldValue.revision() >= newValue.revision() ? oldValue : newValue);
                    }
                } catch (IllegalArgumentException ignored) {
                    // Invalid projections are omitted; authoritative owners will overwrite or expire them.
                }
            }
            lastCompleteSnapshotAt = now;
            return new SessionDirectorySnapshot(now, sessions);
        });
    }

    private CompletionStage<Boolean> mutateOwned(
            UUID playerUuid,
            SessionFence expectedSession,
            SessionMutation mutation
    ) {
        Objects.requireNonNull(playerUuid, "playerUuid");
        OwnedSession entry = owned.get(playerUuid);
        if (entry == null || !entry.session.matches(expectedSession)) {
            return CompletableFuture.completedFuture(false);
        }
        return entry.submit(() -> {
            if (owned.get(playerUuid) != entry || !entry.session.matches(expectedSession)) {
                return CompletableFuture.completedFuture(false);
            }
            return observe(coordination.renew(entry.lease, leaseTtl)).thenCompose(renewed -> {
                if (renewed.isEmpty()) return fenced(entry);
                NetworkSession next = mutation.apply(entry.session, renewed.orElseThrow());
                return write(entry, renewed.orElseThrow(), next);
            });
        });
    }

    private CompletionStage<Boolean> write(
            OwnedSession entry, FencedLease renewed, NetworkSession next
    ) {
        return observe(coordination.writeFencedIndexed(renewed, sessionKey(next.playerUuid()), codec.encode(next),
                leaseTtl, sessionIndex, next.playerUuid().toString()))
                .thenCompose(written -> {
                    if (!written) return fenced(entry);
                    entry.lease = renewed;
                    entry.session = next;
                    cache.put(next.playerUuid(), next);
                    return CompletableFuture.completedFuture(true);
                });
    }

    private CompletionStage<Boolean> fenced(OwnedSession entry) {
        owned.remove(entry.session.playerUuid(), entry);
        cache.remove(entry.session.playerUuid(), entry.session);
        return CompletableFuture.completedFuture(false);
    }

    private CompletionStage<NetworkSession> completedOpen(FencedLease lease, NetworkSession session) {
        OwnedSession entry = new OwnedSession(lease, session);
        owned.put(session.playerUuid(), entry);
        cache.put(session.playerUuid(), session);
        return CompletableFuture.completedFuture(session);
    }

    private Optional<NetworkSession> decodeCurrent(UUID expectedUuid, String encoded) {
        if (encoded == null) {
            cache.remove(expectedUuid);
            return Optional.empty();
        }
        NetworkSession session = codec.decode(encoded);
        if (!session.playerUuid().equals(expectedUuid) || !usable(session, Instant.now())) {
            cache.remove(expectedUuid);
            return Optional.empty();
        }
        cache.put(expectedUuid, session);
        return Optional.of(session);
    }

    private <T> CompletableFuture<T> observe(CompletableFuture<T> future) {
        return future.whenComplete((result, operationFailure) -> {
            checkedAt = Instant.now();
            available = operationFailure == null;
            failure = operationFailure == null ? null : operationFailure.getClass().getSimpleName()
                    + ": " + Objects.toString(operationFailure.getMessage(), "unknown failure");
        });
    }

    private void requireAuthority() {
        if (owner == null) throw new IllegalStateException("This session directory is read-only");
    }

    private PendingSessionClaim pending(
            UUID playerUuid, int protocolVersion, Instant connectedAt, UUID sessionId, LeaseClaim claim
    ) {
        return new PendingSessionClaim(playerUuid, sessionId, connectedAt, protocolVersion, claim.lease(),
                new SessionFence(proxyInstanceId, processEpoch, sessionId,
                        claim.lease().fencingToken(), claim.lease().fencingToken()),
                claim.previousOwner(), claim.previousFencingToken());
    }

    private static NetworkSession copy(
            NetworkSession current,
            Optional<String> destination,
            Optional<String> group,
            Set<SessionMetadataReference> references,
            long revision,
            Instant expiry
    ) {
        return new NetworkSession(current.playerId(), current.playerUuid(), current.username(),
                current.proxyInstanceId(), current.proxyProcessEpoch(), current.sessionId(), current.sessionEpoch(),
                current.fencingToken(), current.currentBackend(), current.previousBackend(), destination, group,
                current.connectedAt(), current.serverConnectedAt(), current.protocolVersion(), references, revision, expiry);
    }

    private String sessionKey(UUID playerUuid) { return sessionPrefix + playerUuid; }
    private String leaseResource(UUID playerUuid) { return leasePrefix + playerUuid; }
    private boolean usable(NetworkSession session, Instant now) {
        return session.leaseExpiresAt().minus(expirySafetyMargin).isAfter(now);
    }
    private static Optional<String> optional(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " cannot be blank");
        return value.trim();
    }
    private static Duration requireTtl(Duration value) {
        Objects.requireNonNull(value, "leaseTtl");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("leaseTtl must be positive");
        return value;
    }
    private static Duration requireNonNegative(Duration value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isNegative()) throw new IllegalArgumentException(field + " must not be negative");
        return value;
    }
    private static String requireNamespace(String value) {
        String normalized = requireText(value, "namespace").toLowerCase(java.util.Locale.ROOT);
        if (!normalized.matches("[a-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("namespace must match [a-z0-9._-]{1,64}");
        }
        return normalized;
    }

    @FunctionalInterface private interface SessionMutation {
        NetworkSession apply(NetworkSession current, FencedLease renewed);
    }
    private static final class OwnedSession {
        private volatile FencedLease lease;
        private volatile NetworkSession session;
        private CompletableFuture<Void> mutationTail = CompletableFuture.completedFuture(null);
        private OwnedSession(FencedLease lease, NetworkSession session) { this.lease = lease; this.session = session; }

        private synchronized CompletionStage<Boolean> submit(Supplier<CompletionStage<Boolean>> operation) {
            CompletableFuture<Boolean> result = mutationTail.handle((ignored, previousFailure) -> null)
                    .thenCompose(ignored -> {
                        try {
                            return operation.get();
                        } catch (RuntimeException failure) {
                            return CompletableFuture.failedFuture(failure);
                        }
                    }).toCompletableFuture();
            mutationTail = result.handle((ignored, failure) -> null);
            return result;
        }
    }
}
