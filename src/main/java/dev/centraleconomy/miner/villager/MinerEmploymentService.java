package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.market.MarketSavedData;
import dev.centraleconomy.miner.market.WorkstationClaim;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic miner employment around minecraft:chiseled_quartz_block.
 *
 * The normal custom POI/profession registration is still present. This service
 * is an explicit server-side fallback so that the miner vertical slice does not
 * depend on vanilla brain scheduling to become testable/playable.
 *
 * Rules:
 *  - one chiseled quartz workstation -> at most one automatically-employed miner
 *  - unemployed adult villagers within 8x4x8 can become miners
 *  - automatically-employed miners lose the profession if their claimed block is removed
 *  - command-spawned/custom miners without an automatic claim are preserved
 *  - every miner gets a visible [광부] badge unless the player gave it a custom name
 */
public final class MinerEmploymentService {
    private static final int INTERVAL_TICKS = 20;
    private static final int SEARCH_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 4;
    private static final double PLAYER_SCAN_RADIUS = 64.0;

    private MinerEmploymentService() {}

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(MinerEmploymentService::tick);
    }

    private static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL_TICKS != 0) return;
        if (level.getServer() == null || level.players().isEmpty()) return;

        MarketSavedData saved = MarketSavedData.get(level.getServer());
        String dimension = level.dimension().toString();

        Map<UUID, Villager> nearby = collectNearbyVillagers(level);
        boolean dirty = cleanupClaims(level, saved, dimension, nearby);

        Set<Long> claimed = new HashSet<>();
        saved.state().workstationClaims().values().stream()
                .filter(c -> c.dimensionId().equals(dimension))
                .forEach(c -> claimed.add(c.blockPos()));

        for (Villager villager : nearby.values()) {
            if (!villager.isAlive() || villager.isBaby()) continue;

            if (isMiner(villager)) {
                MinerVisualIdentity.ensure(villager);

                // Command-created miners may have no claim. If a workstation is
                // nearby, claim one; otherwise leave the explicit profession alone.
                if (!saved.state().workstationClaims().containsKey(villager.getUUID())) {
                    BlockPos workstation = nearestFreeWorkstation(level, villager, claimed);
                    if (workstation != null) {
                        long packed = workstation.asLong();
                        saved.state().workstationClaims().put(
                                villager.getUUID(), new WorkstationClaim(dimension, packed));
                        claimed.add(packed);
                        dirty = true;
                    }
                }
                continue;
            }

            if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) continue;

            BlockPos workstation = nearestFreeWorkstation(level, villager, claimed);
            if (workstation == null) continue;

            try {
                villager.setVillagerData(
                        villager.getVillagerData().withProfession(
                                level.registryAccess(), ModVillagerProfessions.MINER_KEY));
                MinerVisualIdentity.ensure(villager);

                long packed = workstation.asLong();
                saved.state().workstationClaims().put(
                        villager.getUUID(), new WorkstationClaim(dimension, packed));
                claimed.add(packed);
                dirty = true;

                CentralEconomyMod.LOGGER.info(
                        "Villager {} became miner at chiseled quartz workstation {}",
                        villager.getUUID(), workstation);
            } catch (RuntimeException e) {
                CentralEconomyMod.LOGGER.error(
                        "Could not assign miner profession to villager {}",
                        villager.getUUID(), e);
            }
        }

        if (dirty) saved.touch();
    }

    private static Map<UUID, Villager> collectNearbyVillagers(ServerLevel level) {
        Map<UUID, Villager> result = new HashMap<>();
        for (var player : level.players()) {
            var villagers = level.getEntitiesOfClass(
                    Villager.class,
                    player.getBoundingBox().inflate(PLAYER_SCAN_RADIUS),
                    v -> v.isAlive() && !v.isBaby());
            for (Villager villager : villagers) result.put(villager.getUUID(), villager);
        }
        return result;
    }

    /**
     * Clean stale automatic claims. A miner created by this employment service
     * reverts to NONE if its claimed chiseled-quartz block disappears.
     */
    private static boolean cleanupClaims(
            ServerLevel level,
            MarketSavedData saved,
            String dimension,
            Map<UUID, Villager> nearby) {
        boolean dirty = false;
        var iterator = saved.state().workstationClaims().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            WorkstationClaim claim = entry.getValue();
            if (!claim.dimensionId().equals(dimension)) continue;

            BlockPos pos = BlockPos.of(claim.blockPos());
            Villager villager = nearby.get(entry.getKey());
            boolean blockStillValid = level.getBlockState(pos).is(Blocks.CHISELED_QUARTZ_BLOCK);

            if (villager == null) {
                // Do not discard claims merely because the villager is outside the
                // active player scan. It will be checked when loaded/near a player.
                if (!blockStillValid) {
                    iterator.remove();
                    dirty = true;
                }
                continue;
            }

            if (!blockStillValid) {
                iterator.remove();
                dirty = true;
                if (isMiner(villager)) {
                    try {
                        villager.setVillagerData(
                                villager.getVillagerData().withProfession(
                                        level.registryAccess(), VillagerProfession.NONE));
                        MinerVisualIdentity.clearIfOurs(villager);
                        CentralEconomyMod.LOGGER.info(
                                "Miner {} lost workstation and became unemployed",
                                villager.getUUID());
                    } catch (RuntimeException e) {
                        CentralEconomyMod.LOGGER.error(
                                "Could not release miner {} after workstation removal",
                                villager.getUUID(), e);
                    }
                }
                continue;
            }

            if (!isMiner(villager)) {
                iterator.remove();
                dirty = true;
            } else {
                MinerVisualIdentity.ensure(villager);
            }
        }
        return dirty;
    }

    private static boolean isMiner(Villager villager) {
        return villager.getVillagerData().profession().is(ModVillagerProfessions.MINER_KEY);
    }

    private static BlockPos nearestFreeWorkstation(ServerLevel level, Villager villager, Set<Long> claimed) {
        BlockPos center = villager.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (claimed.contains(pos.asLong())) continue;
                    if (!level.getBlockState(pos).is(Blocks.CHISELED_QUARTZ_BLOCK)) continue;

                    double distance = villager.distanceToSqr(
                            pos.getX() + 0.5,
                            pos.getY() + 0.5,
                            pos.getZ() + 0.5);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = pos.immutable();
                    }
                }
            }
        }
        return best;
    }
}
