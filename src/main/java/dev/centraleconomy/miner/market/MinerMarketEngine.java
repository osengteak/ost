package dev.centraleconomy.miner.market;

import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MinerPlan;
import dev.centraleconomy.miner.plan.RetailPlan;
import dev.centraleconomy.miner.plan.TierPlan;

import java.util.UUID;

/** Pure economic rules. Minecraft inventory/network code must call this rather than duplicating policy. */
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
        for (CommodityPlan cp : plan.commodities().values()) {
            RetailPlan rp = cp.retail();
            if (rp == null) continue;
            boolean active = DeterministicRolls.globalActivation(cp.commodityId(), cycleId) < rp.activationProbability();
            state.retailStock().put(cp.commodityId(), active ? rp.initialStock() : 0);
        }
    }

    public int allowedUses(UUID player, CommodityPlan cp, long cycleId, boolean tierA) {
        TierPlan tp = tierA ? cp.procurementA() : cp.procurementB();
        String tier = tierA ? "A" : "B";
        return tp.usesForRoll(DeterministicRolls.playerTier(player, cp.commodityId(), cycleId, tier));
    }

    public ProcurementQuote quoteProcurement(MarketMutableState state, UUID player, String commodityId, long cycleId) {
        CommodityPlan cp = requireCommodity(commodityId);
        QuotaKey key = new QuotaKey(player, commodityId, cycleId);
        QuotaUsage usage = state.quotaUsage().getOrDefault(key, new QuotaUsage(0, 0));
        int aAllowed = allowedUses(player, cp, cycleId, true);
        if (usage.aUsed() < aAllowed) {
            TierPlan a = cp.procurementA();
            return new ProcurementQuote("A", a.lotItems(), a.emeralds(), aAllowed - usage.aUsed());
        }
        int bAllowed = allowedUses(player, cp, cycleId, false);
        if (usage.bUsed() < bAllowed) {
            TierPlan b = cp.procurementB();
            return new ProcurementQuote("B", b.lotItems(), b.emeralds(), bAllowed - usage.bUsed());
        }
        return ProcurementQuote.closed();
    }

    public ProcurementQuote consumeProcurement(MarketMutableState state, UUID player, String commodityId, long cycleId) {
        ProcurementQuote quote = quoteProcurement(state, player, commodityId, cycleId);
        if (!quote.open()) throw new IllegalStateException("procurement quota exhausted");
        QuotaKey key = new QuotaKey(player, commodityId, cycleId);
        QuotaUsage usage = state.quotaUsage().getOrDefault(key, new QuotaUsage(0, 0));
        state.quotaUsage().put(key, "A".equals(quote.tier()) ? usage.useA() : usage.useB());
        state.addTurnover(quote.emeralds());
        // State procurement adds physical stock if the item is also retailed.
        if (requireCommodity(commodityId).hasRetail()) {
            state.retailStock().merge(commodityId, quote.itemCount(), Math::addExact);
        }
        return quote;
    }

    public RetailQuote quoteRetail(MarketMutableState state, String commodityId) {
        CommodityPlan cp = requireCommodity(commodityId);
        RetailPlan rp = cp.retail();
        if (rp == null) return RetailQuote.unavailable("not_retailed");
        long cycleId = state.initializedCycle();
        if (cycleId == Long.MIN_VALUE) return RetailQuote.unavailable("cycle_uninitialized");
        boolean active = DeterministicRolls.globalActivation(cp.commodityId(), cycleId) < rp.activationProbability();
        if (!active) return RetailQuote.unavailable("inactive_cycle");
        if (!GateEvaluator.isOpen(rp.gate(), state)) return RetailQuote.unavailable("gate:" + rp.gate());
        int stock = state.retailStock().getOrDefault(commodityId, 0);
        if (stock < rp.lotItems()) return RetailQuote.unavailable("sold_out");
        return new RetailQuote(true, "ok", rp.lotItems(), rp.emeralds(), stock);
    }

    public RetailQuote consumeRetail(MarketMutableState state, String commodityId) {
        RetailQuote quote = quoteRetail(state, commodityId);
        if (!quote.available()) throw new IllegalStateException(quote.reason());
        int next = state.retailStock().get(commodityId) - quote.itemCount();
        state.retailStock().put(commodityId, next);
        state.addTurnover(quote.emeralds());
        return new RetailQuote(true, "ok", quote.itemCount(), quote.emeralds(), next);
    }

    private CommodityPlan requireCommodity(String id) {
        CommodityPlan cp = plan.commodity(id);
        if (cp == null) throw new IllegalArgumentException("unknown commodity: " + id);
        return cp;
    }
}
