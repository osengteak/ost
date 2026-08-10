package dev.centraleconomy.miner.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Client-local favorites, namespaced by Minecraft player UUID. */
public final class MinerFavorites {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("central_economy").resolve("miner_favorites.json");
    private MinerFavorites() {}

    public static Set<String> load(UUID player) {
        Set<String> out = new HashSet<>();
        try {
            if (!Files.exists(PATH)) return out;
            JsonObject root = JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!root.has(player.toString())) return out;
            for (var value : root.getAsJsonArray(player.toString())) out.add(value.getAsString());
        } catch (Exception ignored) {}
        return out;
    }

    public static void save(UUID player, Set<String> favorites) {
        try {
            Files.createDirectories(PATH.getParent());
            JsonObject root = Files.exists(PATH)
                    ? JsonParser.parseString(Files.readString(PATH, StandardCharsets.UTF_8)).getAsJsonObject()
                    : new JsonObject();
            JsonArray array = new JsonArray();
            favorites.stream().sorted().forEach(array::add);
            root.add(player.toString(), array);
            Files.writeString(PATH, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (Exception ignored) {}
    }
}
