package local.mmm.velocitybridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class PresenceMessageCodec {

    public static final String REQUEST_TYPE = "presence_request";
    public static final String RESPONSE_TYPE = "presence_response";
    public static final int PROTOCOL_VERSION = 1;

    private PresenceMessageCodec() {
    }

    public static byte[] encodeRequest(PresenceRequest request) {
        return write(output -> {
            output.writeUTF(REQUEST_TYPE);
            output.writeInt(PROTOCOL_VERSION);
            writeUuid(output, request.requestId());
            writeUuid(output, request.targetPlayerId());
        });
    }

    public static Optional<PresenceRequest> decodeRequest(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!REQUEST_TYPE.equals(input.readUTF()) || input.readInt() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            return Optional.of(new PresenceRequest(readUuid(input), readUuid(input)));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public static byte[] encodeResponse(PresenceResponse response) {
        return write(output -> {
            output.writeUTF(RESPONSE_TYPE);
            output.writeInt(PROTOCOL_VERSION);
            writeUuid(output, response.requestId());
            writeUuid(output, response.targetPlayerId());
            output.writeUTF(response.snapshot().state().name());
            output.writeLong(response.snapshot().lastDisconnectEpochMillis());
            output.writeUTF(response.snapshot().currentServer());
        });
    }

    public static Optional<PresenceResponse> decodeResponse(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            if (!RESPONSE_TYPE.equals(input.readUTF()) || input.readInt() != PROTOCOL_VERSION) {
                return Optional.empty();
            }
            UUID requestId = readUuid(input);
            UUID targetPlayerId = readUuid(input);
            ProxyPresenceState state = ProxyPresenceState.valueOf(input.readUTF());
            long lastDisconnectEpochMillis = input.readLong();
            String currentServer = input.readUTF();
            return Optional.of(new PresenceResponse(
                    requestId,
                    targetPlayerId,
                    new ProxyPresenceSnapshot(state, lastDisconnectEpochMillis, currentServer)
            ));
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static byte[] write(MessageWriter writer) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            writer.write(output);
            output.flush();
            return bytes.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("无法编码 Presence 消息", exception);
        }
    }

    private static void writeUuid(DataOutputStream output, UUID uniqueId) throws IOException {
        output.writeLong(uniqueId.getMostSignificantBits());
        output.writeLong(uniqueId.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream input) throws IOException {
        return new UUID(input.readLong(), input.readLong());
    }

    @FunctionalInterface
    private interface MessageWriter {
        void write(DataOutputStream output) throws IOException;
    }
}
