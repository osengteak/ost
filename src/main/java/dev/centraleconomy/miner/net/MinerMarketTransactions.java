package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.market.MarketSavedData;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.MinerMarketRuntime;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.RetailQuote;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative adapter between player inventory and the pure central-market engine. */
public final class MinerMarketTransactions {
    private MinerMarketTransactions() {}

    public static void open(ServerPlayer player, int entityId) {
        Villager villager = validatedMiner(player, entityId);
        if (villager == null) return;
        sendSnapshot(player, villager, "");
    }

    public static void execute(ServerPlayer player, int entityId, String direction, String commodityId) {
        Villager villager = validatedMiner(player, entityId);
        if (villager == null) return;
        if (!"BUY".equals(direction) && !"SELL".equals(direction)) {
            sendSnapshot(player, villager, "잘못된 거래 요청");
            return;
        }

        MinecraftServer server = player.getServer();
        MarketSavedData saved = MarketSavedData.get(server);
        MinerMarketEngine engine = MinerMarketRuntime.engine();
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        long cycle = engine.cycleId(overworld == null ? 0L : overworld.getGameTime());
        engine.ensureCycle(saved.state(), cycle);

        Item commodity = resolveItem(commodityId);
        if (commodity == null || commodity == Items.AIR) {
            sendSnapshot(player, villager, "등록되지 않은 품목: " + commodityId);
            return;
        }

        String message;
        if ("SELL".equals(direction)) {
            ProcurementQuote q = engine.quoteProcurement(saved.state(), player.getUUID(), commodityId, cycle);
            if (!q.open()) {
                message = "이 품목의 이번 계획주기 매입 한도가 끝났습니다.";
            } else if (count(player, commodity) < q.itemCount()) {
                message = "판매할 물품이 부족합니다. " + q.itemCount() + "개가 필요합니다.";
            } else {
                removeExactly(player, commodity, q.itemCount());
                engine.consumeProcurement(saved.state(), player.getUUID(), commodityId, cycle);
                giveOrDrop(player, new ItemStack(Items.EMERALD, q.emeralds()));
                saved.touch();
                message = "국가 매입 " + q.tier() + "단계: " + q.itemCount() + "개 → 에메랄드 " + q.emeralds() + "개";
            }
        } else {
            RetailQuote q = engine.quoteRetail(saved.state(), commodityId);
            if (!q.available()) {
                message = retailReason(q.reason());
            } else if (count(player, Items.EMERALD) < q.emeralds()) {
                message = "에메랄드가 부족합니다. " + q.emeralds() + "개가 필요합니다.";
            } else {
                removeExactly(player, Items.EMERALD, q.emeralds());
                engine.consumeRetail(saved.state(), commodityId);
                giveOrDrop(player, new ItemStack(commodity, q.itemCount()));
                saved.touch();
                message = "국가 판매: 에메랄드 " + q.emeralds() + "개 → " + q.itemCount() + "개";
            }
        }
        sendSnapshot(player, villager, message);
    }

    public static void sendSnapshot(ServerPlayer player, Villager villager, String message) {
        MinecraftServer server = player.getServer();
        MarketSavedData saved = MarketSavedData.get(server);
        MinerMarketEngine engine = MinerMarketRuntime.engine();
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        long cycle = engine.cycleId(overworld == null ? 0L : overworld.getGameTime());
        engine.ensureCycle(saved.state(), cycle);
        saved.touch();
        String json = MinerMarketSnapshot.create(villager.getId(), player, engine, saved.state(), cycle, message);
        ServerPlayNetworking.send(player, new MinerMarketSnapshotS2CPayload(json));
    }

    private static Villager validatedMiner(ServerPlayer player, int entityId) {
        Entity entity = player.level().getEntity(entityId);
        if (!(entity instanceof Villager villager)) return null;
        if (!villager.isAlive() || player.distanceToSqr(villager) > 36.0) return null;
        if (!villager.getVillagerData().profession().is(dev.centraleconomy.miner.villager.ModVillagerProfessions.MINER_KEY)) return null;
        return villager;
    }

    private static Item resolveItem(String id) {
        try {
            int colon = id.indexOf(':');
            Identifier key = colon < 0
                    ? Identifier.fromNamespaceAndPath("minecraft", id)
                    : Identifier.fromNamespaceAndPath(id.substring(0, colon), id.substring(colon + 1));
            return BuiltInRegistries.ITEM.getValue(key);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(item)) count = Math.addExact(count, stack.getCount());
        }
        return count;
    }

    private static void removeExactly(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        var inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.is(item)) continue;
            int take = Math.min(stack.getCount(), remaining);
            stack.shrink(take);
            remaining -= take;
        }
        if (remaining != 0) throw new IllegalStateException("inventory changed during validated transaction");
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.addItem(stack) && !stack.isEmpty()) player.drop(stack, false);
    }

    private static String retailReason(String reason) {
        if (reason == null) return "구매할 수 없습니다.";
        if (reason.equals("sold_out")) return "중앙시장 공용 재고가 소진되었습니다.";
        if (reason.equals("not_retailed")) return "국가 판매 대상이 아닙니다.";
        if (reason.equals("inactive_cycle")) return "이번 계획주기에는 판매가 배정되지 않은 희소 품목입니다.";
        if (reason.startsWith("gate:market_warehouse")) return "시장 창고 조건이 필요합니다.";
        if (reason.startsWith("gate:regional_trade_route_or_50e_turnover")) return "지역 교역로 또는 누적 시장거래 50E가 필요합니다.";
        if (reason.startsWith("gate:mineral_warehouse_and_150e_turnover")) return "광물 비축창고와 누적 시장거래 150E가 필요합니다.";
        return reason;
    }
}
