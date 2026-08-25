package local.mmm.velocitybridge;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Plugin(
        id = "mmmvelocitybridge",
        name = "MMMVelocityBridge-Velocity",
        version = "2.0.3",
        description = "Bridges proxy statistics and player presence to backend servers",
        authors = {"Xiaomenxin"}
)
public final class VelocityBridgePlugin {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("mmm:bridge");

    private final ProxyServer server;
    private final Logger logger;
    private final RateLimitedLogger warnings;
    private final Clock clock;
    private final PresenceHistory presenceHistory;
    private final AtomicLong presenceSequence = new AtomicLong(System.currentTimeMillis() << 20);

    @Inject
    public VelocityBridgePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.warnings = new RateLimitedLogger(logger);
        this.clock = Clock.systemUTC();
        this.presenceHistory = new PresenceHistory(
                dataDirectory.resolve("last-disconnects.json"), clock, warnings);
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        presenceHistory.load();
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("Registered plugin message channel {}", CHANNEL.getId());
        logger.info("记得早点休息，爱来自:Xiaomenxin");
    }

    @Subscribe
    public void onPostLogin(PostLoginEvent event) {
        presenceHistory.markOnline(event.getPlayer().getUniqueId());
    }

    @Subscribe
    public void onServerPostConnect(ServerPostConnectEvent event) {
        Player player = event.getPlayer();
        String currentServer = player.getCurrentServer()
                .map(connection -> connection.getServerInfo().getName())
                .orElse("");
        publishPresence(new ProxyPresenceSnapshot(
                ProxyPresenceState.ONLINE, 0L, currentServer), player.getUniqueId());
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        presenceHistory.markDisconnected(playerId);
        publishPresence(PresenceStateResolver.resolve(
                Optional.empty(), presenceHistory.lastDisconnectAt(playerId), clock.instant()), playerId);
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        event.setResult(PluginMessageEvent.ForwardResult.handled());
        byte[] message = event.getData();

        Optional<String> messageType = readMessageType(message);
        if (messageType.isEmpty()) {
            warnings.warn("message-malformed", "收到畸形后端消息");
            return;
        }

        switch (messageType.orElseThrow()) {
            case PresenceMessageCodec.REQUEST_TYPE -> {
                Optional<PresenceRequest> presenceRequest = PresenceMessageCodec.decodeRequest(message);
                if (presenceRequest.isEmpty()) {
                    warnings.warn("presence-malformed", "收到畸形 Presence 请求");
                    return;
                }
                if (!(event.getSource() instanceof ServerConnection serverConnection)) {
                    warnings.warn("presence-client-source", "拒绝来自客户端的 Presence 查询");
                    return;
                }
                sendPresenceResponse(serverConnection, presenceRequest.orElseThrow());
            }
            case "stats_request" -> {
                ServerConnection replyTarget = statsReplyTarget(event.getSource());
                if (replyTarget != null) {
                    sendStatsResponse(replyTarget);
                }
            }
            default -> warnings.warn("message-unknown", "收到未知后端消息类型: " + messageType.orElseThrow());
        }
    }

    private void sendPresenceResponse(ServerConnection replyTarget, PresenceRequest request) {
        UUID playerId = request.targetPlayerId();
        Optional<OnlinePlayerPresence> onlinePlayer = server.getPlayer(playerId)
                .map(player -> new OnlinePlayerPresence(
                        player.getUniqueId(),
                        player.getCurrentServer()
                                .map(connection -> connection.getServerInfo().getName())
                                .orElse("")));
        ProxyPresenceSnapshot snapshot = PresenceStateResolver.resolve(
                onlinePlayer,
                presenceHistory.lastDisconnectAt(playerId),
                clock.instant());
        byte[] message = PresenceMessageCodec.encodeResponse(
                new PresenceResponse(request.requestId(), playerId, snapshot));
        if (!replyTarget.sendPluginMessage(CHANNEL, message)) {
            warnings.warn("presence-send", "无法发送 Presence 响应");
        }
    }

    private void sendStatsResponse(ServerConnection replyTarget) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("stats_response");
            output.writeInt(server.getPlayerCount());
            output.writeInt(server.getConfiguration().getShowMaxPlayers());
            if (!replyTarget.sendPluginMessage(CHANNEL, bytes.toByteArray())) {
                warnings.warn("stats-send", "无法发送统计响应");
            }
        } catch (IOException exception) {
            warnings.warn("stats-encode", "无法编码统计响应");
        }
    }

    private void publishPresence(ProxyPresenceSnapshot snapshot, UUID targetPlayerId) {
        PresencePush push = new PresencePush(
                presenceSequence.incrementAndGet(), targetPlayerId, snapshot);
        byte[] message = PresenceMessageCodec.encodePush(push);
        Set<String> sentServers = new HashSet<>();
        for (Player onlinePlayer : server.getAllPlayers()) {
            Optional<ServerConnection> currentServer = onlinePlayer.getCurrentServer();
            if (currentServer.isEmpty()) {
                continue;
            }
            ServerConnection connection = currentServer.orElseThrow();
            String serverName = connection.getServerInfo().getName();
            if (!sentServers.add(serverName)) {
                continue;
            }
            if (!connection.sendPluginMessage(CHANNEL, message)) {
                warnings.warn("presence-push-send", "无法发送 Presence 状态推送");
            }
        }
    }

    private static Optional<String> readMessageType(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            return Optional.of(input.readUTF());
        } catch (IOException exception) {
            return Optional.empty();
        }
    }

    private static ServerConnection statsReplyTarget(Object source) {
        if (source instanceof ServerConnection serverConnection) {
            return serverConnection;
        }
        if (source instanceof Player player) {
            return player.getCurrentServer().orElse(null);
        }
        return null;
    }

    @Subscribe
    public void onProxyShutdown(com.velocitypowered.api.event.proxy.ProxyShutdownEvent event) {
        presenceHistory.close();
    }
}
