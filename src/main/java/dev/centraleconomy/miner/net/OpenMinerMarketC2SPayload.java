package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** String form keeps the wire contract deliberately tiny and stable. */
public record OpenMinerMarketC2SPayload(String entityId) implements CustomPacketPayload {
    public static final Type<OpenMinerMarketC2SPayload> TYPE = new Type<>(CentralEconomyMod.id("open_miner_market"));
    public static OpenMinerMarketC2SPayload of(int entityId) {
        return new OpenMinerMarketC2SPayload(Integer.toString(entityId));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenMinerMarketC2SPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, OpenMinerMarketC2SPayload::entityId, OpenMinerMarketC2SPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
