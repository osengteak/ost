package dev.centraleconomy.miner.client;

import dev.centraleconomy.miner.CentralEconomyMod;
import dev.centraleconomy.miner.net.ExecuteMinerTradeC2SPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** One responsive, searchable, favorite-aware, vertically scrollable UI shared by all markets. */
public final class MinerMarketScreen extends Screen {
    private MinerMarketView view;
    private EditBox search;
    private String searchText = "";
    private boolean favoritesOnly;
    private boolean sortByPrice;
    private int scrollOffset;
    private boolean tradePending;
    private int pendingTicks;
    private String localMessage = "";
    private final UUID playerId;
    private final Set<String> favorites;

    public MinerMarketScreen(MinerMarketView view) {
        super(Component.literal(view.marketName() + " 중앙시장"));
        this.view = view;
        Minecraft mc = Minecraft.getInstance();
        this.playerId = mc.player == null ? new UUID(0L, 0L) : mc.player.getUUID();
        this.favorites = MinerFavorites.load(playerId);
    }

    public void update(MinerMarketView next) {
        boolean changedMarket = !this.view.marketId().equals(next.marketId());
        this.view = next;
        this.tradePending = false;
        this.pendingTicks = 0;
        this.localMessage = "";
        if (changedMarket) scrollOffset = 0;
        if (search != null) rebuild();
    }

    @Override protected void init() { rebuild(); }

    @Override
    public void tick() {
        super.tick();
        if (!tradePending) return;
        pendingTicks++;
        if (pendingTicks >= 100) {
            tradePending = false;
            pendingTicks = 0;
            localMessage = "서버의 거래 응답이 없습니다. latest.log의 [CE-TRADE] 항목을 확인하세요.";
            CentralEconomyMod.LOGGER.warn("[CE-TRADE] client timed out waiting for trade response");
            rebuild();
        }
    }

