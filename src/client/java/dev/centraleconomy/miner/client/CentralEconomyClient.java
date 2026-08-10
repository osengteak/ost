package dev.centraleconomy.miner.client;

import dev.centraleconomy.miner.net.MinerMarketSnapshotS2CPayload;
import dev.centraleconomy.miner.net.OpenMinerMarketC2SPayload;
import dev.centraleconomy.miner.villager.ModVillagerProfessions;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;

public final class CentralEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MinerMarketSnapshotS2CPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    MinerMarketView view = MinerMarketView.parse(payload.json());
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof MinerMarketScreen screen) screen.update(view);
                    else mc.gui.setScreen(new MinerMarketScreen(view));
                }));

        UseEntityCallback.EVENT.register((player, level, hand, entity, hitResult) -> {
            if (!(entity instanceof Villager villager)) return InteractionResult.PASS;
            if (!villager.getVillagerData().profession().is(ModVillagerProfessions.MINER_KEY)) return InteractionResult.PASS;
            if (level.isClientSide()) ClientPlayNetworking.send(new OpenMinerMarketC2SPayload(Integer.toString(villager.getId())));
            return InteractionResult.SUCCESS;
        });
    }
}
