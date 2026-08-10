package dev.centraleconomy.miner.market;

public final class MarketKeys {
    private MarketKeys() {}

    public static String stock(String marketId, String commodityId) {
        return marketId + "|" + commodityId;
    }
}
