package local.mmm.velocitybridge;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import org.slf4j.Logger;

import java.nio.file.Path;

@Plugin(
        id = "mmmvelocitybridge",
        name = "MMMVelocityBridge-Velocity",
        version = "1.0.0",
        description = "Exposes proxy online/max player counts to backend servers",
        authors = {"Xiaomenxin"}
)
public final class VelocityBridgePlugin {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.from("mmm:bridge");

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public VelocityBridgePlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        server.getChannelRegistrar().register(CHANNEL);
        logger.info("Registered plugin message channel {}", CHANNEL.getId());
        logger.info("记得早点休息，爱来自:Xiaomenxin");
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!CHANNEL.equals(event.getIdentifier())) {
            return;
        }
        final Player player;
        final ServerConnection replyTarget;
        if (event.getSource() instanceof ServerConnection serverConnection) {
            player = serverConnection.getPlayer();
            replyTarget = serverConnection;
        } else if (event.getSource() instanceof Player sourcePlayer) {
            player = sourcePlayer;
            replyTarget = sourcePlayer.getCurrentServer().orElse(null);
        } else {
            return;
        }
        if (replyTarget == null) {
            return;
        }

        event.setResult(PluginMessageEvent.ForwardResult.handled());

        final ByteArrayDataInput input = ByteStreams.newDataInput(event.getData());
        final String requestType = input.readUTF();
        if (!"stats_request".equals(requestType)) {
            return;
        }

        final ByteArrayDataOutput output = ByteStreams.newDataOutput();
        output.writeUTF("stats_response");
        output.writeInt(server.getPlayerCount());
        output.writeInt(server.getConfiguration().getShowMaxPlayers());

        replyTarget.sendPluginMessage(CHANNEL, output.toByteArray());
    }
}
