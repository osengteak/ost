package dev.centraleconomy.miner.client;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.net.MarketSnapshotFraming;
import dev.centraleconomy.miner.net.MinerMarketSnapshotS2CPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class CentralEconomyClient implements ClientModInitializer {
    private static final MarketSnapshotFraming.Assembler SNAPSHOT_ASSEMBLER = new MarketSnapshotFraming.Assembler();

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(MinerMarketSnapshotS2CPayload.TYPE, (payload, context) -> {
            final MarketSnapshotFraming.Complete complete;
            try {
                complete = SNAPSHOT_ASSEMBLER.accept(payload.frame());
            } catch (IllegalArgumentException e) {
                CentralEconomyMod.LOGGER.error("[CE-MARKET] client rejected malformed snapshot frame", e);
                return;
            }
            if (complete == null) return;

            CentralEconomyMod.LOGGER.info("[CE-MARKET] client reassembled snapshot parts={} chars={}",
                    complete.partCount(), complete.totalChars());
            context.client().execute(() -> {
                try {
                    MinerMarketView view = MinerMarketView.parse(complete.json());
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
        CentralEconomyMod.LOGGER.info("[CE-MARKET] client chunked snapshot receiver registered");
    }
}
