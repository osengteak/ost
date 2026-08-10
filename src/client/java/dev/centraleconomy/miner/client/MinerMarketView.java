package dev.centraleconomy.miner.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

public record MinerMarketView(int entityId, long cycle, int cycleDays, long turnoverEmeralds, String message, List<Row> rows) {
    public record Row(
            String item,
            boolean sellOpen, String tier, int sellItems, int sellEmeralds, int tierRemaining, int aRemaining, int bRemaining,
            boolean buyExists, boolean buyOpen, String buyReason, int buyItems, int buyEmeralds, int stock, String gate) {}

    public static MinerMarketView parse(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Row> rows = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("rows")) {
            JsonObject r = element.getAsJsonObject();
            rows.add(new Row(
                    r.get("item").getAsString(),
                    r.get("sell_open").getAsBoolean(), r.get("tier").getAsString(), r.get("sell_items").getAsInt(),
                    r.get("sell_emeralds").getAsInt(), r.get("tier_remaining").getAsInt(), r.get("a_remaining").getAsInt(), r.get("b_remaining").getAsInt(),
                    r.get("buy_exists").getAsBoolean(), r.get("buy_open").getAsBoolean(), r.get("buy_reason").getAsString(),
                    r.get("buy_items").getAsInt(), r.get("buy_emeralds").getAsInt(), r.get("stock").getAsInt(), r.get("gate").getAsString()));
        }
        return new MinerMarketView(
                root.get("entity_id").getAsInt(), root.get("cycle").getAsLong(), root.get("cycle_days").getAsInt(),
                root.get("turnover_e").getAsLong(), root.get("message").getAsString(), List.copyOf(rows));
    }
}
