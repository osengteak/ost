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

/** One overworld-scoped authoritative ledger for all Central Economy markets. */
public final class MarketSavedData extends SavedData {
    private static final int CURRENT_SCHEMA = 3;
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private final MarketMutableState state;
    private static final Codec<MarketSavedData> CODEC =
            Codec.STRING.xmap(MarketSavedData::fromJson, MarketSavedData::toJson);
    private static final SavedDataType<MarketSavedData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(CentralEconomyMod.MOD_ID, "market_state"),
            MarketSavedData::new, CODEC, null);

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
        root.addProperty("schema", CURRENT_SCHEMA);
        root.addProperty("cycle", state.initializedCycle());
        root.addProperty("turnover_e", state.cumulativeTurnoverEmeralds());

        JsonObject stock = new JsonObject();
        state.retailStock().forEach(stock::addProperty);
        root.add("stock", stock);

        JsonObject overflowStock = new JsonObject();
        state.retailOverflowStock().forEach(overflowStock::addProperty);
        root.add("overflow_stock", overflowStock);

        JsonArray flags = new JsonArray();
        state.infrastructureFlags().stream().sorted().forEach(flags::add);
        root.add("flags", flags);

        JsonArray quotas = new JsonArray();
        state.quotaUsage().entrySet().stream()
                .sorted((a,b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> {
                    JsonObject q = new JsonObject();
                    q.addProperty("player", e.getKey().playerId().toString());
                    q.addProperty("market", e.getKey().marketId());
                    q.addProperty("commodity", e.getKey().commodityId());
                    q.addProperty("cycle", e.getKey().cycleId());
                    q.addProperty("a", e.getValue().aUsed());
                    q.addProperty("b", e.getValue().bUsed());
                    quotas.add(q);
                });
        root.add("quotas", quotas);

        JsonArray jobQuotas = new JsonArray();
        state.jobQuotaUsage().entrySet().stream()
                .sorted((a,b) -> a.getKey().toString().compareTo(b.getKey().toString()))
                .forEach(e -> {
                    JsonObject q = new JsonObject();
                    q.addProperty("player", e.getKey().playerId().toString());
                    q.addProperty("market", e.getKey().marketId());
                    q.addProperty("cycle", e.getKey().cycleId());
                    q.addProperty("a_e", e.getValue().aEmeraldsUsed());
                    q.addProperty("b_e", e.getValue().bEmeraldsUsed());
                    jobQuotas.add(q);
                });
        root.add("job_quotas", jobQuotas);

        JsonArray claims = new JsonArray();
        state.workstationClaims().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> {
                    JsonObject c = new JsonObject();
                    c.addProperty("villager", e.getKey().toString());
                    c.addProperty("market", e.getValue().marketId());
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
        boolean migrated = false;
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            int schema = root.has("schema") ? root.get("schema").getAsInt() : 1;
            if (root.has("cycle")) state.initializedCycle(root.get("cycle").getAsLong());
            if (root.has("turnover_e")) state.cumulativeTurnoverEmeralds(root.get("turnover_e").getAsLong());

            if (root.has("stock")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("stock").entrySet()) {
                    // v0.6.x stock keys had no market namespace. Migrate those seven rows into miner.
                    String key = e.getKey().contains("|") ? e.getKey() : MarketKeys.stock("miner", e.getKey());
                    state.retailStock().put(key, Math.max(0, e.getValue().getAsInt()));
                }
            }
            if (root.has("overflow_stock")) {
                for (Map.Entry<String, com.google.gson.JsonElement> e : root.getAsJsonObject("overflow_stock").entrySet()) {
                    state.retailOverflowStock().put(e.getKey(), Math.max(0, e.getValue().getAsInt()));
                }
            }
            if (root.has("flags")) root.getAsJsonArray("flags")
                    .forEach(v -> state.infrastructureFlags().add(v.getAsString()));
            if (root.has("quotas")) root.getAsJsonArray("quotas").forEach(v -> {
                JsonObject q = v.getAsJsonObject();
                String market = q.has("market") ? q.get("market").getAsString() : "miner";
                QuotaKey key = new QuotaKey(UUID.fromString(q.get("player").getAsString()), market,
                        q.get("commodity").getAsString(), q.get("cycle").getAsLong());
                state.quotaUsage().put(key, new QuotaUsage(q.get("a").getAsInt(), q.get("b").getAsInt()));
            });
            if (root.has("job_quotas")) root.getAsJsonArray("job_quotas").forEach(v -> {
                JsonObject q = v.getAsJsonObject();
                JobQuotaKey key = new JobQuotaKey(UUID.fromString(q.get("player").getAsString()),
                        q.get("market").getAsString(), q.get("cycle").getAsLong());
                state.jobQuotaUsage().put(key, new JobQuotaUsage(q.get("a_e").getAsInt(), q.get("b_e").getAsInt()));
            });

            // Schema 2 is the v1.0.2 post-ghost-fix format. Preserve those valid claims.
            // Schema 1 claims remain untrusted and are discarded exactly once as in v1.0.2.
            if (root.has("workstation_claims") && schema >= 2) {
                root.getAsJsonArray("workstation_claims").forEach(v -> {
                    JsonObject c = v.getAsJsonObject();
                    String market = c.has("market") ? c.get("market").getAsString() : "miner";
                    state.workstationClaims().put(UUID.fromString(c.get("villager").getAsString()),
                            new WorkstationClaim(market, c.get("dimension").getAsString(), c.get("pos").getAsLong()));
                });
            } else if (root.has("workstation_claims") && !root.getAsJsonArray("workstation_claims").isEmpty()) {
                migrated = true;
                CentralEconomyMod.LOGGER.warn(
                        "[CE-MIGRATE] discarded legacy workstation claims from schema {} to repair possible ghost occupations", schema);
            }
            if (schema < CURRENT_SCHEMA) {
                // A/B item usage and C stock from schema 2 were calculated under the old price/quota model.
                // Preserve long-lived world state (turnover, infrastructure, valid schema-2 claims), but start the
                // new ABCD economy from one clean planning-cycle snapshot to avoid mixed old/new quotas.
                state.initializedCycle(Long.MIN_VALUE);
                state.quotaUsage().clear();
                state.jobQuotaUsage().clear();
                state.retailStock().clear();
                state.retailOverflowStock().clear();
                migrated = true;
                CentralEconomyMod.LOGGER.info(
                        "[CE-MIGRATE] market_state schema {} -> {}; preserved v1.0.2 claims/turnover/flags and reset only cycle quotas/stocks",
                        schema, CURRENT_SCHEMA);
            }
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error("Invalid persisted market_state; starting safe empty state", e);
            return new MarketSavedData(new MarketMutableState());
        }
        MarketSavedData data = new MarketSavedData(state);
        if (migrated) data.touch();
        return data;
    }
}
