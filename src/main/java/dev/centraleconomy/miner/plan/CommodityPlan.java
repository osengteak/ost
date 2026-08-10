package dev.centraleconomy.miner.plan;

public record CommodityPlan(String commodityId, TierPlan procurementA, TierPlan procurementB, RetailPlan retail) {
    public CommodityPlan {
        if (commodityId == null || commodityId.isBlank()) throw new IllegalArgumentException("commodityId required");
        if (procurementA == null || procurementB == null) throw new IllegalArgumentException("A/B procurement required");
    }

    public boolean hasRetail() { return retail != null; }
}
