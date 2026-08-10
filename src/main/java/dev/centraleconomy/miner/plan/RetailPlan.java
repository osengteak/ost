package dev.centraleconomy.miner.plan;

public record RetailPlan(int lotItems, int emeralds, int uses, double activationProbability, String gate) {
    public RetailPlan {
        if (lotItems <= 0 || emeralds <= 0 || uses < 0) throw new IllegalArgumentException("invalid retail plan");
        if (activationProbability < 0.0 || activationProbability > 1.0) throw new IllegalArgumentException("activationProbability must be in [0,1]");
        if (gate == null || gate.isBlank()) gate = "none";
    }

    public int initialStock() {
        return Math.multiplyExact(lotItems, uses);
    }
}
