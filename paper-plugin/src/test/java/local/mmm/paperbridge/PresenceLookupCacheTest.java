package local.mmm.paperbridge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PresenceLookupCacheTest {

    private static final UUID TARGET_ID = UUID.fromString("55555555-5555-5555-5555-555555555555");
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Test
    void deduplicatesPendingRequestsAndCachesResponsesForThirtySeconds() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));

        PresenceLookupResult first = cache.lookup(TARGET_ID, NOW);
        PresenceLookupResult duplicate = cache.lookup(TARGET_ID, NOW.plusSeconds(1));
        ProxyPresenceSnapshot expected = new ProxyPresenceSnapshot(
                ProxyPresenceState.OFFLINE_RECENT, 1_723_686_400_000L, "");
        cache.complete(new ProxyPresenceResponse(
                first.pendingRequest().orElseThrow().requestId(), TARGET_ID, expected), NOW.plusSeconds(2));

        assertTrue(first.pendingRequest().isPresent());
        assertTrue(duplicate.pendingRequest().isEmpty());
        assertEquals(first.future(), duplicate.future());
        assertTrue(first.future().isDone());
        assertEquals(expected, first.future().join());
        assertEquals(expected, cache.lookup(TARGET_ID, NOW.plusSeconds(3)).snapshot());
        assertTrue(cache.lookup(TARGET_ID, NOW.plusSeconds(33)).pendingRequest().isPresent());
    }

    @Test
    void keepsExpiredOnlineSnapshotWhileRefreshIsPending() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30), Duration.ofSeconds(5));
        PendingPresenceRequest initialRequest = cache.lookup(TARGET_ID, NOW).pendingRequest().orElseThrow();
        ProxyPresenceSnapshot online = new ProxyPresenceSnapshot(ProxyPresenceState.ONLINE, 0L, "login");
        cache.complete(new ProxyPresenceResponse(initialRequest.requestId(), TARGET_ID, online), NOW);

        PresenceLookupResult refresh = cache.lookup(TARGET_ID, NOW.plusSeconds(31));
        PresenceLookupResult duplicate = cache.lookup(TARGET_ID, NOW.plusSeconds(32));

        assertEquals(ProxyPresenceState.ONLINE, refresh.snapshot().state());
        assertTrue(refresh.pendingRequest().isPresent());
        assertEquals(online, refresh.snapshot());
        assertEquals(ProxyPresenceState.ONLINE, duplicate.snapshot().state());
        assertEquals(refresh.future(), duplicate.future());
        assertFalse(refresh.future().isDone());
    }

    @Test
    void acceptsTheNewestPushAndCompletesARefresh() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30), Duration.ofSeconds(5));
        PendingPresenceRequest initialRequest = cache.lookup(TARGET_ID, NOW).pendingRequest().orElseThrow();
        ProxyPresenceSnapshot online = new ProxyPresenceSnapshot(ProxyPresenceState.ONLINE, 0L, "login");
        cache.complete(new ProxyPresenceResponse(initialRequest.requestId(), TARGET_ID, online), NOW);

        PresenceLookupResult refresh = cache.lookup(TARGET_ID, NOW.plusSeconds(31));
        ProxyPresenceSnapshot offline = new ProxyPresenceSnapshot(
                ProxyPresenceState.OFFLINE_RECENT, NOW.toEpochMilli(), "");
        cache.updatePush(new PresencePush(2L, TARGET_ID, offline), NOW.plusSeconds(32));

        assertEquals(offline, refresh.future().join());
        assertEquals(ProxyPresenceState.OFFLINE_RECENT,
                cache.lookup(TARGET_ID, NOW.plusSeconds(33)).snapshot().state());
    }

    @Test
    void ignoresOlderPresencePushes() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));
        cache.updatePush(new PresencePush(2L, TARGET_ID,
                new ProxyPresenceSnapshot(ProxyPresenceState.ONLINE, 0L, "survival")), NOW);
        cache.updatePush(new PresencePush(1L, TARGET_ID,
                new ProxyPresenceSnapshot(ProxyPresenceState.OFFLINE_LONG, NOW.toEpochMilli(), "")), NOW.plusSeconds(1));

        assertEquals(ProxyPresenceState.ONLINE, cache.lookup(TARGET_ID, NOW.plusSeconds(2)).snapshot().state());
    }

    @Test
    void completesPendingFutureWhenCacheIsCleared() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));
        PresenceLookupResult result = cache.lookup(TARGET_ID, NOW);

        cache.clear(NOW.plusSeconds(1));

        assertEquals(ProxyPresenceState.UNKNOWN, result.future().join().state());
    }

    @Test
    void rejectsResponsesForUnknownRequests() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));
        ProxyPresenceSnapshot expected = ProxyPresenceSnapshot.unknown();

        cache.complete(new ProxyPresenceResponse(UUID.randomUUID(), TARGET_ID, expected), NOW);

        assertEquals(ProxyPresenceState.UNKNOWN, cache.lookup(TARGET_ID, NOW).snapshot().state());
    }

    @Test
    void cachesUnknownWhenNoCarrierOrResponseIsAvailable() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));
        PendingPresenceRequest request = cache.lookup(TARGET_ID, NOW).pendingRequest().orElseThrow();

        cache.fail(request, NOW.plusSeconds(1));

        assertEquals(ProxyPresenceState.UNKNOWN, cache.lookup(TARGET_ID, NOW.plusSeconds(2)).snapshot().state());
        assertTrue(cache.lookup(TARGET_ID, NOW.plusSeconds(32)).pendingRequest().isPresent());
    }

    @Test
    void expiresUnknownStateBeforeKnownStateTtl() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30), Duration.ofSeconds(5));
        PendingPresenceRequest request = cache.lookup(TARGET_ID, NOW).pendingRequest().orElseThrow();

        cache.fail(request, NOW.plusSeconds(1));

        assertEquals(ProxyPresenceState.UNKNOWN, cache.lookup(TARGET_ID, NOW.plusSeconds(4)).snapshot().state());
        assertTrue(cache.lookup(TARGET_ID, NOW.plusSeconds(7)).pendingRequest().isPresent());
    }

    @Test
    void refreshesOfflineStateBeforeOnlineStateTtl() {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30), Duration.ofSeconds(5));
        PendingPresenceRequest request = cache.lookup(TARGET_ID, NOW).pendingRequest().orElseThrow();
        ProxyPresenceSnapshot offline = new ProxyPresenceSnapshot(
                ProxyPresenceState.OFFLINE_RECENT, NOW.toEpochMilli(), "");

        cache.complete(new ProxyPresenceResponse(request.requestId(), TARGET_ID, offline), NOW.plusSeconds(1));

        assertEquals(ProxyPresenceState.OFFLINE_RECENT, cache.lookup(TARGET_ID, NOW.plusSeconds(4)).snapshot().state());
        assertTrue(cache.lookup(TARGET_ID, NOW.plusSeconds(7)).pendingRequest().isPresent());
    }

    @Test
    void sendsOnlyOneRequestWhenConcurrentLookupsTargetTheSamePlayer() throws Exception {
        PresenceLookupCache cache = new PresenceLookupCache(Duration.ofSeconds(30));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            Callable<PresenceLookupResult> lookup = () -> cache.lookup(TARGET_ID, NOW);
            List<Future<PresenceLookupResult>> results = IntStream.range(0, 8)
                    .mapToObj(ignored -> executor.submit(lookup))
                    .toList();

            long requests = 0L;
            for (Future<PresenceLookupResult> result : results) {
                if (result.get().pendingRequest().isPresent()) {
                    requests++;
                }
            }
            assertEquals(1L, requests);
        } finally {
            executor.shutdownNow();
        }
    }
}
