package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;

public final class MinerMarketNetworking {
    private MinerMarketNetworking() {}

    public static void initialize() {
        // OpenMinerMarketC2SPayload remains only as a diagnostic/backward-compatible route.
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
            CentralEconomyMod.LOGGER.info(
                    "[CE-TRADE] server received player={} payload={}",
                    context.player().getGameProfile().name(), payload.request());
            try {
                MinerTradeRequest request = MinerTradeRequest.parse(payload.request());
                MinerMarketTransactions.execute(context.player(), request);
            } catch (IllegalArgumentException e) {
                CentralEconomyMod.LOGGER.warn(
                        "[CE-TRADE] rejected malformed request from {}: {}",
                        context.player().getGameProfile().name(), e.getMessage());
                context.player().sendSystemMessage(
                        Component.literal("[Central Economy] 잘못된 거래 요청이 거부되었습니다."), true);
            } catch (RuntimeException e) {
                CentralEconomyMod.LOGGER.error(
                        "[CE-TRADE] unhandled transaction failure for {}",
                        context.player().getGameProfile().name(), e);
                context.player().sendSystemMessage(
                        Component.literal("[Central Economy] 거래 처리 중 오류가 발생했습니다. latest.log를 확인하세요."), true);
            }
        });

        CentralEconomyMod.LOGGER.info("[CE-MARKET] payload codecs and server receivers registered");
    }
}
