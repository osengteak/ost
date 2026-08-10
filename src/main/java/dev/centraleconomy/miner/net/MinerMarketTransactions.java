package dev.centraleconomy.miner.net;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.market.MarketKeys;
import dev.centraleconomy.miner.market.MarketSavedData;
import dev.centraleconomy.miner.market.MinerMarketEngine;
import dev.centraleconomy.miner.market.MinerMarketRuntime;
import dev.centraleconomy.miner.market.ProcurementQuote;
import dev.centraleconomy.miner.market.RetailQuote;
import dev.centraleconomy.miner.plan.CommodityPlan;
import dev.centraleconomy.miner.plan.MarketPlan;
import dev.centraleconomy.miner.villager.MinerEmploymentService;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Server-authoritative adapter between inventories and every profession market. */
public final class MinerMarketTransactions {
    private record Endpoint(Entity entity, String marketId) {}
    private MinerMarketTransactions() {}

    public static void open(ServerPlayer player, int entityId) {
        CentralEconomyMod.LOGGER.info("[CE-MARKET] open request player={} entityId={}", player.getGameProfile().name(), entityId);
        Endpoint endpoint = validatedEndpoint(player, entityId);
        if (endpoint == null) {
            CentralEconomyMod.LOGGER.warn("[CE-MARKET] open rejected player={} entityId={} reason=invalid_endpoint", player.getGameProfile().name(), entityId);
            return;
        }
        MarketPlan market = MinerMarketRuntime.engine().requireMarket(endpoint.marketId());
        player.sendSystemMessage(Component.literal("[Central Economy] " + market.displayName() + " 중앙시장 여는 중..."), true);
        sendSnapshot(player, endpoint, "");
    }

    public static void execute(ServerPlayer player, MinerTradeRequest request) {
        CentralEconomyMod.LOGGER.info("[CE-TRADE] execute start player={} entityId={} direction={} commodity={}",
                player.getGameProfile().name(), request.entityId(), request.direction(), request.commodityId());

        Endpoint endpoint = validatedEndpoint(player, request.entityId());
        if (endpoint == null) {
            player.sendSystemMessage(Component.literal("[Central Economy] 시장 담당자와 너무 멀거나 더 이상 유효한 담당자가 아닙니다."), true);
            return;
        }

        MinecraftServer server = ((ServerLevel) player.level()).getServer();
        MarketSavedData saved = MarketSavedData.get(server);
        MinerMarketEngine engine = MinerMarketRuntime.engine();
        ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
        long cycle = engine.cycleId(overworld == null ? 0L : overworld.getGameTime());
        engine.ensureCycle(saved.state(), cycle);

        CommodityPlan cp;
        try {
            cp = engine.requireCommodity(endpoint.marketId(), request.commodityId());
        } catch (IllegalArgumentException e) {
            CentralEconomyMod.LOGGER.warn("[CE-TRADE] rejected cross/unknown commodity market={} commodity={}", endpoint.marketId(), request.commodityId());
            sendSnapshot(player, endpoint, "이 시장의 거래 대상이 아닌 품목입니다.");
            return;
        }

        String message = request.direction() == MinerTradeRequest.Direction.SELL
                ? executeSell(player, saved, engine, cycle, endpoint.marketId(), cp)
                : executeBuy(player, saved, engine, endpoint.marketId(), cp);

        player.getInventory().setChanged();
        sendSnapshot(player, endpoint, message);
    }

    private static String executeSell(ServerPlayer player, MarketSavedData saved, MinerMarketEngine engine,
                                      long cycle, String marketId, CommodityPlan cp) {
        if (!cp.hasProcurement()) return "국가 매입 대상이 아닙니다.";
        if (!cp.isPlainItem()) return "구성요소가 있는 특수 품목은 현재 국가 매입 대상이 아닙니다.";

        Item commodity = MarketStackFactory.resolvePlainItem(cp.itemId());
        if (commodity == Items.AIR) return "등록되지 않은 품목입니다: " + cp.itemId();
        ProcurementQuote q = engine.quoteProcurement(saved.state(), player.getUUID(), marketId, cp.commodityId(), cycle);
        if (!q.open()) return "이 품목의 이번 계획주기 매입 한도가 끝났습니다.";

        int available = count(player, commodity);
        if (available < q.itemCount()) {
            CentralEconomyMod.LOGGER.info("[CE-TRADE] SELL denied player={} market={} commodity={} have={} need={}",
                    player.getGameProfile().name(), marketId, cp.commodityId(), available, q.itemCount());
            return "판매할 물품이 부족합니다. " + q.itemCount() + "개가 필요합니다.";
        }

        removeExactly(player, commodity, q.itemCount());
        engine.consumeProcurement(saved.state(), player.getUUID(), marketId, cp.commodityId(), cycle);
        giveOrDrop(player, new ItemStack(Items.EMERALD, q.emeralds()));
        saved.touch();
        CentralEconomyMod.LOGGER.info("[CE-TRADE] SELL committed player={} market={} commodity={} tier={} items={} emeralds={} cycle={}",
                player.getGameProfile().name(), marketId, cp.commodityId(), q.tier(), q.itemCount(), q.emeralds(), cycle);
        return "국가 매입 " + q.tier() + "단계: " + q.itemCount() + "개 → 에메랄드 " + q.emeralds() + "개";
    }

