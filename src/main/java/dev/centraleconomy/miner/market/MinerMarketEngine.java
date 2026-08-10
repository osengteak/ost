package dev.centraleconomy.miner.market;

import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
import dev.centraleconomy.miner.plan.MinerPlan;
import dev.centraleconomy.miner.plan.RetailPlan;
import dev.centraleconomy.miner.plan.TierPlan;

import java.util.UUID;

/** Pure central-planning trade rules shared by every profession. */
public final class MinerMarketEngine {
    private final MinerPlan plan;

    public MinerMarketEngine(MinerPlan plan) { this.plan = plan; }
    public MinerPlan plan() { return plan; }
    public long cycleId(long overworldGameTime) { return Math.floorDiv(overworldGameTime, plan.cycleTicks()); }

    public void ensureCycle(MarketMutableState state, long cycleId) {
        if (state.initializedCycle() == cycleId) return;
        state.initializedCycle(cycleId);
        state.quotaUsage().clear();
        state.retailStock().clear();

        for (MarketPlan market : plan.markets().values()) {
            for (CommodityPlan cp : market.commodities().values()) {
                RetailPlan rp = cp.retail();
                if (rp == null) continue;
                boolean active = DeterministicRolls.globalActivation(
                        market.marketId() + "|" + cp.commodityId(), cycleId) < rp.activationProbability();
                state.retailStock().put(MarketKeys.stock(market.marketId(), cp.commodityId()),
                        active ? rp.initialStock() : 0);
            }
        }
    }

    public int allowedUses(UUID player, String marketId, CommodityPlan cp, long cycleId, boolean tierA) {
        if (!cp.hasProcurement()) return 0;
        TierPlan tp = tierA ? cp.procurementA() : cp.procurementB();
        String tier = tierA ? "A" : "B";
        return tp.usesForRoll(DeterministicRolls.playerTier(
                player, marketId + "|" + cp.commodityId(), cycleId, tier));
    }

    public ProcurementQuote quoteProcurement(
            MarketMutableState state, UUID player, String marketId, String commodityId, long cycleId) {
        CommodityPlan cp = requireCommodity(marketId, commodityId);
        if (!cp.hasProcurement()) return ProcurementQuote.closed();

        QuotaKey key = new QuotaKey(player, marketId, commodityId, cycleId);
        QuotaUsage usage = state.quotaUsage().getOrDefault(key, new QuotaUsage(0, 0));
        int aAllowed = allowedUses(player, marketId, cp, cycleId, true);
        if (usage.aUsed() < aAllowed) {
            TierPlan a = cp.procurementA();
            return new ProcurementQuote("A", a.lotItems(), a.emeralds(), aAllowed - usage.aUsed());
        }
        int bAllowed = allowedUses(player, marketId, cp, cycleId, false);
        if (usage.bUsed() < bAllowed) {
            TierPlan b = cp.procurementB();
            return new ProcurementQuote("B", b.lotItems(), b.emeralds(), bAllowed - usage.bUsed());
        }
        return ProcurementQuote.closed();
    }

    public ProcurementQuote consumeProcurement(
            MarketMutableState state, UUID player, String marketId, String commodityId, long cycleId) {
        ProcurementQuote quote = quoteProcurement(state, player, marketId, commodityId, cycleId);
        if (!quote.open()) throw new IllegalStateException("procurement quota exhausted");
        QuotaKey key = new QuotaKey(player, marketId, commodityId, cycleId);
        QuotaUsage usage = state.quotaUsage().getOrDefault(key, new QuotaUsage(0, 0));
        state.quotaUsage().put(key, "A".equals(quote.tier()) ? usage.useA() : usage.useB());
        state.addTurnover(quote.emeralds());

        if (requireCommodity(marketId, commodityId).hasRetail()) {
            state.retailStock().merge(MarketKeys.stock(marketId, commodityId), quote.itemCount(), Math::addExact);
        }
        return quote;
    }

    public RetailQuote quoteRetail(MarketMutableState state, String marketId, String commodityId) {
        CommodityPlan cp = requireCommodity(marketId, commodityId);
        RetailPlan rp = cp.retail();
        if (rp == null) return RetailQuote.unavailable("not_retailed");
        long cycleId = state.initializedCycle();
        if (cycleId == Long.MIN_VALUE) return RetailQuote.unavailable("cycle_uninitialized");

        boolean active = DeterministicRolls.globalActivation(
                marketId + "|" + cp.commodityId(), cycleId) < rp.activationProbability();
        if (!active) return RetailQuote.unavailable("inactive_cycle");
        if (!GateEvaluator.isOpen(rp.gate(), state)) return RetailQuote.unavailable("gate:" + rp.gate());

        int stock = state.retailStock().getOrDefault(MarketKeys.stock(marketId, commodityId), 0);
        if (stock < rp.lotItems()) return RetailQuote.unavailable("sold_out");
        return new RetailQuote(true, "ok", rp.lotItems(), rp.emeralds(), stock);
    }

    public RetailQuote consumeRetail(MarketMutableState state, String marketId, String commodityId) {
        RetailQuote quote = quoteRetail(state, marketId, commodityId);
        if (!quote.available()) throw new IllegalStateException(quote.reason());
        String key = MarketKeys.stock(marketId, commodityId);
        int next = state.retailStock().get(key) - quote.itemCount();
        state.retailStock().put(key, next);
        state.addTurnover(quote.emeralds());
        return new RetailQuote(true, "ok", quote.itemCount(), quote.emeralds(), next);
    }

    public MarketPlan requireMarket(String marketId) {
        MarketPlan market = plan.market(marketId);
        if (market == null) throw new IllegalArgumentException("unknown market: " + marketId);
        return market;
    }

    public CommodityPlan requireCommodity(String marketId, String commodityId) {
        CommodityPlan cp = requireMarket(marketId).commodity(commodityId);
        if (cp == null) throw new IllegalArgumentException("unknown commodity: " + marketId + "/" + commodityId);
        return cp;
    }
}
