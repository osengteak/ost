package dev.centraleconomy.miner.client;

import dev.centraleconomy.miner.net.MinerMarketSnapshotS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

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

    }
}
