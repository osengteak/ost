package dev.centraleconomy.miner.villager;

import java.util.function.Predicate;

import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

public final class ModVillagerProfessions {
    public static final ResourceKey<VillagerProfession> MINER_KEY =
            ResourceKey.create(Registries.VILLAGER_PROFESSION, CentralEconomyMod.id("miner"));

    public static final VillagerProfession MINER = registerMiner();

    private ModVillagerProfessions() {}

    private static VillagerProfession registerMiner() {
        Predicate<Holder<PoiType>> minerWorkstation = holder -> holder.is(ModPoiTypes.MINER_WORKSTATION_KEY);

        VillagerProfession profession = new VillagerProfession(
                net.minecraft.network.chat.Component.translatable("entity.central_economy.villager.miner"),
                minerWorkstation,
                minerWorkstation,
                ImmutableSet.of(),
                ImmutableSet.of(),
                SoundEvents.VILLAGER_WORK_TOOLSMITH,
                new Int2ObjectOpenHashMap<>()
        );

        return Registry.register(BuiltInRegistries.VILLAGER_PROFESSION, MINER_KEY, profession);
    }

    public static void initialize() {
        CentralEconomyMod.LOGGER.debug("Registered miner profession: {}", MINER_KEY);
    }
}
