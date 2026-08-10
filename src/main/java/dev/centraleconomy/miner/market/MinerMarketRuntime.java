package dev.centraleconomy.miner.market;

import dev.centraleconomy.miner.plan.MinerPlanRepository;

public final class MinerMarketRuntime {
    public static final MinerPlanRepository PLANS = new MinerPlanRepository();
    private static volatile MinerMarketEngine engine;
    private MinerMarketRuntime() {}

    public static synchronized MinerMarketEngine reload() {
        engine = new MinerMarketEngine(PLANS.loadOrReload());
        return engine;
    }
    public static MinerMarketEngine engine() {
        MinerMarketEngine e = engine;
        return e == null ? reload() : e;
    }
}
