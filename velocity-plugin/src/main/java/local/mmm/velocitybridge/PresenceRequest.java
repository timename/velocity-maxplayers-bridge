package local.mmm.velocitybridge;

import java.util.UUID;

public record PresenceRequest(UUID requestId, UUID targetPlayerId) {
}
