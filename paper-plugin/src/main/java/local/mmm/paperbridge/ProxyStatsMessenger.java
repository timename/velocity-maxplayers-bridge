package local.mmm.paperbridge;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ProxyStatsMessenger implements PluginMessageListener {

    public static final String CHANNEL = "mmm:bridge";

    private final PaperBridgePlugin plugin;
    private final RateLimitedWarningLogger warnings;

    public ProxyStatsMessenger(PaperBridgePlugin plugin) {
        this.plugin = plugin;
        this.warnings = new RateLimitedWarningLogger(plugin.getLogger());
    }

    public void sendRequest(Player player) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeUTF("stats_request");
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException | RuntimeException exception) {
            warnings.warn("stats-send", "无法发送统计请求", exception);
        }
    }

    public boolean sendPresenceRequest(Player player, PendingPresenceRequest request) {
        try {
            player.sendPluginMessage(plugin, CHANNEL, PresenceMessageCodec.encodeRequest(request));
            return true;
        } catch (RuntimeException exception) {
            warnings.warn("presence-send", "无法发送 Presence 请求", exception);
            return false;
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        final String responseType;
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            responseType = input.readUTF();
        } catch (IOException exception) {
            warnings.warn("message-malformed", "收到畸形代理消息", exception);
            return;
        }

        switch (responseType) {
            case "stats_response" -> readStatsResponse(message);
            case "presence_response" -> PresenceMessageCodec.decodeResponse(message)
                    .ifPresentOrElse(plugin::updatePresence,
                            () -> warnings.warn("presence-malformed", "收到畸形 Presence 响应"));
            case "presence_push" -> PresenceMessageCodec.decodePush(message)
                    .ifPresentOrElse(plugin::updatePresencePush,
                            () -> warnings.warn("presence-push-malformed", "收到畸形 Presence 推送"));
            default -> warnings.warn("message-unknown", "收到未知代理消息类型: " + responseType);
        }
    }

    private void readStatsResponse(byte[] message) {
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(message))) {
            input.readUTF();
            plugin.updateStats(input.readInt(), input.readInt());
        } catch (IOException | RuntimeException exception) {
            warnings.warn("stats-malformed", "收到畸形统计响应", exception);
        }
    }
}
