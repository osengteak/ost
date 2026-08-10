package dev.centraleconomy.miner.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MinerPlan {
    private final int schema;
    private final int planningCycleDays;
    private final Map<String, CommodityPlan> commodities;

    public MinerPlan(int schema, int planningCycleDays, Map<String, CommodityPlan> commodities) {
        if (schema <= 0) throw new IllegalArgumentException("schema must be positive");
        if (planningCycleDays <= 0) throw new IllegalArgumentException("planningCycleDays must be positive");
        if (commodities == null || commodities.isEmpty()) throw new IllegalArgumentException("commodities required");
        this.schema = schema;
        this.planningCycleDays = planningCycleDays;
        this.commodities = Collections.unmodifiableMap(new LinkedHashMap<>(commodities));
    }

    public int schema() { return schema; }
    public int planningCycleDays() { return planningCycleDays; }
    public Map<String, CommodityPlan> commodities() { return commodities; }
    public CommodityPlan commodity(String id) { return commodities.get(id); }

    public long cycleTicks() {
        return Math.multiplyExact(24_000L, planningCycleDays);
    }
}
