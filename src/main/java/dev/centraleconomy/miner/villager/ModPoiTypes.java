package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.world.poi.PoiHelper;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.block.Block;

import java.util.LinkedHashMap;
import java.util.Map;

/** POI registry for the ten player-placeable Central Economy workstations. */
public final class ModPoiTypes {
    private static final Map<String, ResourceKey<PoiType>> KEYS = new LinkedHashMap<>();
    private static final Map<String, PoiType> TYPES = new LinkedHashMap<>();

    static {
        ModBlocks.allWorkstations().forEach(ModPoiTypes::register);
    }

    private ModPoiTypes() {}

    private static void register(String marketId, Block block) {
        var id = CentralEconomyMod.id(marketId + "_workstation");
        ResourceKey<PoiType> key = ResourceKey.create(Registries.POINT_OF_INTEREST_TYPE, id);
        PoiType type = PoiHelper.register(id, 1, 1, block);
        KEYS.put(marketId, key);
        TYPES.put(marketId, type);
    }

    public static ResourceKey<PoiType> key(String marketId) { return KEYS.get(marketId); }
    public static Map<String, ResourceKey<PoiType>> allKeys() { return Map.copyOf(KEYS); }

    public static void initialize() {
        CentralEconomyMod.LOGGER.info("Registered {} Central Economy workstation POIs", KEYS.size());
    }
}
