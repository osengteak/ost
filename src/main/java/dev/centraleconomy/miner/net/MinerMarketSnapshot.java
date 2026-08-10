package dev.centraleconomy.miner.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.centraleconomy.miner.market.MarketKeys;
import dev.centraleconomy.miner.market.MarketMutableState;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.QuotaKey;
import dev.centraleconomy.miner.market.QuotaUsage;
import dev.centraleconomy.miner.market.RetailQuote;
import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
import net.minecraft.server.level.ServerPlayer;

public final class MinerMarketSnapshot {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private MinerMarketSnapshot() {}

    public static String create(int entityId, String marketId, ServerPlayer player, MinerMarketEngine engine,
                                MarketMutableState state, long cycleId, String message) {
        MarketPlan market = engine.requireMarket(marketId);
        JsonObject root = new JsonObject();
        root.addProperty("entity_id", entityId);
        root.addProperty("market_id", marketId);
        root.addProperty("market_name", market.displayName());
        root.addProperty("cycle", cycleId);
        root.addProperty("cycle_days", engine.plan().planningCycleDays());
        root.addProperty("turnover_e", state.cumulativeTurnoverEmeralds());
        root.addProperty("message", message == null ? "" : message);

        JsonArray rows = new JsonArray();
        for (CommodityPlan cp : market.commodities().values()) {
            ProcurementQuote pq = engine.quoteProcurement(state, player.getUUID(), marketId, cp.commodityId(), cycleId);
            int aAllowed = cp.hasProcurement() ? engine.allowedUses(player.getUUID(), marketId, cp, cycleId, true) : 0;
            int bAllowed = cp.hasProcurement() ? engine.allowedUses(player.getUUID(), marketId, cp, cycleId, false) : 0;
            QuotaUsage usage = state.quotaUsage().getOrDefault(
                    new QuotaKey(player.getUUID(), marketId, cp.commodityId(), cycleId), new QuotaUsage(0, 0));

            JsonObject row = new JsonObject();
            row.addProperty("commodity_id", cp.commodityId());
            row.addProperty("item", cp.itemId());
            row.addProperty("display_name", cp.displayName());
            row.addProperty("translation_key", MarketStackFactory.translationKey(cp));
            row.addProperty("kind", cp.kind());
            row.addProperty("sell_exists", cp.hasProcurement());
            row.addProperty("sell_open", pq.open());
            row.addProperty("tier", pq.tier());
            row.addProperty("sell_items", pq.itemCount());
            row.addProperty("sell_emeralds", pq.emeralds());
            row.addProperty("tier_remaining", pq.remainingLots());
            row.addProperty("a_remaining", Math.max(0, aAllowed - usage.aUsed()));
            row.addProperty("b_remaining", Math.max(0, bAllowed - usage.bUsed()));

            RetailQuote rq = engine.quoteRetail(state, marketId, cp.commodityId());
            row.addProperty("buy_exists", cp.hasRetail());
            row.addProperty("buy_open", rq.available());
            row.addProperty("buy_reason", rq.reason());
            row.addProperty("buy_items", cp.hasRetail() ? cp.retail().lotItems() : 0);
            row.addProperty("buy_emeralds", cp.hasRetail() ? cp.retail().emeralds() : 0);
            row.addProperty("stock", state.retailStock().getOrDefault(MarketKeys.stock(marketId, cp.commodityId()), 0));
            row.addProperty("gate", cp.hasRetail() ? cp.retail().gate() : "none");
            rows.add(row);
        }
        root.add("rows", rows);
        return GSON.toJson(root);
    }
}
