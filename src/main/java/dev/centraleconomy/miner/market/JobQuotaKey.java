package dev.centraleconomy.miner.market;

import java.util.UUID;

/** Profession-wide procurement ledger key: player UUID × market × cycle. */
public record JobQuotaKey(UUID playerId, String marketId, long cycleId) {
    public JobQuotaKey {
        if (playerId == null || marketId == null || marketId.isBlank()) {
            throw new IllegalArgumentException("invalid job quota key");
        }
    }
}
