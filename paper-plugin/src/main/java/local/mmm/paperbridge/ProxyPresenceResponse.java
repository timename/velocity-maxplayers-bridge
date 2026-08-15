package local.mmm.paperbridge;

import java.util.UUID;

record ProxyPresenceResponse(UUID requestId, UUID targetPlayerId, ProxyPresenceSnapshot snapshot) {
}
