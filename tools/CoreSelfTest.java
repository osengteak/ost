import dev.centraleconomy.miner.market.DeterministicRolls;
import dev.centraleconomy.miner.market.MarketMutableState;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.RetailQuote;
import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MinerPlan;
import dev.centraleconomy.miner.plan.RetailPlan;
import dev.centraleconomy.miner.plan.TierPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class CoreSelfTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Map<String, CommodityPlan> commodities = new LinkedHashMap<>();
        commodities.put("minecraft:iron_ingot", new CommodityPlan(
                "minecraft:iron_ingot",
                new TierPlan(2, 1, 1, 0.0),
                new TierPlan(4, 1, 1, 0.0),
                new RetailPlan(3, 2, 2, 1.0, "none")));
        commodities.put("minecraft:raw_iron", new CommodityPlan(
                "minecraft:raw_iron",
                new TierPlan(3, 1, 1, 0.0),
                new TierPlan(6, 1, 1, 0.0),
                null));

        MinerMarketEngine engine = new MinerMarketEngine(new MinerPlan(1, 7, commodities));
        MarketMutableState state = new MarketMutableState();
        UUID playerA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID playerB = UUID.fromString("22222222-2222-2222-2222-222222222222");

        long cycle = 5L;
        engine.ensureCycle(state, cycle);
        check(state.retailStock().get("minecraft:iron_ingot") == 6, "retail stock initialization");

        ProcurementQuote a1 = engine.quoteProcurement(state, playerA, "minecraft:iron_ingot", cycle);
        check(a1.open() && "A".equals(a1.tier()), "player A begins in tier A");
        engine.consumeProcurement(state, playerA, "minecraft:iron_ingot", cycle);
        check(state.retailStock().get("minecraft:iron_ingot") == 8, "procurement adds physical shared stock");

        ProcurementQuote a2 = engine.quoteProcurement(state, playerA, "minecraft:iron_ingot", cycle);
        check(a2.open() && "B".equals(a2.tier()), "A exhaustion transitions to B");

        ProcurementQuote b1 = engine.quoteProcurement(state, playerB, "minecraft:iron_ingot", cycle);
        check(b1.open() && "A".equals(b1.tier()), "quota is player-specific, not NPC-specific");

        RetailQuote retail = engine.quoteRetail(state, "minecraft:iron_ingot");
        check(retail.available() && retail.stockItems() == 8, "retail sees shared stock");
        engine.consumeRetail(state, "minecraft:iron_ingot");
        check(state.retailStock().get("minecraft:iron_ingot") == 5, "retail decrements shared stock");

        double roll1 = DeterministicRolls.playerTier(playerA, "minecraft:iron_ingot", cycle, "A");
        double roll2 = DeterministicRolls.playerTier(playerA, "minecraft:iron_ingot", cycle, "A");
        check(Double.compare(roll1, roll2) == 0, "quota roll must be deterministic");

        engine.ensureCycle(state, cycle + 1);
        check(state.quotaUsage().isEmpty(), "new cycle resets player quota usage");
        check(state.retailStock().get("minecraft:iron_ingot") == 6, "new cycle resets planned retail stock");

        check(engine.plan().cycleTicks() == 168000L, "7-day cycle is 168000 ticks");
        System.out.println("PASS: central economy core invariants");
    }
}
