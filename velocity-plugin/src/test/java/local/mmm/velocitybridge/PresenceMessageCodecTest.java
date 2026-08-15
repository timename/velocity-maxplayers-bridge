package local.mmm.velocitybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresenceMessageCodecTest {

    private static final UUID REQUEST_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID TARGET_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Test
    void encodesPresenceRequestsWithTheVersionedWireContract() throws IOException {
        byte[] message = PresenceMessageCodec.encodeRequest(new PresenceRequest(REQUEST_ID, TARGET_ID));

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            assertEquals("presence_request", input.readUTF());
            assertEquals(1, input.readInt());
            assertEquals(REQUEST_ID, new UUID(input.readLong(), input.readLong()));
            assertEquals(TARGET_ID, new UUID(input.readLong(), input.readLong()));
        }
    }

    @Test
    void decodesAResponseAndRejectsMalformedData() {
        ProxyPresenceSnapshot snapshot = new ProxyPresenceSnapshot(
                ProxyPresenceState.OFFLINE_RECENT, 1_723_686_400_000L, "");
        byte[] response = PresenceMessageCodec.encodeResponse(
                new PresenceResponse(REQUEST_ID, TARGET_ID, snapshot));

        assertEquals(
                new PresenceResponse(REQUEST_ID, TARGET_ID, snapshot),
                PresenceMessageCodec.decodeResponse(response).orElseThrow()
        );
        assertTrue(PresenceMessageCodec.decodeResponse(new byte[] {0, 1, 2}).isEmpty());
    }
}
