package dev.centraleconomy.miner.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Full Central Economy plan. The legacy class name is retained to keep the proven 0.6.x ABI small. */
public final class MinerPlan {
    private final int schema;
    private final int planningCycleDays;
    private final Map<String, MarketPlan> markets;

    public MinerPlan(int schema, int planningCycleDays, Map<String, MarketPlan> markets) {
        if (schema <= 0) throw new IllegalArgumentException("schema must be positive");
        if (planningCycleDays <= 0) throw new IllegalArgumentException("planningCycleDays must be positive");
        if (markets == null || markets.isEmpty()) throw new IllegalArgumentException("markets required");
        this.schema = schema;
        this.planningCycleDays = planningCycleDays;
        this.markets = Collections.unmodifiableMap(new LinkedHashMap<>(markets));
    }

    public int schema() { return schema; }
    public int planningCycleDays() { return planningCycleDays; }
    public Map<String, MarketPlan> markets() { return markets; }
    public MarketPlan market(String id) { return markets.get(id); }

    public long cycleTicks() { return Math.multiplyExact(24_000L, planningCycleDays); }

    public int commodityCount() {
        return markets.values().stream().mapToInt(m -> m.commodities().size()).sum();
    }
}
