package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Blocks;

/** Miner workstation POI. The agreed workstation is vanilla Chiseled Quartz Block. */
public final class ModPoiTypes {
    public static final Identifier MINER_WORKSTATION_ID = CentralEconomyMod.id("miner_workstation");
    public static final ResourceKey<PoiType> MINER_WORKSTATION_KEY =
            ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, MINER_WORKSTATION_ID);

    // One ticket means one workstation is not intentionally shared by several villagers.
    public static final PoiType MINER_WORKSTATION =
            PoiHelper.register(MINER_WORKSTATION_ID, 1, 1, Blocks.CHISELED_QUARTZ_BLOCK);

    private ModPoiTypes() {}
    public static void initialize() {
        CentralEconomyMod.LOGGER.info("Miner workstation POI registered on minecraft:chiseled_quartz_block");
    }
}
