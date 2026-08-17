package local.mmm.paperbridge;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class PresenceLookupCache {

    private final Duration ttl;
    private final Duration shortTtl;
    private final Map<UUID, CachedPresence> cached = new HashMap<>();
    private final Map<UUID, PendingPresenceRequest> pending = new HashMap<>();

    PresenceLookupCache(Duration ttl) {
        this(ttl, ttl);
    }

    PresenceLookupCache(Duration ttl, Duration shortTtl) {
        this.ttl = ttl;
        this.shortTtl = shortTtl;
    }

    synchronized PresenceLookupResult lookup(UUID targetPlayerId, Instant now) {
        CachedPresence cachedPresence = cached.get(targetPlayerId);
        if (cachedPresence != null && cachedPresence.expiresAt().isAfter(now)) {
            return new PresenceLookupResult(cachedPresence.snapshot(), Optional.empty());
        }
        cached.remove(targetPlayerId);

        PendingPresenceRequest existingRequest = pending.get(targetPlayerId);
        if (existingRequest != null) {
            return new PresenceLookupResult(ProxyPresenceSnapshot.unknown(), Optional.empty());
        }

        PendingPresenceRequest request = new PendingPresenceRequest(UUID.randomUUID(), targetPlayerId);
        pending.put(targetPlayerId, request);
        return new PresenceLookupResult(ProxyPresenceSnapshot.unknown(), Optional.of(request));
    }

    synchronized void complete(ProxyPresenceResponse response, Instant now) {
        PendingPresenceRequest request = pending.get(response.targetPlayerId());
        if (request == null || !request.requestId().equals(response.requestId())) {
            return;
        }
        pending.remove(response.targetPlayerId());
        Duration responseTtl = response.snapshot().state() == ProxyPresenceState.ONLINE ? ttl : shortTtl;
        cached.put(response.targetPlayerId(), new CachedPresence(response.snapshot(), now.plus(responseTtl)));
    }

    synchronized void fail(PendingPresenceRequest request, Instant now) {
        if (pending.remove(request.targetPlayerId(), request)) {
            cached.put(request.targetPlayerId(), new CachedPresence(ProxyPresenceSnapshot.unknown(), now.plus(shortTtl)));
        }
    }

    private record CachedPresence(ProxyPresenceSnapshot snapshot, Instant expiresAt) {
    }
}
