package local.mmm.velocitybridge;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public final class PresenceStateResolver {

    private static final Duration RECENT_OFFLINE_DURATION = Duration.ofHours(1);

    private PresenceStateResolver() {
    }

    public static ProxyPresenceSnapshot resolve(
            Optional<OnlinePlayerPresence> onlinePlayer,
            Optional<Instant> lastDisconnectAt,
            Instant now
    ) {
        if (onlinePlayer.isPresent()) {
            OnlinePlayerPresence player = onlinePlayer.get();
            return new ProxyPresenceSnapshot(ProxyPresenceState.ONLINE, 0L, player.currentServer());
        }
        if (lastDisconnectAt.isEmpty()) {
            return ProxyPresenceSnapshot.unknown();
        }

        Instant disconnectAt = lastDisconnectAt.get();
        ProxyPresenceState state = disconnectAt.plus(RECENT_OFFLINE_DURATION).isAfter(now)
                ? ProxyPresenceState.OFFLINE_RECENT
                : ProxyPresenceState.OFFLINE_LONG;
        return new ProxyPresenceSnapshot(state, disconnectAt.toEpochMilli(), "");
    }
}
