package dev.centraleconomy.miner.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.centraleconomy.miner.CentralEconomyMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Loads all economic numbers from JSON. Java code contains no miner prices or quotas. */
public final class MinerPlanRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String BUNDLED = "/data/central_economy/economy/miner_plan.json";
    private final Path configPath;
    private volatile MinerPlan current;

    public MinerPlanRepository() {
        this.configPath = FabricLoader.getInstance().getConfigDir().resolve("central_economy").resolve("miner_plan.json");
    }

    public synchronized MinerPlan loadOrReload() {
        try {
            Files.createDirectories(configPath.getParent());
            if (Files.notExists(configPath)) {
                try (InputStream in = MinerPlanRepository.class.getResourceAsStream(BUNDLED)) {
                    if (in == null) throw new IOException("bundled miner_plan.json missing");
                    Files.copy(in, configPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                current = parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            CentralEconomyMod.LOGGER.info("Loaded miner economy plan: {} commodities, {} day cycle", current.commodities().size(), current.planningCycleDays());
            return current;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + configPath, e);
        }
    }

    public MinerPlan current() {
        MinerPlan value = current;
        if (value == null) return loadOrReload();
        return value;
    }

    public Path configPath() { return configPath; }

    private static MinerPlan parse(JsonObject root) {
        int schema = requiredInt(root, "schema");
        int cycleDays = requiredInt(root, "planning_cycle_days");
        JsonObject commoditiesJson = root.getAsJsonObject("commodities");
        if (commoditiesJson == null || commoditiesJson.isEmpty()) throw new IllegalArgumentException("commodities missing");
        Map<String, CommodityPlan> commodities = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : commoditiesJson.entrySet()) {
            JsonObject value = entry.getValue().getAsJsonObject();
            TierPlan a = tier(value.getAsJsonObject("procurement_a"));
            TierPlan b = tier(value.getAsJsonObject("procurement_b"));
            RetailPlan retail = value.has("retail") ? retail(value.getAsJsonObject("retail")) : null;
            commodities.put(entry.getKey(), new CommodityPlan(entry.getKey(), a, b, retail));
        }
        return new MinerPlan(schema, cycleDays, commodities);
    }

    private static TierPlan tier(JsonObject o) {
        if (o == null) throw new IllegalArgumentException("procurement tier missing");
        return new TierPlan(requiredInt(o, "lot_items"), requiredInt(o, "emeralds"), requiredInt(o, "base_uses"), requiredDouble(o, "extra_use_probability"));
    }

    private static RetailPlan retail(JsonObject o) {
        return new RetailPlan(requiredInt(o, "lot_items"), requiredInt(o, "emeralds"), requiredInt(o, "uses"), requiredDouble(o, "activation_probability"), o.has("gate") ? o.get("gate").getAsString() : "none");
    }

    private static int requiredInt(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("missing " + key);
        return o.get(key).getAsInt();
    }
    private static double requiredDouble(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("missing " + key);
        return o.get(key).getAsDouble();
    }
}
