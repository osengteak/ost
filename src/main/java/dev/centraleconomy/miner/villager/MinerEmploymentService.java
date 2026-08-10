package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.block.ModBlocks;
import dev.centraleconomy.miner.market.MarketSavedData;
import dev.centraleconomy.miner.market.WorkstationClaim;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Authoritative 1:1 employment state machine shared by all ten workstation professions.
 * Persistent claims, not vanilla job-site AI, are the source of truth.
 */
public final class MinerEmploymentService {
    private static final int INTERVAL_TICKS = 20;
    private static final int SEARCH_RADIUS = 8;
    private static final int VERTICAL_RADIUS = 4;
    private static final double PLAYER_SCAN_RADIUS = 64.0;

    private record Workstation(String marketId, BlockPos pos, double distance) {}

    private MinerEmploymentService() {}

    public static void initialize() {
        ServerTickEvents.END_LEVEL_TICK.register(MinerEmploymentService::tick);
    }

    /** Returns the authoritative market id for this employed villager, or null. */
    public static String activeMarket(ServerLevel level, Villager villager) {
        if (level.getServer() == null || villager == null || !villager.isAlive() || villager.isBaby()) return null;
        WorkstationClaim claim = MarketSavedData.get(level.getServer()).state().workstationClaims().get(villager.getUUID());
        return hasValidClaim(level, claim) ? claim.marketId() : null;
    }

    public static boolean isActiveMiner(ServerLevel level, Villager villager) {
        return "miner".equals(activeMarket(level, villager));
    }

