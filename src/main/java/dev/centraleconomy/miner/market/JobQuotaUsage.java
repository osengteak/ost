package dev.centraleconomy.miner.market;

/** Emerald payout already consumed from a profession-wide A/B budget. */
public record JobQuotaUsage(int aEmeraldsUsed, int bEmeraldsUsed) {
    public JobQuotaUsage {
        if (aEmeraldsUsed < 0 || bEmeraldsUsed < 0) {
            throw new IllegalArgumentException("job quota usage cannot be negative");
        }
    }

    public JobQuotaUsage useA(int emeralds) {
        if (emeralds <= 0) throw new IllegalArgumentException("emeralds must be positive");
        return new JobQuotaUsage(Math.addExact(aEmeraldsUsed, emeralds), bEmeraldsUsed);
    }

    public JobQuotaUsage useB(int emeralds) {
        if (emeralds <= 0) throw new IllegalArgumentException("emeralds must be positive");
        return new JobQuotaUsage(aEmeraldsUsed, Math.addExact(bEmeraldsUsed, emeralds));
    }
}
