package local.mmm.paperbridge;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

public final class ProxyStatsExpansion extends PlaceholderExpansion {

    private final PaperBridgePlugin plugin;
    private final String identifier;

    public ProxyStatsExpansion(PaperBridgePlugin plugin, String identifier) {
        this.plugin = plugin;
        this.identifier = identifier;
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }

    @Override
    public String getAuthor() {
        return "Xiaomenxin";
    }

    @Override
    public String getVersion() {
        return "2.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        return switch (params.toLowerCase()) {
            case "online" -> Integer.toString(plugin.getProxyOnline());
            case "maxplayers" -> Integer.toString(plugin.getProxyMaxPlayers());
            default -> null;
        };
    }
}