    private void rebuild() {
        String keep = searchText;
        clearWidgets();
        MinerMarketLayout.Metrics layout = MinerMarketLayout.calculate(width, height);
        search = new EditBox(font, layout.searchX(), layout.searchY(), layout.searchWidth(), 20, Component.literal("검색"));
        search.setValue(keep);
        search.setHint(Component.literal("품목 검색..."));
        search.setResponder(value -> searchText = value);
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.literal("검색"), b -> { scrollOffset = 0; rebuild(); })
                .bounds(layout.searchButtonX(), layout.searchY(), layout.searchButtonWidth(), 20).build());
        addRenderableWidget(Button.builder(Component.literal(favoritesOnly ? "★ 즐겨찾기" : "전체"), b -> {
            favoritesOnly = !favoritesOnly; scrollOffset = 0; rebuild();
        }).bounds(layout.favoritesX(), layout.searchY(), layout.favoritesWidth(), 20).build());
        addRenderableWidget(Button.builder(Component.literal(sortByPrice ? "가격순" : "기본순"), b -> {
            sortByPrice = !sortByPrice; scrollOffset = 0; rebuild();
        }).bounds(layout.sortX(), layout.searchY(), layout.sortWidth(), 20).build());

        List<MinerMarketView.Row> filtered = filteredRows();
        scrollOffset = clampScroll(scrollOffset, filtered.size(), layout.visibleRows());
        int end = Math.min(filtered.size(), scrollOffset + layout.visibleRows());
        for (int i = scrollOffset; i < end; i++) {
            MinerMarketView.Row row = filtered.get(i);
            int y = layout.rowY(i - scrollOffset) + 4;
            String favoriteKey = favoriteKey(row);
            Button favorite = Button.builder(Component.literal(favorites.contains(favoriteKey) ? "★" : "☆"), b -> {
                if (!favorites.add(favoriteKey)) favorites.remove(favoriteKey);
                MinerFavorites.save(playerId, favorites);
                if (favoritesOnly) { scrollOffset = 0; rebuild(); }
                else b.setMessage(Component.literal(favorites.contains(favoriteKey) ? "★" : "☆"));
            }).bounds(layout.favoriteX(), y, layout.favoriteWidth(), MinerMarketLayout.ROW_BUTTON_HEIGHT).build();
            addRenderableWidget(favorite);

            Button sell = Button.builder(Component.literal(sellLabel(row)), b -> trade("SELL", row.commodityId()))
                    .bounds(layout.sellX(), y, layout.sellWidth(), MinerMarketLayout.ROW_BUTTON_HEIGHT).build();
            sell.active = !tradePending && row.sellExists() && row.sellOpen();
            addRenderableWidget(sell);

            Button buy = Button.builder(Component.literal(buyLabel(row)), b -> trade("BUY", row.commodityId()))
                    .bounds(layout.buyX(), y, layout.buyWidth(), MinerMarketLayout.ROW_BUTTON_HEIGHT).build();
            buy.active = !tradePending && row.buyExists() && row.buyOpen();
            addRenderableWidget(buy);
        }
    }

    private List<MinerMarketView.Row> filteredRows() {
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        List<MinerMarketView.Row> rows = new ArrayList<>();
        for (MinerMarketView.Row row : view.rows()) {
            if (favoritesOnly && !favorites.contains(favoriteKey(row))) continue;
            String name = displayName(row).toLowerCase(Locale.ROOT);
            String ids = (row.commodityId() + " " + row.item()).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !ids.contains(q) && !name.contains(q)) continue;
            rows.add(row);
        }
        if (sortByPrice) rows.sort(Comparator
                .comparingDouble((MinerMarketView.Row r) -> r.buyExists() ? (double) r.buyEmeralds() / Math.max(1, r.buyItems()) : Double.POSITIVE_INFINITY)
                .thenComparing(MinerMarketView.Row::commodityId));
        return rows;
    }

    private String favoriteKey(MinerMarketView.Row row) { return view.marketId() + "|" + row.commodityId(); }

    private void trade(String direction, String commodityId) {
        if (tradePending) return;
        if (!ClientPlayNetworking.canSend(ExecuteMinerTradeC2SPayload.TYPE)) {
            localMessage = "서버가 거래 패킷을 받을 준비가 되지 않았습니다. 모드 버전을 확인하세요.";
            CentralEconomyMod.LOGGER.error("[CE-TRADE] client cannot send direction={} market={} commodity={} entityId={}",
                    direction, view.marketId(), commodityId, view.entityId());
            rebuild(); return;
        }
        tradePending = true; pendingTicks = 0; localMessage = "거래 요청 전송 중..."; rebuild();
        try {
            CentralEconomyMod.LOGGER.info("[CE-TRADE] client sending direction={} market={} commodity={} entityId={}",
                    direction, view.marketId(), commodityId, view.entityId());
            ClientPlayNetworking.send(ExecuteMinerTradeC2SPayload.of(view.entityId(), direction, commodityId));
        } catch (RuntimeException e) {
            tradePending = false; pendingTicks = 0; localMessage = "거래 요청 전송 실패. latest.log를 확인하세요.";
            CentralEconomyMod.LOGGER.error("[CE-TRADE] client failed send market={} commodity={}", view.marketId(), commodityId, e);
            rebuild();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        MinerMarketLayout.Metrics layout = MinerMarketLayout.calculate(width, height);
        List<MinerMarketView.Row> rows = filteredRows();
        int max = layout.maxScroll(rows.size());
        if (max > 0 && layout.insideList(mouseX, mouseY) && verticalAmount != 0.0) {
            int next = scrollOffset + (verticalAmount < 0.0 ? 1 : -1);
            next = Math.max(0, Math.min(max, next));
            if (next != scrollOffset) { scrollOffset = next; rebuild(); }
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private static int clampScroll(int value, int rowCount, int visibleRows) {
        return Math.max(0, Math.min(Math.max(0, rowCount - visibleRows), value));
    }

    private static String sellLabel(MinerMarketView.Row row) {
        if (!row.sellExists()) return "매입 안 함";
        if (!row.sellOpen()) return "매입 한도 소진";
        return row.tier() + " " + row.sellItems() + "→" + row.sellEmeralds() + "E (" + row.tierRemaining() + ")";
    }

    private static String buyLabel(MinerMarketView.Row row) {
        if (!row.buyExists()) return "판매 안 함";
        if (!row.buyOpen()) {
            if (row.buyReason().equals("sold_out")) return "품절";
            if (row.buyReason().equals("inactive_cycle")) return "이번 주기 미배정";
            if (row.buyReason().startsWith("gate:")) return "잠김";
            return "구매 불가";
        }
        return row.buyEmeralds() + "E→" + row.buyItems() + " (" + row.stock() + ")";
    }

    private static String displayName(MinerMarketView.Row row) {
        if (row.displayName() != null && !row.displayName().isBlank()) return row.displayName();
        if (row.translationKey() != null && !row.translationKey().isBlank()) return Component.translatable(row.translationKey()).getString();
        return row.commodityId();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        MinerMarketLayout.Metrics layout = MinerMarketLayout.calculate(width, height);
        graphics.text(font, view.marketName() + " 중앙시장  |  계획주기 " + view.cycle() + " (" + view.cycleDays() + "일)  |  누적거래 " + view.turnoverEmeralds() + "E",
                layout.left(), 18, 0xFFFFFFFF, true);
        graphics.text(font, "품목", layout.itemX(), layout.headerY(), 0xFFBFBFBF, true);
        graphics.text(font, "쿼터", layout.quotaX(), layout.headerY(), 0xFFBFBFBF, true);
        graphics.text(font, "국가 매입", layout.sellX(), layout.headerY(), 0xFFBFBFBF, true);
        graphics.text(font, "국가 판매 / 재고", layout.buyX(), layout.headerY(), 0xFFBFBFBF, true);

        List<MinerMarketView.Row> filtered = filteredRows();
        int safeOffset = clampScroll(scrollOffset, filtered.size(), layout.visibleRows());
        int end = Math.min(filtered.size(), safeOffset + layout.visibleRows());
        for (int i = safeOffset; i < end; i++) {
            MinerMarketView.Row row = filtered.get(i);
            int textY = layout.rowY(i - safeOffset) + 10;
            graphics.text(font, displayName(row), layout.itemX(), textY, 0xFFFFFFFF, true);
            String quota = row.sellExists() ? "A" + row.aRemaining() + " B" + row.bRemaining() : "-";
            graphics.text(font, quota, layout.quotaX(), textY, 0xFFAAAAAA, true);
        }
        if (filtered.size() > layout.visibleRows()) {
            int from = filtered.isEmpty() ? 0 : safeOffset + 1;
            int to = Math.min(filtered.size(), safeOffset + layout.visibleRows());
            graphics.text(font, from + "-" + to + "/" + filtered.size() + "  ↕ 마우스 휠", layout.left(), layout.messageY(), 0xFFBFBFBF, true);
        }
        String message = !localMessage.isBlank() ? localMessage : view.message();
        if (!message.isBlank()) graphics.text(font, message, layout.left(), Math.min(height - 8, layout.messageY() + 12), 0xFFFFFF80, true);
    }
}
