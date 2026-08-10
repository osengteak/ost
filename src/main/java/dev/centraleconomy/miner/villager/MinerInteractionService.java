package dev.centraleconomy.miner.villager;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.net.MinerMarketTransactions;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.core.registries.BuiltInRegistries;

/** One server-authoritative interaction entrypoint for every Central Economy market endpoint. */
public final class MinerInteractionService {
    private MinerInteractionService() {}

    public static void initialize() {
        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

            if (entity instanceof Villager villager) {
                String marketId = MinerEmploymentService.activeMarket(serverLevel, villager);
                CentralEconomyMod.LOGGER.info("[CE-MARKET] interact player={} villager={} market={}",
                        serverPlayer.getGameProfile().name(), villager.getUUID(), marketId);
                if (marketId == null) return InteractionResult.PASS;
                MinerMarketTransactions.open(serverPlayer, villager.getId());
                return InteractionResult.SUCCESS;
            }

            if (isWanderingTrader(entity)) {
                CentralEconomyMod.LOGGER.info("[CE-MARKET] interact player={} wanderingTrader={}",
                        serverPlayer.getGameProfile().name(), entity.getUUID());
                MinerMarketTransactions.open(serverPlayer, entity.getId());
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
        CentralEconomyMod.LOGGER.info("[CE-MARKET] server villager/wandering-trader interaction hook registered");
    }

    /**
     * Do not reference the concrete wandering-trader Java class here. Minecraft 26.2
     * moved NPC implementation classes, while the stable registry identity remains
     * minecraft:wandering_trader. Using the registry id keeps this endpoint resilient
     * to package/class moves and works for the generic Entity supplied by the callback.
     */
    private static boolean isWanderingTrader(net.minecraft.world.entity.Entity entity) {
        var id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return id != null && "minecraft:wandering_trader".equals(id.toString());
    }
}
