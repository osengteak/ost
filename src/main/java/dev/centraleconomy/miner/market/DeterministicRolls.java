package dev.centraleconomy.miner.market;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** Reproducible pseudo-random rolls: relogging or changing miner NPC cannot reroll a quota. */
public final class DeterministicRolls {
    private DeterministicRolls() {}

    public static double playerTier(UUID playerId, String commodityId, long cycleId, String tier) {
        return unit("quota|" + playerId + "|" + commodityId + "|" + cycleId + "|" + tier);
    }

    public static double globalActivation(String commodityId, long cycleId) {
        return unit("activation|" + commodityId + "|" + cycleId);
    }

    static double unit(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) value = (value << 8) | (digest[i] & 0xffL);
            long mantissa = value >>> 11;
            return mantissa * 0x1.0p-53;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
