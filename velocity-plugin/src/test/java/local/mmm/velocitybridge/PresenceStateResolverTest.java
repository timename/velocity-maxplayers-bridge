package local.mmm.velocitybridge;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PresenceStateResolverTest {

    private static final UUID PLAYER_ID = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Test
    void resolvesAnOnlinePlayerWithItsCurrentServer() {
        ProxyPresenceSnapshot snapshot = PresenceStateResolver.resolve(
                Optional.of(new OnlinePlayerPresence(PLAYER_ID, "survival")),
                Optional.of(NOW.minus(Duration.ofHours(2))),
                NOW
        );

        assertEquals(ProxyPresenceState.ONLINE, snapshot.state());
        assertEquals("survival", snapshot.currentServer());
    }

    @Test
    void resolvesRecentAndLongOfflineBoundaries() {
        ProxyPresenceSnapshot recent = PresenceStateResolver.resolve(
                Optional.empty(), Optional.of(NOW.minus(Duration.ofMinutes(59))), NOW);
        ProxyPresenceSnapshot longOffline = PresenceStateResolver.resolve(
                Optional.empty(), Optional.of(NOW.minus(Duration.ofHours(1))), NOW);

        assertEquals(ProxyPresenceState.OFFLINE_RECENT, recent.state());
        assertEquals(ProxyPresenceState.OFFLINE_LONG, longOffline.state());
    }

    @Test
    void resolvesNoDisconnectHistoryAsUnknown() {
        ProxyPresenceSnapshot snapshot = PresenceStateResolver.resolve(Optional.empty(), Optional.empty(), NOW);

        assertEquals(ProxyPresenceState.UNKNOWN, snapshot.state());
        assertEquals(0L, snapshot.lastDisconnectEpochMillis());
    }
}
