package dev.centraleconomy.miner.plan;

public record TierPlan(int lotItems, int emeralds, int baseUses, double extraUseProbability) {
    public TierPlan {
        if (lotItems <= 0) throw new IllegalArgumentException("lotItems must be > 0");
        if (emeralds <= 0) throw new IllegalArgumentException("emeralds must be > 0");
        if (baseUses < 0) throw new IllegalArgumentException("baseUses must be >= 0");
        if (extraUseProbability < 0.0 || extraUseProbability >= 1.0) {
            throw new IllegalArgumentException("extraUseProbability must be in [0,1)");
        }
    }

    public int usesForRoll(double roll) {
        if (roll < 0.0 || roll >= 1.0) throw new IllegalArgumentException("roll must be in [0,1)");
        return baseUses + (roll < extraUseProbability ? 1 : 0);
    }
}
