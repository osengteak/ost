import dev.centraleconomy.miner.market.DeterministicRolls;
import dev.centraleconomy.miner.market.GateEvaluator;
import dev.centraleconomy.miner.market.MarketKeys;
import dev.centraleconomy.miner.market.MarketMutableState;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.RetailQuote;
import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
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

    private static CommodityPlan item(String id, int aLot, int bLot, int retailLot, String gate) {
        return new CommodityPlan(
                id, id, "", "item", "", 0,
                new TierPlan(aLot, 1, 1, 0.0),
                new TierPlan(bLot, 1, 1, 0.0),
                new RetailPlan(retailLot, 1, 2, 1.0, gate));
    }

    public static void main(String[] args) {
        Map<String, CommodityPlan> minerRows = new LinkedHashMap<>();
        minerRows.put("minecraft:iron_ingot", item("minecraft:iron_ingot", 2, 4, 3, "none"));
        minerRows.put("minecraft:diamond", item("minecraft:diamond", 1, 2, 1, "mineral_warehouse_and_150e_turnover"));

        Map<String, CommodityPlan> clericRows = new LinkedHashMap<>();
        // Deliberately repeat an item id in another market: state keys must remain market-local.
        clericRows.put("minecraft:redstone", item("minecraft:redstone", 3, 6, 2, "none"));

        Map<String, MarketPlan> markets = new LinkedHashMap<>();
        markets.put("miner", new MarketPlan("miner", "광부", "central_economy:miner_workstation", minerRows));
        markets.put("cleric", new MarketPlan("cleric", "성직자", "central_economy:cleric_workstation", clericRows));

        MinerMarketEngine engine = new MinerMarketEngine(new MinerPlan(3, 7, markets));
        MarketMutableState state = new MarketMutableState();
        UUID playerA = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID playerB = UUID.fromString("22222222-2222-2222-2222-222222222222");

        long cycle = 5L;
        engine.ensureCycle(state, cycle);
        check(state.retailStock().get(MarketKeys.stock("miner", "minecraft:iron_ingot")) == 6,
                "retail stock initialized with market-qualified key");
        check(state.retailStock().get(MarketKeys.stock("cleric", "minecraft:redstone")) == 4,
                "second market gets independent shared stock");

        ProcurementQuote a1 = engine.quoteProcurement(state, playerA, "miner", "minecraft:iron_ingot", cycle);
        check(a1.open() && "A".equals(a1.tier()), "player begins in livelihood A tier");
        engine.consumeProcurement(state, playerA, "miner", "minecraft:iron_ingot", cycle);
        check(state.retailStock().get(MarketKeys.stock("miner", "minecraft:iron_ingot")) == 8,
                "procurement adds physical stock only to its market commodity");

        ProcurementQuote a2 = engine.quoteProcurement(state, playerA, "miner", "minecraft:iron_ingot", cycle);
        check(a2.open() && "B".equals(a2.tier()), "A exhaustion transitions to industrial B tier");

        ProcurementQuote b1 = engine.quoteProcurement(state, playerB, "miner", "minecraft:iron_ingot", cycle);
        check(b1.open() && "A".equals(b1.tier()), "procurement quota is per player UUID");

        ProcurementQuote clericA = engine.quoteProcurement(state, playerA, "cleric", "minecraft:redstone", cycle);
        check(clericA.open() && "A".equals(clericA.tier()), "quota is also isolated by market");

        RetailQuote retail = engine.quoteRetail(state, "miner", "minecraft:iron_ingot");
        check(retail.available() && retail.stockItems() == 8, "retail sees central shared stock");
        engine.consumeRetail(state, "miner", "minecraft:iron_ingot");
        check(state.retailStock().get(MarketKeys.stock("miner", "minecraft:iron_ingot")) == 5,
                "retail decrements shared stock");

        RetailQuote gated = engine.quoteRetail(state, "miner", "minecraft:diamond");
        check(!gated.available() && gated.reason().startsWith("gate:"), "progression gate closes gated retail");
        state.infrastructureFlags().add("mineral_warehouse");
        state.cumulativeTurnoverEmeralds(150);
        check(GateEvaluator.isOpen("mineral_warehouse_and_150e_turnover", state), "infrastructure + turnover opens gate");
        check(engine.quoteRetail(state, "miner", "minecraft:diamond").available(), "gated retail opens after requirements");

        double roll1 = DeterministicRolls.playerTier(playerA, "miner|minecraft:iron_ingot", cycle, "A");
        double roll2 = DeterministicRolls.playerTier(playerA, "miner|minecraft:iron_ingot", cycle, "A");
        check(Double.compare(roll1, roll2) == 0, "quota roll is deterministic");

        engine.ensureCycle(state, cycle + 1);
        check(state.quotaUsage().isEmpty(), "new planning cycle resets player quota usage");
        check(state.retailStock().get(MarketKeys.stock("miner", "minecraft:iron_ingot")) == 6,
                "new planning cycle resets planned stock");
        check(engine.plan().cycleTicks() == 168000L, "7 Minecraft days equal 168000 ticks");
        check(engine.plan().markets().size() == 2 && engine.plan().commodityCount() == 3,
                "generic plan supports multiple profession markets");

        System.out.println("PASS: full central economy engine invariants");
    }
}
