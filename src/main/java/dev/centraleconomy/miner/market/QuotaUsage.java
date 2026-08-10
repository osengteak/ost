package dev.centraleconomy.miner.market;

public record QuotaUsage(int aUsed, int bUsed) {
    public QuotaUsage {
        if (aUsed < 0 || bUsed < 0) throw new IllegalArgumentException("usage cannot be negative");
    }
    public QuotaUsage useA() { return new QuotaUsage(aUsed + 1, bUsed); }
    public QuotaUsage useB() { return new QuotaUsage(aUsed, bUsed + 1); }
}
