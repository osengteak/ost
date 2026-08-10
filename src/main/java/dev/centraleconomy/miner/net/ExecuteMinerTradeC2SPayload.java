package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Compact transport wrapper; parsing/validation is delegated to MinerTradeRequest. */
public record ExecuteMinerTradeC2SPayload(String request) implements CustomPacketPayload {
    public static final Type<ExecuteMinerTradeC2SPayload> TYPE = new Type<>(CentralEconomyMod.id("execute_miner_trade"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ExecuteMinerTradeC2SPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, ExecuteMinerTradeC2SPayload::request, ExecuteMinerTradeC2SPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static ExecuteMinerTradeC2SPayload of(int entityId, String direction, String commodityId) {
        MinerTradeRequest.Direction parsed = MinerTradeRequest.Direction.valueOf(direction);
        return new ExecuteMinerTradeC2SPayload(new MinerTradeRequest(entityId, parsed, commodityId).encode());
    }
}
