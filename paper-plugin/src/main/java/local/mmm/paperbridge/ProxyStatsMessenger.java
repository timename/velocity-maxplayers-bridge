package local.mmm.paperbridge;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

public final class ProxyStatsMessenger implements PluginMessageListener {

    public static final String CHANNEL = "mmm:bridge";

    private final PaperBridgePlugin plugin;

    public ProxyStatsMessenger(PaperBridgePlugin plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player player) {
        final ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("stats_request");
        player.sendPluginMessage(plugin, CHANNEL, output.toByteArray());
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) {
            return;
        }

        final ByteArrayDataInput input = ByteStreams.newDataInput(message);
        final String responseType = input.readUTF();
        if (!"stats_response".equals(responseType)) {
            return;
        }

        final int online = input.readInt();
        final int maxPlayers = input.readInt();
        plugin.updateStats(online, maxPlayers);
    }
}
