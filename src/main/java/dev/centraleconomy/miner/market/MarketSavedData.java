package dev.centraleconomy.miner.market;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import dev.centraleconomy.miner.CentralEconomyMod;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.Map;
import java.util.UUID;

/** One overworld-scoped authoritative market ledger shared by every miner NPC on the server. */
public final class MarketSavedData extends SavedData {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MarketMutableState state;

    private static final Codec<MarketSavedData> CODEC = Codec.STRING.xmap(MarketSavedData::fromJson, MarketSavedData::toJson);
    private static final SavedDataType<MarketSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CentralEconomyMod.MOD_ID, "market_state"),
            MarketSavedData::new,
            CODEC,
            null
    );

    public MarketSavedData() { this(new MarketMutableState()); }
    private MarketSavedData(MarketMutableState state) { this.state = state; }
    public MarketMutableState state() { return state; }
    public void touch() { setDirty(); }

    public static MarketSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld unavailable");
        return overworld.getDataStorage().computeIfAbsent(TYPE);
    }

    private String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("cycle", state.initializedCycle());
        root.addProperty("turnover_e", state.cumulativeTurnoverEmeralds());

        JsonObject stock = new JsonObject();
        state.retailStock().forEach(stock::addProperty);
        root.add("stock", stock);

        JsonArray flags = new JsonArray();
        state.infrastructureFlags().stream().sorted().forEach(flags::add);
        root.add("flags", flags);

        JsonArray quotas = new JsonArray();
        state.quotaUsage().entrySet().stream()
                .sorted((a,b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> {
                    JsonObject q = new JsonObject();
                    q.addProperty("player", e.getKey().playerId().toString());
                    q.addProperty("commodity", e.getKey().commodityId());
                    q.addProperty("cycle", e.getKey().cycleId());
                    q.addProperty("a", e.getValue().aUsed());
                    q.addProperty("b", e.getValue().bUsed());
                    quotas.add(q);
                });
        root.add("quotas", quotas);

        JsonArray claims = new JsonArray();
        state.workstationClaims().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    JsonObject c = new JsonObject();
                    c.addProperty("villager", e.getKey().toString());
                    c.addProperty("dimension", e.getValue().dimensionId());
                    c.addProperty("pos", e.getValue().blockPos());
                    claims.add(c);
                });
        root.add("workstation_claims", claims);
        return GSON.toJson(root);
    }

    private static MarketSavedData fromJson(String json) {
        MarketMutableState state = new MarketMutableState();
        if (json == null || json.isBlank()) return new MarketSavedData(state);
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("cycle")) state.initializedCycle(root.get("cycle").getAsLong());
            if (root.has("turnover_e")) state.cumulativeTurnoverEmeralds(root.get("turnover_e").getAsLong());
            if (root.has("stock")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("stock").entrySet()) {
                    state.retailStock().put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
                }
            }
            if (root.has("flags")) root.getAsJsonArray("flags").forEach(v -> state.infrastructureFlags().add(v.getAsString()));
            if (root.has("quotas")) root.getAsJsonArray("quotas").forEach(v -> {
                JsonObject q = v.getAsJsonObject();
                QuotaKey key = new QuotaKey(UUID.fromString(q.get("player").getAsString()), q.get("commodity").getAsString(), q.get("cycle").getAsLong());
                state.quotaUsage().put(key, new QuotaUsage(q.get("a").getAsInt(), q.get("b").getAsInt()));
            });
            if (root.has("workstation_claims")) root.getAsJsonArray("workstation_claims").forEach(v -> {
                JsonObject c = v.getAsJsonObject();
                state.workstationClaims().put(
                        UUID.fromString(c.get("villager").getAsString()),
                        new WorkstationClaim(c.get("dimension").getAsString(), c.get("pos").getAsLong()));
            });
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error("Invalid persisted market_state; starting safe empty state", e);
            return new MarketSavedData(new MarketMutableState());
        }
        return new MarketSavedData(state);
    }
}
