package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.net.MinerMarketTransactions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Opens the miner market from the normal entity-interaction packet.
 *
 * Client side deliberately returns PASS so vanilla sends the ordinary
 * use-entity packet to the server. The server then recognizes our miner,
 * opens the central market snapshot and consumes the interaction before
 * vanilla Merchant handling can take over.
 */
public final class MinerInteractionService {
    private MinerInteractionService() {}

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(entity instanceof Villager villager)) return InteractionResult.PASS;
            if (!villager.getVillagerData().profession().is(ModVillagerProfessions.MINER_KEY)) {
                return InteractionResult.PASS;
            }

            // Important: PASS on the logical client keeps the normal interaction
            // packet flowing to the server instead of short-circuiting it locally.
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            MinerMarketTransactions.open(serverPlayer, villager.getId());
            return InteractionResult.SUCCESS;
        });
    }
}
