package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

public final class MinerMarketNetworking {
    private MinerMarketNetworking() {}

    public static void initialize() {
        // OpenMinerMarketC2SPayload is kept as a diagnostics/backward-compatible
        // route, but normal gameplay in 0.6.0 opens from the server UseEntity event.
        PayloadTypeRegistry.serverboundPlay().register(OpenMinerMarketC2SPayload.TYPE, OpenMinerMarketC2SPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(ExecuteMinerTradeC2SPayload.TYPE, ExecuteMinerTradeC2SPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(MinerMarketSnapshotS2CPayload.TYPE, MinerMarketSnapshotS2CPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(OpenMinerMarketC2SPayload.TYPE, (payload, context) -> {
            try {
                int entityId = Integer.parseInt(payload.entityId());
                CentralEconomyMod.LOGGER.info(
                        "[CE-MARKET] diagnostic C2S open packet player={} entityId={}",
                        context.player().getGameProfile().name(), entityId);
                MinerMarketTransactions.open(context.player(), entityId);
            } catch (NumberFormatException e) {
                CentralEconomyMod.LOGGER.warn(
                        "Rejected malformed miner market open packet from {}",
                        context.player().getGameProfile().name());
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(ExecuteMinerTradeC2SPayload.TYPE, (payload, context) -> {
            String[] parts = payload.request().split("\\|", 3);
            if (parts.length != 3) {
                CentralEconomyMod.LOGGER.warn("Rejected malformed miner transaction packet: wrong field count");
                return;
            }
            try {
                MinerMarketTransactions.execute(
                        context.player(), Integer.parseInt(parts[0]), parts[1], parts[2]);
            } catch (NumberFormatException e) {
                CentralEconomyMod.LOGGER.warn("Rejected malformed miner transaction packet: bad entity id");
            }
        });

        CentralEconomyMod.LOGGER.info("[CE-MARKET] payload codecs and server receivers registered");
    }
}
