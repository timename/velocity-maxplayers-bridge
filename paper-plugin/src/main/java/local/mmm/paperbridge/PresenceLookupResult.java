package local.mmm.paperbridge;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

record PresenceLookupResult(
        ProxyPresenceSnapshot snapshot,
        Optional<PendingPresenceRequest> pendingRequest,
        CompletableFuture<ProxyPresenceSnapshot> future
) {
}
