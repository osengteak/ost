package dev.centraleconomy.miner.plan;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.centraleconomy.miner.CentralEconomyMod;
import net.fabricmc.loader.api.FabricLoader;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Economic values are external JSON. Java owns invariants, not prices. */
public final class MinerPlanRepository {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String BUNDLED = "/data/central_economy/economy/economy_plan.json";
    private final Path configPath =
            FabricLoader.getInstance().getConfigDir().resolve("central_economy").resolve("economy_plan.json");
    private volatile MinerPlan current;

    public synchronized MinerPlan loadOrReload() {
        try {
            Files.createDirectories(configPath.getParent());
            JsonObject bundled = readBundled();
            int bundledSchema = requiredInt(bundled, "schema");
            if (Files.notExists(configPath)) {
                writeConfig(bundled);
            } else {
                JsonObject existing;
                try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                    existing = JsonParser.parseReader(reader).getAsJsonObject();
                } catch (RuntimeException e) {
                    Path backup = configPath.resolveSibling("economy_plan.invalid.backup.json");
                    Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
                    writeConfig(bundled);
                    existing = bundled;
                }
                int existingSchema = existing.has("schema") ? existing.get("schema").getAsInt() : 0;
                if (existingSchema < bundledSchema) {
                    Path backup = configPath.resolveSibling("economy_plan.schema" + existingSchema + ".backup.json");
                    Files.copy(configPath, backup, StandardCopyOption.REPLACE_EXISTING);
                    writeConfig(bundled);
                    CentralEconomyMod.LOGGER.info("Migrated economy_plan schema {} -> {}", existingSchema, bundledSchema);
                }
            }
            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                current = parse(JsonParser.parseReader(reader).getAsJsonObject());
            }
            CentralEconomyMod.LOGGER.info(
                    "Loaded Central Economy plan: schema={}, markets={}, rows={}, cycle={} days",
                    current.schema(), current.markets().size(), current.commodityCount(), current.planningCycleDays());
            return current;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + configPath, e);
        }
    }

    public MinerPlan current() {
        MinerPlan value = current;
        return value == null ? loadOrReload() : value;
    }

    private JsonObject readBundled() throws Exception {
        try (InputStream in = MinerPlanRepository.class.getResourceAsStream(BUNDLED)) {
            if (in == null) throw new IllegalStateException("bundled economy_plan.json missing");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private void writeConfig(JsonObject root) throws Exception {
        Files.writeString(configPath, GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static MinerPlan parse(JsonObject root) {
        int schema = requiredInt(root, "schema");
        int cycleDays = requiredInt(root, "planning_cycle_days");
        JsonObject marketsJson = root.getAsJsonObject("markets");
        if (marketsJson == null || marketsJson.isEmpty()) throw new IllegalArgumentException("markets missing");
        Map<String, MarketPlan> markets = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> marketEntry : marketsJson.entrySet()) {
            String marketId = marketEntry.getKey();
            JsonObject mo = marketEntry.getValue().getAsJsonObject();
            String display = mo.get("display_name").getAsString();
            String workstation = mo.has("workstation") && !mo.get("workstation").isJsonNull()
                    ? mo.get("workstation").getAsString() : "";
            ProcurementJobCaps jobCaps = mo.has("procurement_job_caps")
                    ? jobCaps(mo.getAsJsonObject("procurement_job_caps"))
                    : ProcurementJobCaps.UNLIMITED;
            JsonObject commoditiesJson = mo.getAsJsonObject("commodities");
            if (commoditiesJson == null || commoditiesJson.isEmpty()) {
                throw new IllegalArgumentException("commodities missing for market " + marketId);
            }
            Map<String, CommodityPlan> commodities = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : commoditiesJson.entrySet()) {
                JsonObject o = entry.getValue().getAsJsonObject();
                String commodityId = entry.getKey();
                String itemId = requiredString(o, "item");
                String displayName = o.has("display_name") ? o.get("display_name").getAsString() : "";
                String kind = o.has("kind") ? o.get("kind").getAsString() : "item";
                String variant = o.has("variant") ? o.get("variant").getAsString() : "";
                int level = o.has("level") ? o.get("level").getAsInt() : 0;
                boolean enabled = !o.has("enabled") || o.get("enabled").getAsBoolean();
                TierPlan a = o.has("procurement_a") ? tier(o.getAsJsonObject("procurement_a")) : null;
                TierPlan b = o.has("procurement_b") ? tier(o.getAsJsonObject("procurement_b")) : null;
                RetailPlan retail = o.has("retail") ? retail(o.getAsJsonObject("retail")) : null;
                RetailPlan overflow = o.has("retail_overflow") ? retail(o.getAsJsonObject("retail_overflow")) : null;
                String containerReturn = o.has("procurement_container_return")
                        ? o.get("procurement_container_return").getAsString() : "";
                commodities.put(commodityId,
                        new CommodityPlan(commodityId, itemId, displayName, kind, variant, level,
                                a, b, retail, overflow, enabled, containerReturn));
            }
            markets.put(marketId, new MarketPlan(marketId, display, workstation, commodities, jobCaps));
        }
        return new MinerPlan(schema, cycleDays, markets);
    }

    private static ProcurementJobCaps jobCaps(JsonObject o) {
        int a = o.has("a_emeralds_per_player_per_cycle")
                ? o.get("a_emeralds_per_player_per_cycle").getAsInt() : 0;
        int b = o.has("b_emeralds_per_player_per_cycle")
                ? o.get("b_emeralds_per_player_per_cycle").getAsInt() : 0;
        return new ProcurementJobCaps(a, b);
    }

    private static TierPlan tier(JsonObject o) {
        return new TierPlan(requiredInt(o, "lot_items"), requiredInt(o, "emeralds"),
                requiredInt(o, "base_uses"), requiredDouble(o, "extra_use_probability"));
    }

    private static RetailPlan retail(JsonObject o) {
        return new RetailPlan(requiredInt(o, "lot_items"), requiredInt(o, "emeralds"),
                requiredInt(o, "uses"), requiredDouble(o, "activation_probability"),
                o.has("gate") ? o.get("gate").getAsString() : "none");
    }

    private static String requiredString(JsonObject o, String key) {
        if (!o.has(key)) throw new IllegalArgumentException("missing " + key);
        return o.get(key).getAsString();
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
