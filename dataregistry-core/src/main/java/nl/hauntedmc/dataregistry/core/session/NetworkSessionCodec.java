package nl.hauntedmc.dataregistry.core.session;

import nl.hauntedmc.dataregistry.api.session.NetworkSession;
import nl.hauntedmc.dataregistry.api.session.SessionMetadataReference;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class NetworkSessionCodec {
    private static final int FORMAT = 1;

    String encode(NetworkSession session) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(FORMAT);
                output.writeLong(session.playerId());
                uuid(output, session.playerUuid());
                output.writeUTF(session.username());
                output.writeUTF(session.proxyInstanceId());
                uuid(output, session.proxyProcessEpoch());
                uuid(output, session.sessionId());
                output.writeLong(session.sessionEpoch());
                output.writeLong(session.fencingToken());
                optional(output, session.currentBackend());
                optional(output, session.previousBackend());
                optional(output, session.logicalDestination());
                optional(output, session.logicalGroup());
                output.writeLong(session.connectedAt().toEpochMilli());
                output.writeBoolean(session.serverConnectedAt().isPresent());
                if (session.serverConnectedAt().isPresent()) output.writeLong(session.serverConnectedAt().orElseThrow().toEpochMilli());
                output.writeInt(session.protocolVersion());
                var references = session.metadataReferences().stream()
                        .sorted(Comparator.comparing(SessionMetadataReference::namespace)
                                .thenComparing(SessionMetadataReference::key))
                        .toList();
                output.writeInt(references.size());
                for (SessionMetadataReference reference : references) {
                    output.writeUTF(reference.namespace());
                    output.writeUTF(reference.key());
                    output.writeLong(reference.revision());
                }
                output.writeLong(session.revision());
                output.writeLong(session.leaseExpiresAt().toEpochMilli());
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes.toByteArray());
        } catch (IOException impossible) {
            throw new IllegalStateException("Could not encode network session", impossible);
        }
    }

    NetworkSession decode(String encoded) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(
                Base64.getUrlDecoder().decode(encoded)))) {
            if (input.readInt() != FORMAT) throw new IllegalArgumentException("Unsupported session format");
            long playerId = input.readLong();
            UUID playerUuid = uuid(input);
            String username = input.readUTF();
            String proxyId = input.readUTF();
            UUID processEpoch = uuid(input);
            UUID sessionId = uuid(input);
            long sessionEpoch = input.readLong();
            long fence = input.readLong();
            Optional<String> current = optional(input);
            Optional<String> previous = optional(input);
            Optional<String> destination = optional(input);
            Optional<String> group = optional(input);
            Instant connectedAt = Instant.ofEpochMilli(input.readLong());
            Optional<Instant> serverConnectedAt = input.readBoolean()
                    ? Optional.of(Instant.ofEpochMilli(input.readLong())) : Optional.empty();
            int protocol = input.readInt();
            int referenceCount = input.readInt();
            if (referenceCount < 0 || referenceCount > 256) throw new IllegalArgumentException("Invalid metadata count");
            Set<SessionMetadataReference> references = new LinkedHashSet<>();
            for (int index = 0; index < referenceCount; index++) {
                references.add(new SessionMetadataReference(input.readUTF(), input.readUTF(), input.readLong()));
            }
            long revision = input.readLong();
            Instant expiry = Instant.ofEpochMilli(input.readLong());
            if (input.available() != 0) throw new IllegalArgumentException("Trailing session data");
            return new NetworkSession(playerId, playerUuid, username, proxyId, processEpoch, sessionId,
                    sessionEpoch, fence, current, previous, destination, group, connectedAt,
                    serverConnectedAt, protocol, references, revision, expiry);
        } catch (IOException | IllegalArgumentException failure) {
            throw new IllegalArgumentException("Invalid encoded network session", failure);
        }
    }

    private static void uuid(DataOutputStream output, UUID value) throws IOException {
        output.writeLong(value.getMostSignificantBits());
        output.writeLong(value.getLeastSignificantBits());
    }
    private static UUID uuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
    private static void optional(DataOutputStream output, Optional<String> value) throws IOException {
        output.writeBoolean(value.isPresent());
        if (value.isPresent()) output.writeUTF(value.orElseThrow());
    }
    private static Optional<String> optional(DataInputStream input) throws IOException {
        return input.readBoolean() ? Optional.of(input.readUTF()) : Optional.empty();
    }
}
