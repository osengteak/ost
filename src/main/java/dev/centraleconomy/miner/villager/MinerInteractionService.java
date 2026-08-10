package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.net.MinerMarketTransactions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Single authoritative interaction entrypoint.
 *
 * <p>The client always returns PASS so Minecraft sends its ordinary use-entity
 * packet. The server checks the persistent employment claim and, only for a
 * valid miner contract, consumes the interaction and opens the central market.</p>
 */
public final class MinerInteractionService {
    private MinerInteractionService() {}

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(entity instanceof Villager villager)) return InteractionResult.PASS;

            // Never open screens or decide employment on the logical client.
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            boolean activeMiner = MinerEmploymentService.isActiveMiner(serverLevel, villager);
            CentralEconomyMod.LOGGER.info(
                    "[CE-MARKET] interact player={} villager={} activeMiner={}",
                    serverPlayer.getGameProfile().name(), villager.getUUID(), activeMiner);

            if (!activeMiner) return InteractionResult.PASS;

            MinerMarketTransactions.open(serverPlayer, villager.getId());
            return InteractionResult.SUCCESS;
        });

        CentralEconomyMod.LOGGER.info("[CE-MARKET] server villager interaction hook registered");
    }
}
