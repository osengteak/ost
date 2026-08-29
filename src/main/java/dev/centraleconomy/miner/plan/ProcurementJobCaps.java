package dev.centraleconomy.miner.plan;

/** Per-player, per-market, per-cycle emerald payout ceilings layered above item quotas. */
public record ProcurementJobCaps(int aEmeraldsPerPlayerPerCycle, int bEmeraldsPerPlayerPerCycle) {
    public static final ProcurementJobCaps UNLIMITED = new ProcurementJobCaps(0, 0);

    public ProcurementJobCaps {
        if (aEmeraldsPerPlayerPerCycle < 0 || bEmeraldsPerPlayerPerCycle < 0) {
            throw new IllegalArgumentException("job procurement caps cannot be negative");
        }
    }

    /** Zero intentionally means "no extra profession-wide cap" for backwards compatibility. */
    public boolean hasACap() { return aEmeraldsPerPlayerPerCycle > 0; }
    public boolean hasBCap() { return bEmeraldsPerPlayerPerCycle > 0; }
}
