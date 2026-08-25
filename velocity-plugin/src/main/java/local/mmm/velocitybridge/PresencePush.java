package local.mmm.velocitybridge;

import java.util.UUID;

public record PresencePush(long sequence, UUID targetPlayerId, ProxyPresenceSnapshot snapshot) {
}
