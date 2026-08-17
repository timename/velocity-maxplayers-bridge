package local.mmm.paperbridge;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class PresenceServiceProvider {

    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final Duration UNKNOWN_CACHE_TTL = Duration.ofSeconds(5);
    private static final long RESPONSE_TIMEOUT_TICKS = 100L;
    private static final int MAX_REQUESTS_PER_TICK = 64;

    private final JavaPlugin plugin;
    private final ProxyStatsMessenger messenger;
    private final PresenceLookupCache cache = new PresenceLookupCache(CACHE_TTL, UNKNOWN_CACHE_TTL);
    private final ConcurrentLinkedQueue<PendingPresenceRequest> pendingSends = new ConcurrentLinkedQueue<>();
    private final Clock clock;

    PresenceServiceProvider(JavaPlugin plugin, ProxyStatsMessenger messenger) {
        this(plugin, messenger, Clock.systemUTC());
    }

    PresenceServiceProvider(JavaPlugin plugin, ProxyStatsMessenger messenger, Clock clock) {
        this.plugin = plugin;
        this.messenger = messenger;
        this.clock = clock;
    }

    CompletableFuture<ProxyPresenceSnapshot> lookup(UUID targetPlayerId) {
        PresenceLookupResult lookup = cache.lookup(targetPlayerId, clock.instant());
        lookup.pendingRequest().ifPresent(pendingSends::offer);
        return CompletableFuture.completedFuture(lookup.snapshot());
    }

    void accept(ProxyPresenceResponse response) {
        cache.complete(response, clock.instant());
    }

    void processPendingRequests() {
        PendingPresenceRequest request = pendingSends.poll();
        if (request == null) {
            return;
        }
        Player carrier = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        int processed = 0;
        do {
            PendingPresenceRequest currentRequest = request;
            if (carrier == null || !messenger.sendPresenceRequest(carrier, currentRequest)) {
                cache.fail(currentRequest, clock.instant());
            } else {
                plugin.getServer().getScheduler().runTaskLater(
                        plugin, () -> cache.fail(currentRequest, clock.instant()), RESPONSE_TIMEOUT_TICKS);
            }
            request = pendingSends.poll();
            processed++;
        } while (request != null && processed < MAX_REQUESTS_PER_TICK);
    }

    void clearPendingRequests() {
        PendingPresenceRequest request;
        while ((request = pendingSends.poll()) != null) {
            cache.fail(request, clock.instant());
        }
    }
}