    private static String executeBuy(ServerPlayer player, MarketSavedData saved, MinerMarketEngine engine,
                                     String marketId, CommodityPlan cp) {
        RetailQuote q = engine.quoteRetail(saved.state(), marketId, cp.commodityId());
        if (!q.available()) return retailReason(q.reason());

        final ItemStack product;
        try {
            product = MarketStackFactory.create(player, cp, q.itemCount());
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error("[CE-TRADE] cannot construct market stack market={} commodity={}", marketId, cp.commodityId(), e);
            return "상품 데이터를 생성하지 못했습니다. latest.log를 확인하세요.";
        }

        int emeralds = count(player, Items.EMERALD);
        if (emeralds < q.emeralds()) return "에메랄드가 부족합니다. " + q.emeralds() + "개가 필요합니다.";

        removeExactly(player, Items.EMERALD, q.emeralds());
        engine.consumeRetail(saved.state(), marketId, cp.commodityId());
        giveOrDrop(player, product);
        saved.touch();

        CentralEconomyMod.LOGGER.info("[CE-TRADE] BUY committed player={} market={} commodity={} items={} emeralds={} remainingStock={}",
                player.getGameProfile().name(), marketId, cp.commodityId(), q.itemCount(), q.emeralds(),
                saved.state().retailStock().getOrDefault(MarketKeys.stock(marketId, cp.commodityId()), 0));
        return "국가 판매: 에메랄드 " + q.emeralds() + "개 → " + q.itemCount() + "개";
    }

    private static void sendSnapshot(ServerPlayer player, Endpoint endpoint, String message) {
        try {
            MinecraftServer server = ((ServerLevel) player.level()).getServer();
            MarketSavedData saved = MarketSavedData.get(server);
            MinerMarketEngine engine = MinerMarketRuntime.engine();
            ServerLevel overworld = server.getLevel(ServerLevel.OVERWORLD);
            long cycle = engine.cycleId(overworld == null ? 0L : overworld.getGameTime());
            engine.ensureCycle(saved.state(), cycle);
            saved.touch();

            String json = MinerMarketSnapshot.create(endpoint.entity().getId(), endpoint.marketId(), player, engine, saved.state(), cycle, message);
            int rows = engine.requireMarket(endpoint.marketId()).commodities().size();
            CentralEconomyMod.LOGGER.info("[CE-MARKET] snapshot built player={} market={} rows={} bytes={}",
                    player.getGameProfile().name(), endpoint.marketId(), rows, json.length());
            ServerPlayNetworking.send(player, new MinerMarketSnapshotS2CPayload(json));
            CentralEconomyMod.LOGGER.info("[CE-MARKET] snapshot sent player={} market={} entity={}",
                    player.getGameProfile().name(), endpoint.marketId(), endpoint.entity().getUUID());
        } catch (RuntimeException e) {
            CentralEconomyMod.LOGGER.error("[CE-MARKET] failed to build/send market snapshot to {}", player.getGameProfile().name(), e);
            player.sendSystemMessage(Component.literal("[Central Economy] 시장 화면 전송 중 오류가 발생했습니다. latest.log를 확인하세요."), true);
        }
    }

    private static Endpoint validatedEndpoint(ServerPlayer player, int entityId) {
        ServerLevel level = (ServerLevel) player.level();
        Entity entity = level.getEntity(entityId);
        if (entity == null || !entity.isAlive() || player.distanceToSqr(entity) > 36.0) return null;
        if (entity instanceof Villager villager) {
            String marketId = MinerEmploymentService.activeMarket(level, villager);
            return marketId == null ? null : new Endpoint(villager, marketId);
        }
        if (entity instanceof WanderingTrader trader) return new Endpoint(trader, "wandering_trader");
        return null;
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
        inventory.setChanged();
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
        if (reason.startsWith("gate:library_and_150e_turnover")) return "공공도서관과 누적 시장거래 150E가 필요합니다.";
        return reason;
    }
}
