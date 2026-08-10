package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record MinerMarketSnapshotS2CPayload(String json) implements CustomPacketPayload {
    public static final Type<MinerMarketSnapshotS2CPayload> TYPE = new Type<>(CentralEconomyMod.id("miner_market_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MinerMarketSnapshotS2CPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, MinerMarketSnapshotS2CPayload::json, MinerMarketSnapshotS2CPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
