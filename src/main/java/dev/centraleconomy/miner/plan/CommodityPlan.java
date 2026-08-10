package dev.centraleconomy.miner.plan;

/**
 * One trade row. commodityId is a stable market-local transaction key and may
 * represent a configured ItemStack variant (enchanted book/potion/tipped arrow).
 */
public record CommodityPlan(
        String commodityId,
        String itemId,
        String displayName,
        String kind,
        String variantId,
        int level,
        TierPlan procurementA,
        TierPlan procurementB,
        RetailPlan retail) {

    public CommodityPlan {
        if (commodityId == null || commodityId.isBlank()) throw new IllegalArgumentException("commodityId required");
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId required");
        if (displayName == null) displayName = "";
        if (kind == null || kind.isBlank()) kind = "item";
        if (variantId == null) variantId = "";
        if (level < 0) throw new IllegalArgumentException("level cannot be negative");
        if ((procurementA == null) != (procurementB == null)) {
            throw new IllegalArgumentException("procurement A/B must both exist or both be absent");
        }
    }

    public boolean hasProcurement() { return procurementA != null; }
    public boolean hasRetail() { return retail != null; }
    public boolean isPlainItem() { return "item".equals(kind); }
}
