package dev.centraleconomy.miner.market;

import java.util.UUID;

/** Per-player quota is shared by every NPC endpoint of the same profession market. */
public record QuotaKey(UUID playerId, String marketId, String commodityId, long cycleId) {
    public QuotaKey {
        if (playerId == null || marketId == null || marketId.isBlank()
                || commodityId == null || commodityId.isBlank()) {
            throw new IllegalArgumentException("invalid quota key");
        }
    }
}
