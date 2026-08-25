package local.mmm.paperbridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

final class PresenceMessageCodec {

    private static final String REQUEST_TYPE = "presence_request";
    private static final String RESPONSE_TYPE = "presence_response";
    private static final String PUSH_TYPE = "presence_push";
    private static final int PROTOCOL_VERSION = 1;

    private PresenceMessageCodec() {
    }

    static byte[] encodeRequest(PendingPresenceRequest request) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(REQUEST_TYPE);
            output.writeInt(PROTOCOL_VERSION);
            writeUuid(output, request.requestId());
            writeUuid(output, request.targetPlayerId());
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码 Presence 请求", exception);
        }
    }

    static Optional<ProxyPresenceResponse> decodeResponse(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!RESPONSE_TYPE.equals(input.readUTF()) || input.readInt() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            UUID requestId = readUuid(input);
            UUID targetPlayerId = readUuid(input);
            ProxyPresenceState state = ProxyPresenceState.valueOf(input.readUTF());
            long lastDisconnectEpochMillis = input.readLong();
            String currentServer = input.readUTF();
            return Optional.of(new ProxyPresenceResponse(
                    requestId,
                    targetPlayerId,
                    new ProxyPresenceSnapshot(state, lastDisconnectEpochMillis, currentServer)
            ));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static byte[] encodePush(PresencePush push) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF(PUSH_TYPE);
            output.writeInt(PROTOCOL_VERSION);
            output.writeLong(push.sequence());
            writeUuid(output, push.targetPlayerId());
            output.writeUTF(push.snapshot().state().name());
            output.writeLong(push.snapshot().lastDisconnectEpochMillis());
            output.writeUTF(push.snapshot().currentServer());
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码 Presence 推送", exception);
        }
    }

    static Optional<PresencePush> decodePush(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!PUSH_TYPE.equals(input.readUTF()) || input.readInt() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            long sequence = input.readLong();
            UUID targetPlayerId = readUuid(input);
            ProxyPresenceState state = ProxyPresenceState.valueOf(input.readUTF());
            long lastDisconnectEpochMillis = input.readLong();
            String currentServer = input.readUTF();
            return Optional.of(new PresencePush(sequence, targetPlayerId, new ProxyPresenceSnapshot(
                    state, lastDisconnectEpochMillis, currentServer)));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static void writeUuid(DataOutputStream output, UUID uniqueId) throws IOException {
        output.writeLong(uniqueId.getMostSignificantBits());
        output.writeLong(uniqueId.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }
}
