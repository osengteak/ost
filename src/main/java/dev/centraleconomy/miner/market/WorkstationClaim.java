package dev.centraleconomy.miner.market;

/** Persisted 1:1 employment contract: profession market + dimension + workstation position. */
public record WorkstationClaim(String marketId, String dimensionId, long blockPos) {
    public WorkstationClaim {
        if (marketId == null || marketId.isBlank()) throw new IllegalArgumentException("marketId required");
        if (dimensionId == null || dimensionId.isBlank()) throw new IllegalArgumentException("dimensionId required");
    }
}
