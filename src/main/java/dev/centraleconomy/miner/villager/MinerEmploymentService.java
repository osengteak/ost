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
 * Authoritative employment state machine for the miner vertical slice.
 *
 * <p>The persistent workstation claim is the source of truth. The registered
 * VillagerProfession is still assigned once on hire for normal Minecraft
 * semantics/appearance, but market access does not depend on vanilla keeping
 * that holder forever. This avoids the 0.5.x loop where vanilla reset the
 * profession and the mod re-hired/logged the same villager every second.</p>
 *
 * <p>State machine:</p>
 * <pre>
 * NONE + free chiseled quartz -> claim -> MINER badge/attempt profession
 * valid claim                -> stable employed miner (no re-hire)
 * claimed block removed      -> claim removed -> profession NONE -> badge removed
 * </pre>
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

    /** Server-authoritative miner check used by interaction and transaction code. */
    public static boolean isActiveMiner(ServerLevel level, Villager villager) {
        if (level.getServer() == null || villager == null || !villager.isAlive() || villager.isBaby()) return false;
        MarketSavedData saved = MarketSavedData.get(level.getServer());
        return hasValidClaim(level, villager, saved.state().workstationClaims().get(villager.getUUID()));
    }

    public static WorkstationClaim claimFor(ServerLevel level, Villager villager) {
        if (level.getServer() == null) return null;
        WorkstationClaim claim = MarketSavedData.get(level.getServer()).state().workstationClaims().get(villager.getUUID());
        return hasValidClaim(level, villager, claim) ? claim : null;
    }

    private static void tick(ServerLevel level) {
        if (level.getGameTime() % INTERVAL_TICKS != 0) return;
        if (level.getServer() == null || level.players().isEmpty()) return;

        MarketSavedData saved = MarketSavedData.get(level.getServer());
        String dimension = dimensionId(level);
        Map<UUID, Villager> nearby = collectNearbyVillagers(level);

        boolean dirty = reconcileExistingClaims(level, saved, dimension, nearby);

        Set<Long> claimedPositions = new HashSet<>();
        saved.state().workstationClaims().values().stream()
                .filter(c -> c.dimensionId().equals(dimension))
                .forEach(c -> claimedPositions.add(c.blockPos()));

        for (Villager villager : nearby.values()) {
            if (!villager.isAlive() || villager.isBaby()) continue;

            WorkstationClaim current = saved.state().workstationClaims().get(villager.getUUID());
            if (hasValidClaim(level, villager, current)) {
                // Stable state. Never re-run setVillagerData here.
                MinerVisualIdentity.ensureBadge(villager);
                continue;
            }

            // A custom miner produced by a command/debug tool can attach to a free
            // workstation. Without a workstation it is not an active market endpoint.
            if (isRegisteredMinerProfession(villager)) {
                BlockPos workstation = nearestFreeWorkstation(level, villager, claimedPositions);
                if (workstation != null) {
                    createClaim(saved, dimension, villager, workstation, claimedPositions);
                    MinerVisualIdentity.ensureBadge(villager);
                    dirty = true;
                    CentralEconomyMod.LOGGER.info(
                            "[CE-EMPLOY] attached existing miner {} to workstation {}",
                            villager.getUUID(), workstation);
                }
                continue;
            }

            // Only truly unemployed adult villagers can be hired automatically.
            if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) continue;

            BlockPos workstation = nearestFreeWorkstation(level, villager, claimedPositions);
            if (workstation == null) continue;

            long packed = workstation.asLong();
            saved.state().workstationClaims().put(villager.getUUID(), new WorkstationClaim(dimension, packed));
            claimedPositions.add(packed);
            dirty = true;

            try {
                // One assignment attempt only. The persistent claim remains the
                // gameplay authority even if vanilla later rewrites this holder.
                villager.setVillagerData(
                        villager.getVillagerData().withProfession(
                                level.registryAccess(), ModVillagerProfessions.MINER_KEY));
                MinerVisualIdentity.ensureBadge(villager);
                CentralEconomyMod.LOGGER.info(
                        "[CE-EMPLOY] hired villager {} as miner at {}",
                        villager.getUUID(), workstation);
            } catch (RuntimeException e) {
                // Never leave a half-created employment contract.
                saved.state().workstationClaims().remove(villager.getUUID());
                claimedPositions.remove(packed);
                dirty = true;
                CentralEconomyMod.LOGGER.error(
                        "[CE-EMPLOY] failed to assign miner profession to {}",
                        villager.getUUID(), e);
            }
        }

        if (dirty) saved.touch();
    }

    /**
     * Reconciles loaded villagers with their persisted employment contracts.
     * Claims are deliberately retained while a villager is outside the active
     * scan so unloading a chunk cannot silently erase employment.
     */
    private static boolean reconcileExistingClaims(
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

            Villager villager = nearby.get(entry.getKey());
            if (villager == null) continue;

            BlockPos pos = BlockPos.of(claim.blockPos());
            boolean workstationExists = level.getBlockState(pos).is(Blocks.CHISELED_QUARTZ_BLOCK);
            if (!workstationExists) {
                iterator.remove();
                dirty = true;
                releaseVillager(level, villager, "workstation removed");
                continue;
            }

            // If some other system deliberately assigned a different non-NONE
            // profession, relinquish our claim instead of fighting it forever.
            boolean none = villager.getVillagerData().profession().is(VillagerProfession.NONE);
            boolean registeredMiner = isRegisteredMinerProfession(villager);
            if (!none && !registeredMiner) {
                iterator.remove();
                dirty = true;
                MinerVisualIdentity.clearIfOurs(villager);
                CentralEconomyMod.LOGGER.info(
                        "[CE-EMPLOY] released claim for {} because another profession took over",
                        villager.getUUID());
                continue;
            }

            // NONE is acceptable here: vanilla may have reset the custom holder,
            // but the persisted contract still defines the miner gameplay state.
            MinerVisualIdentity.ensureBadge(villager);
        }
        return dirty;
    }

    private static void releaseVillager(ServerLevel level, Villager villager, String reason) {
        try {
            if (isRegisteredMinerProfession(villager)) {
                villager.setVillagerData(
                        villager.getVillagerData().withProfession(
                                level.registryAccess(), VillagerProfession.NONE));
            }
            MinerVisualIdentity.clearIfOurs(villager);
            CentralEconomyMod.LOGGER.info(
                    "[CE-EMPLOY] miner {} became unemployed: {}",
                    villager.getUUID(), reason);
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error(
                    "[CE-EMPLOY] could not release miner {}",
                    villager.getUUID(), e);
        }
    }

    private static void createClaim(
            MarketSavedData saved,
            String dimension,
            Villager villager,
            BlockPos workstation,
            Set<Long> claimedPositions) {
        long packed = workstation.asLong();
        saved.state().workstationClaims().put(
                villager.getUUID(), new WorkstationClaim(dimension, packed));
        claimedPositions.add(packed);
    }

    private static boolean hasValidClaim(ServerLevel level, Villager villager, WorkstationClaim claim) {
        if (claim == null) return false;
        if (!claim.dimensionId().equals(dimensionId(level))) return false;
        BlockPos pos = BlockPos.of(claim.blockPos());
        return level.getBlockState(pos).is(Blocks.CHISELED_QUARTZ_BLOCK);
    }

    private static boolean isRegisteredMinerProfession(Villager villager) {
        return villager.getVillagerData().profession().is(ModVillagerProfessions.MINER_KEY);
    }

    private static String dimensionId(ServerLevel level) {
        return level.dimension().toString();
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
