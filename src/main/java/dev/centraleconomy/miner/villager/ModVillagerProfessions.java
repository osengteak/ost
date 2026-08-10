package dev.centraleconomy.miner.villager;

import com.google.common.collect.ImmutableSet;
import dev.centraleconomy.miner.CentralEconomyMod;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;

public final class ModVillagerProfessions {
    private static final String[] MARKET_IDS = {
            "farmer", "rancher", "fisher", "miner", "lumberjack",
            "mason", "fletcher", "librarian", "cleric", "cartographer"
    };
    private static final Map<String, ResourceKey<VillagerProfession>> KEYS = new LinkedHashMap<>();
    private static final Map<String, VillagerProfession> PROFESSIONS = new LinkedHashMap<>();

    static {
        for (String marketId : MARKET_IDS) register(marketId);
    }

    private ModVillagerProfessions() {}

    private static void register(String marketId) {
        ResourceKey<VillagerProfession> key = ResourceKey.create(
                Registries.VILLAGER_PROFESSION, CentralEconomyMod.id(marketId));
        ResourceKey<PoiType> poiKey = ModPoiTypes.key(marketId);
        Predicate<Holder<PoiType>> workstation = holder -> holder.is(poiKey);
        VillagerProfession profession = new VillagerProfession(
                Component.translatable("entity.central_economy.villager." + marketId),
                workstation,
                workstation,
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_TOOLSMITH,
                new Int2ObjectOpenHashMap<>()
        );
        KEYS.put(marketId, key);
        PROFESSIONS.put(marketId, Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, key, profession));
    }

    public static ResourceKey<VillagerProfession> key(String marketId) { return KEYS.get(marketId); }

    public static String marketForProfession(Villager villager) {
        if (villager == null) return null;
        for (Map.Entry<String, ResourceKey<VillagerProfession>> e : KEYS.entrySet()) {
            if (villager.getVillagerData().profession().is(e.getValue())) return e.getKey();
        }
        return null;
    }

    public static boolean isOurs(Villager villager) { return marketForProfession(villager) != null; }
    public static Map<String, ResourceKey<VillagerProfession>> allKeys() { return Map.copyOf(KEYS); }

    public static void initialize() {
        CentralEconomyMod.LOGGER.info("Registered {} Central Economy villager professions", KEYS.size());
    }
}
