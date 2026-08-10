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
import java.nio.file.StandardOpenOption;
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
            JsonObject bundled = readBundled();
            int bundledSchema = requiredInt(bundled, "schema");

            if (Files.notExists(configPath)) {
                writeConfig(bundled);
                CentralEconomyMod.LOGGER.info("Created default miner_plan.json schema={}", bundledSchema);
            } else {
                migrateOlderConfigIfNeeded(bundled, bundledSchema);
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                current = parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            CentralEconomyMod.LOGGER.info(
                    "Loaded miner economy plan: schema={}, {} commodities, {} day cycle",
                    current.schema(), current.commodities().size(), current.planningCycleDays());
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

    private JsonObject readBundled() throws IOException {
        try (InputStream in = MinerPlanRepository.class.getResourceAsStream(BUNDLED)) {
            if (in == null) throw new IOException("bundled miner_plan.json missing");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        }
    }

    private void migrateOlderConfigIfNeeded(JsonObject bundled, int bundledSchema) throws IOException {
        JsonObject existing;
        try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            existing = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (RuntimeException e) {
            Path backup = configPath.resolveSibling("miner_plan.invalid.backup.json");
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            writeConfig(bundled);
            CentralEconomyMod.LOGGER.warn(
                    "Existing miner_plan.json was invalid; backed it up to {} and restored bundled schema",
                    backup);
            return;
        }

        int existingSchema = existing.has("schema") ? existing.get("schema").getAsInt() : 0;
        if (existingSchema < bundledSchema) {
            Path backup = configPath.resolveSibling("miner_plan.schema" + existingSchema + ".backup.json");
            Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
            writeConfig(bundled);
            CentralEconomyMod.LOGGER.info(
                    "Migrated miner_plan.json schema {} -> {}; previous file backed up to {}",
                    existingSchema, bundledSchema, backup);
        }
    }

    private void writeConfig(JsonObject root) throws IOException {
        Files.writeString(
                configPath,
                GSON.toJson(root) + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
    }

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
