package local.mmm.velocitybridge;

public record ProxyPresenceSnapshot(
        ProxyPresenceState state,
        long lastDisconnectEpochMillis,
        String currentServer
) {

    public static ProxyPresenceSnapshot unknown() {
        return new ProxyPresenceSnapshot(ProxyPresenceState.UNKNOWN, 0L, "");
    }
}
