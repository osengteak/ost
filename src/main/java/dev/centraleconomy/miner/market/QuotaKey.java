package dev.centraleconomy.miner.market;

import java.util.UUID;

public record QuotaKey(UUID playerId, String commodityId, long cycleId) {
    public QuotaKey {
        if (playerId == null || commodityId == null || commodityId.isBlank()) throw new IllegalArgumentException("invalid quota key");
    }
}
