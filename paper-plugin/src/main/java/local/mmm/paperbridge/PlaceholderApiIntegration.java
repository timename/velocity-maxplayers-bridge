package local.mmm.paperbridge;

final class PlaceholderApiIntegration implements AutoCloseable {

    private final ProxyStatsExpansion expansion;
    private final ProxyStatsExpansion legacyExpansion;

    private PlaceholderApiIntegration(ProxyStatsExpansion expansion,
                                      ProxyStatsExpansion legacyExpansion) {
        this.expansion = expansion;
        this.legacyExpansion = legacyExpansion;
    }

    static PlaceholderApiIntegration register(PaperBridgePlugin plugin) {
        ProxyStatsExpansion expansion = new ProxyStatsExpansion(plugin, "mmmvelocitybridge");
        ProxyStatsExpansion legacyExpansion = new ProxyStatsExpansion(plugin, "mmmproxy");
        if (!expansion.register() || !legacyExpansion.register()) {
            expansion.unregister();
            legacyExpansion.unregister();
            return null;
        }
        return new PlaceholderApiIntegration(expansion, legacyExpansion);
    }

    @Override
    public void close() {
        expansion.unregister();
        legacyExpansion.unregister();
    }
}
