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
        RetailPlan retail,
        RetailPlan retailOverflow,
        boolean enabled,
        String procurementContainerReturn) {

    /** v1.0.2-compatible constructor retained for pure tests and small ABI surface. */
    public CommodityPlan(
            String commodityId, String itemId, String displayName, String kind, String variantId, int level,
            TierPlan procurementA, TierPlan procurementB, RetailPlan retail) {
        this(commodityId, itemId, displayName, kind, variantId, level,
                procurementA, procurementB, retail, null, true, "");
    }

    public CommodityPlan {
        if (commodityId == null || commodityId.isBlank()) throw new IllegalArgumentException("commodityId required");
        if (itemId == null || itemId.isBlank()) throw new IllegalArgumentException("itemId required");
        if (displayName == null) displayName = "";
        if (kind == null || kind.isBlank()) kind = "item";
        if (variantId == null) variantId = "";
        if (procurementContainerReturn == null) procurementContainerReturn = "";
        if (level < 0) throw new IllegalArgumentException("level cannot be negative");
        if ((procurementA == null) != (procurementB == null)) {
            throw new IllegalArgumentException("procurement A/B must both exist or both be absent");
        }
        if (retailOverflow != null && retail == null) {
            throw new IllegalArgumentException("overflow retail requires planned retail");
        }
    }

    public boolean hasProcurement() { return enabled && procurementA != null; }
    public boolean hasRetail() { return enabled && retail != null; }
    public boolean hasRetailOverflow() { return enabled && retailOverflow != null; }
    public boolean isPlainItem() { return "item".equals(kind); }
    public boolean returnsProcurementContainer() { return !procurementContainerReturn.isBlank(); }
}
