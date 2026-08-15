package local.mmm.paperbridge;

import java.util.Optional;

record PresenceLookupResult(ProxyPresenceSnapshot snapshot, Optional<PendingPresenceRequest> pendingRequest) {
}
