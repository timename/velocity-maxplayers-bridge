package local.mmm.paperbridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperBridgePlugin extends JavaPlugin {

    private ProxyStatsExpansion expansion;
    private ProxyStatsMessenger messenger;
    private int requestTaskId = -1;
    private volatile int proxyOnline = 0;
    private volatile int proxyMaxPlayers = 0;

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().severe("PlaceholderAPI is required.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        messenger = new ProxyStatsMessenger(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyStatsMessenger.CHANNEL, messenger);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ProxyStatsMessenger.CHANNEL);

        expansion = new ProxyStatsExpansion(this);
        if (!expansion.register()) {
            getLogger().severe("Failed to register PlaceholderAPI expansion.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        requestTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this::requestStats, 20L, 40L);
        getLogger().info("记得早点休息，爱来自:Xiaomenxin");
    }

    @Override
    public void onDisable() {
        if (requestTaskId != -1) {
            getServer().getScheduler().cancelTask(requestTaskId);
        }
        if (expansion != null) {
            expansion.unregister();
        }
        getServer().getMessenger().unregisterIncomingPluginChannel(this, ProxyStatsMessenger.CHANNEL);
        getServer().getMessenger().unregisterOutgoingPluginChannel(this, ProxyStatsMessenger.CHANNEL);
    }

    public int getProxyOnline() {
        return proxyOnline;
    }

    public int getProxyMaxPlayers() {
        return proxyMaxPlayers;
    }

    void updateStats(int online, int maxPlayers) {
        this.proxyOnline = online;
        this.proxyMaxPlayers = maxPlayers;
    }

    private void requestStats() {
        final Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }
        messenger.sendRequest(player);
    }
}
