package local.mmm.paperbridge;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

final class PresenceLookupCache {

    private static final int MAX_PENDING_REQUESTS = 1_024;
    private final Duration ttl;
    private final Duration shortTtl;
    private final Map<UUID, CachedPresence> cached = new HashMap<>();
    private final Map<UUID, PendingLookup> pending = new HashMap<>();

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
            return completedResult(cachedPresence.snapshot());
        }

        PendingLookup existingLookup = pending.get(targetPlayerId);
        if (existingLookup != null) {
            return new PresenceLookupResult(
                    staleSnapshot(cachedPresence), Optional.empty(), existingLookup.future());
        }
        if (pending.size() >= MAX_PENDING_REQUESTS) {
            return completedResult(ProxyPresenceSnapshot.unknown());
        }

        PendingPresenceRequest request = new PendingPresenceRequest(UUID.randomUUID(), targetPlayerId);
        CompletableFuture<ProxyPresenceSnapshot> future = new CompletableFuture<>();
        pending.put(targetPlayerId, new PendingLookup(request, future));
        return new PresenceLookupResult(
                staleSnapshot(cachedPresence), Optional.of(request), future);
    }

    void complete(ProxyPresenceResponse response, Instant now) {
        CompletableFuture<ProxyPresenceSnapshot> future;
        synchronized (this) {
            PendingLookup lookup = pending.get(response.targetPlayerId());
            if (lookup == null || !lookup.request().requestId().equals(response.requestId())) {
                return;
            }
            pending.remove(response.targetPlayerId());
            Duration responseTtl = response.snapshot().state() == ProxyPresenceState.ONLINE
                    ? ttl : shortTtl;
            cached.put(response.targetPlayerId(), new CachedPresence(
                    response.snapshot(), now.plus(responseTtl), pushSequence(response.targetPlayerId())));
            future = lookup.future();
        }
        future.complete(response.snapshot());
    }

    void fail(PendingPresenceRequest request, Instant now) {
        CompletableFuture<ProxyPresenceSnapshot> future = null;
        synchronized (this) {
            PendingLookup lookup = pending.get(request.targetPlayerId());
            if (lookup != null && lookup.request().equals(request)
                    && pending.remove(request.targetPlayerId(), lookup)) {
                ProxyPresenceSnapshot unknown = ProxyPresenceSnapshot.unknown();
                cached.put(request.targetPlayerId(), new CachedPresence(
                        unknown, now.plus(shortTtl), pushSequence(request.targetPlayerId())));
                future = lookup.future();
            }
        }
        if (future != null) {
            future.complete(ProxyPresenceSnapshot.unknown());
        }
    }

    void clear(Instant now) {
        List<CompletableFuture<ProxyPresenceSnapshot>> futures;
        synchronized (this) {
            futures = new ArrayList<>(pending.size());
            ProxyPresenceSnapshot unknown = ProxyPresenceSnapshot.unknown();
            for (PendingLookup lookup : pending.values()) {
                futures.add(lookup.future());
                cached.put(lookup.request().targetPlayerId(), new CachedPresence(
                        unknown, now.plus(shortTtl), pushSequence(lookup.request().targetPlayerId())));
            }
            pending.clear();
        }
        for (CompletableFuture<ProxyPresenceSnapshot> future : futures) {
            future.complete(ProxyPresenceSnapshot.unknown());
        }
    }

    boolean updatePush(PresencePush push, Instant now) {
        CompletableFuture<ProxyPresenceSnapshot> future = null;
        synchronized (this) {
            CachedPresence current = cached.get(push.targetPlayerId());
            if (current != null && current.pushSequence() >= push.sequence()) {
                return false;
            }
            Duration responseTtl = push.snapshot().state() == ProxyPresenceState.ONLINE
                    ? ttl : shortTtl;
            cached.put(push.targetPlayerId(), new CachedPresence(
                    push.snapshot(), now.plus(responseTtl), push.sequence()));
            PendingLookup lookup = pending.remove(push.targetPlayerId());
            if (lookup != null) {
                future = lookup.future();
            }
        }
        if (future != null) {
            future.complete(push.snapshot());
        }
        return true;
    }

    private PresenceLookupResult completedResult(ProxyPresenceSnapshot snapshot) {
        return new PresenceLookupResult(snapshot, Optional.empty(), CompletableFuture.completedFuture(snapshot));
    }

    private ProxyPresenceSnapshot staleSnapshot(CachedPresence cachedPresence) {
        return cachedPresence == null ? ProxyPresenceSnapshot.unknown() : cachedPresence.snapshot();
    }

    private long pushSequence(UUID targetPlayerId) {
        CachedPresence current = cached.get(targetPlayerId);
        return current == null ? 0L : current.pushSequence();
    }

    private record CachedPresence(ProxyPresenceSnapshot snapshot, Instant expiresAt, long pushSequence) {
    }

    private record PendingLookup(PendingPresenceRequest request,
                                 CompletableFuture<ProxyPresenceSnapshot> future) {
    }
}
