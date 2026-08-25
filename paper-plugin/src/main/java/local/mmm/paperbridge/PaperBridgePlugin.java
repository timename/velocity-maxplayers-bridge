package local.mmm.paperbridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class PaperBridgePlugin extends JavaPlugin {

    private AutoCloseable placeholderApiIntegration;
    private ProxyStatsMessenger messenger;
    private PresenceServiceProvider presenceService;
    private MmmFlightPresenceIntegration.PresenceChangeNotifier presenceChangeNotifier;
    private int requestTaskId = -1;
    private int presenceTaskId = -1;
    private volatile int proxyOnline = 0;
    private volatile int proxyMaxPlayers = 0;

    @Override
    public void onEnable() {
        messenger = new ProxyStatsMessenger(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, ProxyStatsMessenger.CHANNEL, messenger);
        getServer().getMessenger().registerOutgoingPluginChannel(this, ProxyStatsMessenger.CHANNEL);

        presenceService = new PresenceServiceProvider(this, messenger);
        presenceChangeNotifier = MmmFlightPresenceIntegration.register(this, presenceService);
        if (presenceChangeNotifier != null) {
            getLogger().info("已注册 MMMFlight Presence 服务");
        }

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                placeholderApiIntegration = PlaceholderApiIntegration.register(this);
            } catch (LinkageError | RuntimeException exception) {
                getLogger().log(java.util.logging.Level.WARNING,
                        "PlaceholderAPI 变量注册失败，统计服务仍保持可用", exception);
            }
            if (placeholderApiIntegration == null) {
                getLogger().warning("PlaceholderAPI 变量注册失败，统计服务仍保持可用");
            }
        } else {
            getLogger().warning("PlaceholderAPI 未安装，跳过统计变量注册");
        }

        requestTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(this, this::requestStats, 20L, 40L);
        presenceTaskId = getServer().getScheduler().scheduleSyncRepeatingTask(
                this, presenceService::processPendingRequests, 1L, 1L);
        getLogger().info("记得早点休息，爱来自:Xiaomenxin");
    }

    @Override
    public void onDisable() {
        if (requestTaskId != -1) {
            getServer().getScheduler().cancelTask(requestTaskId);
        }
        if (presenceTaskId != -1) {
            getServer().getScheduler().cancelTask(presenceTaskId);
        }
        if (presenceService != null) {
            presenceService.clearPendingRequests();
        }
        if (placeholderApiIntegration != null) {
            try {
                placeholderApiIntegration.close();
            } catch (Exception exception) {
                getLogger().log(java.util.logging.Level.WARNING,
                        "PlaceholderAPI 变量注销失败", exception);
            }
        }
        getServer().getServicesManager().unregisterAll(this);
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

    void updatePresence(ProxyPresenceResponse response) {
        if (presenceService != null) {
            presenceService.accept(response);
        }
    }

    void updatePresencePush(PresencePush push) {
        if (presenceService != null && presenceService.acceptPush(push)
                && presenceChangeNotifier != null) {
            presenceChangeNotifier.notifyChanged(push.targetPlayerId(), push.snapshot());
        }
    }

    private void requestStats() {
        final Player player = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (player == null) {
            return;
        }
        messenger.sendRequest(player);
    }
}
