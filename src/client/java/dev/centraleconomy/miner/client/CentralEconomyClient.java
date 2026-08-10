package dev.centraleconomy.miner.client;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.net.MinerMarketSnapshotS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class CentralEconomyClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MinerMarketSnapshotS2CPayload.TYPE, (payload, context) -> {
            CentralEconomyMod.LOGGER.info("[CE-MARKET] client received snapshot bytes={}", payload.json().length());
            context.client().execute(() -> {
                try {
                    MinerMarketView view = MinerMarketView.parse(payload.json());
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.gui.screen() instanceof MinerMarketScreen screen) {
                        screen.update(view);
                        CentralEconomyMod.LOGGER.info("[CE-MARKET] client updated market={} entityId={}", view.marketId(), view.entityId());
                    } else {
                        mc.gui.setScreen(new MinerMarketScreen(view));
                        CentralEconomyMod.LOGGER.info("[CE-MARKET] client opened market={} entityId={}", view.marketId(), view.entityId());
                    }
                } catch (RuntimeException e) {
                    CentralEconomyMod.LOGGER.error("[CE-MARKET] client failed to open market screen", e);
                }
            });
        });
        CentralEconomyMod.LOGGER.info("[CE-MARKET] client snapshot receiver registered");
    }
}
