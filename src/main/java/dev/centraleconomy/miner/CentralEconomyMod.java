package dev.centraleconomy.miner;

import dev.centraleconomy.miner.block.ModBlocks;
import dev.centraleconomy.miner.command.CentralEconomyCommands;
import dev.centraleconomy.miner.market.MinerMarketRuntime;
import dev.centraleconomy.miner.net.MinerMarketNetworking;
import dev.centraleconomy.miner.villager.MinerEmploymentService;
import dev.centraleconomy.miner.villager.MinerInteractionService;
import dev.centraleconomy.miner.villager.ModPoiTypes;
import dev.centraleconomy.miner.villager.ModVillagerProfessions;
import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CentralEconomyMod implements ModInitializer {
    public static final String MOD_ID = "central_economy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        ModBlocks.initialize();
        ModPoiTypes.initialize();
        ModVillagerProfessions.initialize();
        MinerMarketRuntime.reload();
        MinerMarketNetworking.initialize();
        MinerInteractionService.initialize();
        MinerEmploymentService.initialize();
        CentralEconomyCommands.initialize();
        LOGGER.info("Central Economy 1.0.0 initialized: 10 workstation professions + wandering trader market");
    }
}
