package dev.centraleconomy.miner.market;

import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
import dev.centraleconomy.miner.plan.MinerPlan;
import dev.centraleconomy.miner.plan.ProcurementJobCaps;
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
        if (state.initializedCycle() == cycleId) {
            // Schema-2 worlds have no D ledger. Initialize only missing keys without refreshing exhausted D stock.
            ensureMissingOverflowStocks(state, cycleId);
            return;
        }
        state.initializedCycle(cycleId);
        state.quotaUsage().clear();
        state.jobQuotaUsage().clear();
        state.retailStock().clear();
        state.retailOverflowStock().clear();
        for (MarketPlan market : plan.markets().values()) {
            for (CommodityPlan cp : market.commodities().values()) {
                if (!cp.enabled()) continue;
                RetailPlan rp = cp.retail();
                if (rp == null) continue;
                boolean active = isRetailActive(market.marketId(), cp.commodityId(), cycleId, rp);
                String key = MarketKeys.stock(market.marketId(), cp.commodityId());
                state.retailStock().put(key, active ? rp.initialStock() : 0);
                if (cp.hasRetailOverflow()) {
                    RetailPlan dp = cp.retailOverflow();
                    boolean dActive = active && isOverflowActive(market.marketId(), cp.commodityId(), cycleId, dp);
                    state.retailOverflowStock().put(key, dActive ? dp.initialStock() : 0);
                }
            }
        }
    }

    private void ensureMissingOverflowStocks(MarketMutableState state, long cycleId) {
        for (MarketPlan market : plan.markets().values()) {
            for (CommodityPlan cp : market.commodities().values()) {
                if (!cp.hasRetailOverflow()) continue;
                String key = MarketKeys.stock(market.marketId(), cp.commodityId());
                if (state.retailOverflowStock().containsKey(key)) continue;
                RetailPlan c = cp.retail();
                RetailPlan d = cp.retailOverflow();
                boolean cActive = c != null && isRetailActive(market.marketId(), cp.commodityId(), cycleId, c);
                boolean dActive = cActive && isOverflowActive(market.marketId(), cp.commodityId(), cycleId, d);
                state.retailOverflowStock().put(key, dActive ? d.initialStock() : 0);
            }
        }
    }

    private static boolean isRetailActive(String marketId, String commodityId, long cycleId, RetailPlan rp) {
        return DeterministicRolls.globalActivation(marketId + "|" + commodityId, cycleId) < rp.activationProbability();
    }

    private static boolean isOverflowActive(String marketId, String commodityId, long cycleId, RetailPlan rp) {
        return DeterministicRolls.globalActivation(marketId + "|" + commodityId + "|D", cycleId) < rp.activationProbability();
    }

    public int allowedUses(UUID player, String marketId, CommodityPlan cp, long cycleId, boolean tierA) {
        if (!cp.hasProcurement()) return 0;
        TierPlan tp = tierA ? cp.procurementA() : cp.procurementB();
        String tier = tierA ? "A" : "B";
        return tp.usesForRoll(DeterministicRolls.playerTier(
                player, marketId + "|" + cp.commodityId(), cycleId, tier));
    }

    public JobQuotaUsage jobUsage(MarketMutableState state, UUID player, String marketId, long cycleId) {
        return state.jobQuotaUsage().getOrDefault(new JobQuotaKey(player, marketId, cycleId), new JobQuotaUsage(0, 0));
    }

    public int jobCapEmeralds(String marketId, boolean tierA) {
        ProcurementJobCaps caps = requireMarket(marketId).procurementJobCaps();
        return tierA ? caps.aEmeraldsPerPlayerPerCycle() : caps.bEmeraldsPerPlayerPerCycle();
    }

    public int jobRemainingEmeralds(MarketMutableState state, UUID player, String marketId, long cycleId, boolean tierA) {
        int cap = jobCapEmeralds(marketId, tierA);
        if (cap <= 0) return Integer.MAX_VALUE;
        JobQuotaUsage usage = jobUsage(state, player, marketId, cycleId);
        int used = tierA ? usage.aEmeraldsUsed() : usage.bEmeraldsUsed();
        return Math.max(0, cap - used);
    }

    public int remainingUses(MarketMutableState state, UUID player, String marketId,
                             CommodityPlan cp, long cycleId, boolean tierA) {
        if (!cp.hasProcurement()) return 0;
        QuotaUsage usage = state.quotaUsage().getOrDefault(
                new QuotaKey(player, marketId, cp.commodityId(), cycleId), new QuotaUsage(0, 0));
        int itemAllowed = allowedUses(player, marketId, cp, cycleId, tierA);
        int itemUsed = tierA ? usage.aUsed() : usage.bUsed();
        int itemRemaining = Math.max(0, itemAllowed - itemUsed);
        TierPlan tp = tierA ? cp.procurementA() : cp.procurementB();
        int jobCap = jobCapEmeralds(marketId, tierA);
        if (jobCap <= 0) return itemRemaining;
        int jobRemaining = jobRemainingEmeralds(state, player, marketId, cycleId, tierA);
        return Math.min(itemRemaining, jobRemaining / tp.emeralds());
    }

    public ProcurementQuote quoteProcurement(
            MarketMutableState state, UUID player, String marketId, String commodityId, long cycleId) {
        CommodityPlan cp = requireCommodity(marketId, commodityId);
        if (!cp.hasProcurement()) return ProcurementQuote.closed();

        int aRemaining = remainingUses(state, player, marketId, cp, cycleId, true);
        if (aRemaining > 0) {
            TierPlan a = cp.procurementA();
            return new ProcurementQuote("A", a.lotItems(), a.emeralds(), aRemaining);
        }

        int bRemaining = remainingUses(state, player, marketId, cp, cycleId, false);
        if (bRemaining > 0) {
            TierPlan b = cp.procurementB();
            return new ProcurementQuote("B", b.lotItems(), b.emeralds(), bRemaining);
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

        JobQuotaKey jobKey = new JobQuotaKey(player, marketId, cycleId);
        JobQuotaUsage jobUsage = state.jobQuotaUsage().getOrDefault(jobKey, new JobQuotaUsage(0, 0));
        state.jobQuotaUsage().put(jobKey, "A".equals(quote.tier())
                ? jobUsage.useA(quote.emeralds()) : jobUsage.useB(quote.emeralds()));

        state.addTurnover(quote.emeralds());
        if (requireCommodity(marketId, commodityId).hasRetail()) {
            state.retailStock().merge(MarketKeys.stock(marketId, commodityId), quote.itemCount(), Math::addExact);
        }
        return quote;
    }

    public RetailQuote quoteRetail(MarketMutableState state, String marketId, String commodityId) {
        CommodityPlan cp = requireCommodity(marketId, commodityId);
        RetailPlan c = cp.retail();
        if (c == null) return RetailQuote.unavailable("not_retailed");
        long cycleId = state.initializedCycle();
        if (cycleId == Long.MIN_VALUE) return RetailQuote.unavailable("cycle_uninitialized");
        if (!isRetailActive(marketId, cp.commodityId(), cycleId, c)) {
            return RetailQuote.unavailable("inactive_cycle");
        }
        if (!GateEvaluator.isOpen(c.gate(), state)) return RetailQuote.unavailable("gate:" + c.gate());

        String key = MarketKeys.stock(marketId, commodityId);
        int cStock = state.retailStock().getOrDefault(key, 0);
        if (cStock >= c.lotItems()) {
            return new RetailQuote(true, "ok", "C", c.lotItems(), c.emeralds(), cStock);
        }

        RetailPlan d = cp.retailOverflow();
        if (d == null) return RetailQuote.unavailable("sold_out");
        if (!isOverflowActive(marketId, cp.commodityId(), cycleId, d)) {
            return RetailQuote.unavailable("sold_out");
        }
        if (!GateEvaluator.isOpen(d.gate(), state)) return RetailQuote.unavailable("gate:" + d.gate());
        int dStock = state.retailOverflowStock().getOrDefault(key, 0);
        if (dStock < d.lotItems()) return RetailQuote.unavailable("sold_out");
        return new RetailQuote(true, "ok", "D", d.lotItems(), d.emeralds(), dStock);
    }

    public RetailQuote consumeRetail(MarketMutableState state, String marketId, String commodityId) {
        RetailQuote quote = quoteRetail(state, marketId, commodityId);
        if (!quote.available()) throw new IllegalStateException(quote.reason());
        String key = MarketKeys.stock(marketId, commodityId);
        int next;
        if ("D".equals(quote.tier())) {
            next = state.retailOverflowStock().get(key) - quote.itemCount();
            state.retailOverflowStock().put(key, next);
        } else {
            next = state.retailStock().get(key) - quote.itemCount();
            state.retailStock().put(key, next);
        }
        state.addTurnover(quote.emeralds());
        return new RetailQuote(true, "ok", quote.tier(), quote.itemCount(), quote.emeralds(), next);
    }

    public MarketPlan requireMarket(String marketId) {
        MarketPlan market = plan.market(marketId);
        if (market == null) throw new IllegalArgumentException("unknown market: " + marketId);
        return market;
    }

    public CommodityPlan requireCommodity(String marketId, String commodityId) {
        CommodityPlan cp = requireMarket(marketId).commodity(commodityId);
        if (cp == null || !cp.enabled()) {
            throw new IllegalArgumentException("unknown/disabled commodity: " + marketId + "/" + commodityId);
        }
        return cp;
    }
}
