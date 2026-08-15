package local.mmm.paperbridge;

import java.util.UUID;

record PendingPresenceRequest(UUID requestId, UUID targetPlayerId) {
}
