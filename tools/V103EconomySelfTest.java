import dev.centraleconomy.miner.market.MarketKeys;
import dev.centraleconomy.miner.market.MarketMutableState;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.RetailQuote;
import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
import dev.centraleconomy.miner.plan.MinerPlan;
import dev.centraleconomy.miner.plan.ProcurementJobCaps;
import dev.centraleconomy.miner.plan.RetailPlan;
import dev.centraleconomy.miner.plan.TierPlan;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public final class V103EconomySelfTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static CommodityPlan item(String id) {
        return new CommodityPlan(
                id, id, "", "item", "", 0,
                new TierPlan(2, 1, 10, 0.0),
                new TierPlan(20, 1, 10, 0.0),
                new RetailPlan(3, 1, 1, 1.0, "none"),
                new RetailPlan(3, 2, 4, 1.0, "none"),
                true, "");
    }

    public static void main(String[] args) {
        Map<String, CommodityPlan> rows = new LinkedHashMap<>();
        rows.put("minecraft:wheat", item("minecraft:wheat"));
        rows.put("minecraft:carrot", item("minecraft:carrot"));
        rows.put("disabled", new CommodityPlan(
                "disabled", "minecraft:potato", "", "item", "", 0,
                null, null, null, null, false, ""));
        MarketPlan farmer = new MarketPlan("farmer", "농부", "central_economy:farmer_workstation", rows,
                new ProcurementJobCaps(2, 3));
        MinerMarketEngine engine = new MinerMarketEngine(new MinerPlan(4, 7, Map.of("farmer", farmer)));
        MarketMutableState state = new MarketMutableState();
        UUID player = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        long cycle = 8;
        engine.ensureCycle(state, cycle);

        // A is capped by profession total even though each commodity has ten item uses.
        check("A".equals(engine.quoteProcurement(state, player, "farmer", "minecraft:wheat", cycle).tier()),
                "A begins open");
        engine.consumeProcurement(state, player, "farmer", "minecraft:wheat", cycle);
        engine.consumeProcurement(state, player, "farmer", "minecraft:wheat", cycle);
        ProcurementQuote carrotAfterAJobCap = engine.quoteProcurement(state, player, "farmer", "minecraft:carrot", cycle);
        check("B".equals(carrotAfterAJobCap.tier()), "switching commodities cannot bypass profession A cap");

        // B total cap is also shared across commodities.
        engine.consumeProcurement(state, player, "farmer", "minecraft:carrot", cycle);
        engine.consumeProcurement(state, player, "farmer", "minecraft:carrot", cycle);
        engine.consumeProcurement(state, player, "farmer", "minecraft:wheat", cycle);
        check(!engine.quoteProcurement(state, player, "farmer", "minecraft:wheat", cycle).open(),
                "profession B cap closes industrial procurement across commodities");

        String wheatKey = MarketKeys.stock("farmer", "minecraft:wheat");
        check(state.retailStock().get(wheatKey) >= 3, "C planned/player-supplied stock exists");
        check(state.retailOverflowStock().get(wheatKey) == 12, "D stock initializes separately");
        // Drain all C lots. Player procurement above also added stock, so consume until quote becomes D.
        int guard = 100;
        while (guard-- > 0 && "C".equals(engine.quoteRetail(state, "farmer", "minecraft:wheat").tier())) {
            engine.consumeRetail(state, "farmer", "minecraft:wheat");
        }
        RetailQuote d = engine.quoteRetail(state, "farmer", "minecraft:wheat");
        check(d.available() && "D".equals(d.tier()) && d.emeralds() == 2,
                "D activates only after C cannot fill another lot");
        for (int i = 0; i < 4; i++) engine.consumeRetail(state, "farmer", "minecraft:wheat");
        check(!engine.quoteRetail(state, "farmer", "minecraft:wheat").available(), "finite D stock eventually sells out");

        boolean disabledRejected = false;
        try { engine.requireCommodity("farmer", "disabled"); }
        catch (IllegalArgumentException expected) { disabledRejected = true; }
        check(disabledRejected, "disabled commodities are rejected server-side");

        engine.ensureCycle(state, cycle + 1);
        check(state.quotaUsage().isEmpty() && state.jobQuotaUsage().isEmpty(), "new cycle resets item and profession quotas");
        check(state.retailOverflowStock().get(wheatKey) == 12, "new cycle restores D planned stock");
        System.out.println("PASS: v1.0.3 ABCD job quota and overflow retail invariants");
    }
}
