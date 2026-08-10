package dev.centraleconomy.miner.market;

/** Persisted fallback employment claim. Dimension id is namespaced, position is packed BlockPos long. */
public record WorkstationClaim(String dimensionId, long blockPos) {
    public WorkstationClaim {
        if (dimensionId == null || dimensionId.isBlank()) throw new IllegalArgumentException("dimensionId required");
    }
}
