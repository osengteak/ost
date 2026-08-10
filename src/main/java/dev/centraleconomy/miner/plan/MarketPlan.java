package dev.centraleconomy.miner.plan;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MarketPlan {
    private final String marketId;
    private final String displayName;
    private final String workstationId;
    private final Map<String, CommodityPlan> commodities;

    public MarketPlan(String marketId, String displayName, String workstationId, Map<String, CommodityPlan> commodities) {
        if (marketId == null || marketId.isBlank()) throw new IllegalArgumentException("marketId required");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName required");
        if (commodities == null || commodities.isEmpty()) throw new IllegalArgumentException("commodities required");
        this.marketId = marketId;
        this.displayName = displayName;
        this.workstationId = workstationId == null ? "" : workstationId;
        this.commodities = Collections.unmodifiableMap(new LinkedHashMap<>(commodities));
    }

    public String marketId() { return marketId; }
    public String displayName() { return displayName; }
    public String workstationId() { return workstationId; }
    public boolean hasWorkstation() { return !workstationId.isBlank(); }
    public Map<String, CommodityPlan> commodities() { return commodities; }
    public CommodityPlan commodity(String id) { return commodities.get(id); }
}
