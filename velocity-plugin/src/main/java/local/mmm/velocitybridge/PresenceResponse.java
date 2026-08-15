package local.mmm.velocitybridge;

import java.util.UUID;

public record PresenceResponse(UUID requestId, UUID targetPlayerId, ProxyPresenceSnapshot snapshot) {
}