    public static WorkstationClaim claimFor(ServerLevel level, Villager villager) {
        if (level.getServer() == null) return null;
        WorkstationClaim claim = MarketSavedData.get(level.getServer()).state().workstationClaims().get(villager.getUUID());
        return hasValidClaim(level, claim) ? claim : null;
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
            if (hasValidClaim(level, current)) {
                MinerVisualIdentity.ensureBadge(villager, current.marketId());
                continue;
            }

            String registeredMarket = ModVillagerProfessions.marketForProfession(villager);
            if (registeredMarket != null) {
                Workstation workstation = nearestFreeWorkstation(level, villager, claimedPositions, registeredMarket);
                if (workstation != null) {
                    createClaim(saved, dimension, villager, workstation, claimedPositions);
                    MinerVisualIdentity.ensureBadge(villager, registeredMarket);
                    dirty = true;
                    CentralEconomyMod.LOGGER.info("[CE-EMPLOY] attached {} villager {} to {}", registeredMarket, villager.getUUID(), workstation.pos());
                }
                continue;
            }

            if (!villager.getVillagerData().profession().is(VillagerProfession.NONE)) continue;
            Workstation workstation = nearestFreeWorkstation(level, villager, claimedPositions, null);
            if (workstation == null) continue;

            long packed = workstation.pos().asLong();
            saved.state().workstationClaims().put(villager.getUUID(), new WorkstationClaim(workstation.marketId(), dimension, packed));
            claimedPositions.add(packed);
            dirty = true;

            try {
                villager.setVillagerData(villager.getVillagerData().withProfession(
                        level.registryAccess(), ModVillagerProfessions.key(workstation.marketId())));
                MinerVisualIdentity.ensureBadge(villager, workstation.marketId());
                CentralEconomyMod.LOGGER.info("[CE-EMPLOY] hired villager {} as {} at {}", villager.getUUID(), workstation.marketId(), workstation.pos());
            } catch (RuntimeException e) {
                saved.state().workstationClaims().remove(villager.getUUID());
                claimedPositions.remove(packed);
                dirty = true;
                CentralEconomyMod.LOGGER.error("[CE-EMPLOY] failed to assign {} to {}", workstation.marketId(), villager.getUUID(), e);
            }
        }
        if (dirty) saved.touch();
    }

    private static boolean reconcileExistingClaims(ServerLevel level, MarketSavedData saved, String dimension, Map<UUID, Villager> nearby) {
        boolean dirty = false;
        var iterator = saved.state().workstationClaims().entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            WorkstationClaim claim = entry.getValue();
            if (!claim.dimensionId().equals(dimension)) continue;
            Villager villager = nearby.get(entry.getKey());
            if (villager == null) continue;

            if (!hasValidClaim(level, claim)) {
                iterator.remove();
                dirty = true;
                releaseVillager(level, villager, claim.marketId(), "workstation removed or changed");
                continue;
            }

            String currentMarket = ModVillagerProfessions.marketForProfession(villager);
            boolean none = villager.getVillagerData().profession().is(VillagerProfession.NONE);
            if (!none && currentMarket == null) {
                iterator.remove();
                dirty = true;
                MinerVisualIdentity.clearIfOurs(villager);
                CentralEconomyMod.LOGGER.info("[CE-EMPLOY] released {} claim for {} because another profession took over", claim.marketId(), villager.getUUID());
                continue;
            }
            if (currentMarket != null && !currentMarket.equals(claim.marketId())) {
                iterator.remove();
                dirty = true;
                releaseVillager(level, villager, claim.marketId(), "profession/workstation mismatch");
                continue;
            }
            MinerVisualIdentity.ensureBadge(villager, claim.marketId());
        }
        return dirty;
    }

    private static void releaseVillager(ServerLevel level, Villager villager, String marketId, String reason) {
        try {
            if (ModVillagerProfessions.isOurs(villager)) {
                villager.setVillagerData(villager.getVillagerData().withProfession(level.registryAccess(), VillagerProfession.NONE));
            }
            MinerVisualIdentity.clearIfOurs(villager);
            CentralEconomyMod.LOGGER.info("[CE-EMPLOY] {} {} became unemployed: {}", marketId, villager.getUUID(), reason);
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error("[CE-EMPLOY] could not release {} villager {}", marketId, villager.getUUID(), e);
        }
    }

    private static void createClaim(MarketSavedData saved, String dimension, Villager villager, Workstation workstation, Set<Long> claimedPositions) {
        long packed = workstation.pos().asLong();
        saved.state().workstationClaims().put(villager.getUUID(), new WorkstationClaim(workstation.marketId(), dimension, packed));
        claimedPositions.add(packed);
    }

    private static boolean hasValidClaim(ServerLevel level, WorkstationClaim claim) {
        if (claim == null || !claim.dimensionId().equals(dimensionId(level))) return false;
        BlockPos pos = BlockPos.of(claim.blockPos());
        String actualMarket = ModBlocks.marketForBlock(level.getBlockState(pos));
        return claim.marketId().equals(actualMarket);
    }

    private static String dimensionId(ServerLevel level) { return level.dimension().toString(); }

    private static Map<UUID, Villager> collectNearbyVillagers(ServerLevel level) {
        Map<UUID, Villager> result = new HashMap<>();
        for (var player : level.players()) {
            var villagers = level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(PLAYER_SCAN_RADIUS), v -> v.isAlive() && !v.isBaby());
            for (Villager villager : villagers) result.put(villager.getUUID(), villager);
        }
        return result;
    }

    /** preferredMarket null means choose the nearest free workstation of any Central Economy profession. */
    private static Workstation nearestFreeWorkstation(ServerLevel level, Villager villager, Set<Long> claimed, String preferredMarket) {
        BlockPos center = villager.blockPosition();
        Workstation best = null;
        for (int dy = -VERTICAL_RADIUS; dy <= VERTICAL_RADIUS; dy++) {
            for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    if (claimed.contains(pos.asLong())) continue;
                    String marketId = ModBlocks.marketForBlock(level.getBlockState(pos));
                    if (marketId == null) continue;
                    if (preferredMarket != null && !preferredMarket.equals(marketId)) continue;
                    double distance = villager.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                    if (best == null || distance < best.distance()) best = new Workstation(marketId, pos.immutable(), distance);
                }
            }
        }
        return best;
    }
}
