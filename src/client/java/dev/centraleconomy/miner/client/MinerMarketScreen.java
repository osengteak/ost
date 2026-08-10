package dev.centraleconomy.miner.client;

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

/** Searchable, favorite-aware miner market. Prices/quotas/stocks come only from the server snapshot. */
public final class MinerMarketScreen extends Screen {
    private static final int ROWS_PER_PAGE = 6;
    private MinerMarketView view;
    private EditBox search;
    private String searchText = "";
    private boolean favoritesOnly;
    private boolean sortByPrice;
    private int page;
    private final UUID playerId;
    private final Set<String> favorites;

    public MinerMarketScreen(MinerMarketView view) {
        super(Component.literal("광부 중앙시장"));
        this.view = view;
        Minecraft mc = Minecraft.getInstance();
        this.playerId = mc.player == null ? new UUID(0L, 0L) : mc.player.getUUID();
        this.favorites = MinerFavorites.load(playerId);
    }

    public void update(MinerMarketView next) {
        this.view = next;
        if (search != null) rebuild();
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        String keep = searchText;
        clearWidgets();
        int left = Math.max(12, width / 2 - 310);
        int top = 42;

        search = new EditBox(font, left, top, 155, 20, Component.literal("검색"));
        search.setValue(keep);
        search.setHint(Component.literal("품목 검색..."));
        search.setResponder(value -> searchText = value);
        addRenderableWidget(search);

        addRenderableWidget(Button.builder(Component.literal("검색"), b -> { page = 0; rebuild(); })
                .bounds(left + 160, top, 46, 20).build());
        addRenderableWidget(Button.builder(Component.literal(favoritesOnly ? "★ 즐겨찾기" : "전체"), b -> {
            favoritesOnly = !favoritesOnly;
            page = 0;
            rebuild();
        }).bounds(left + 212, top, 90, 20).build());
        addRenderableWidget(Button.builder(Component.literal(sortByPrice ? "가격순" : "기본순"), b -> {
            sortByPrice = !sortByPrice;
            page = 0;
            rebuild();
        }).bounds(left + 308, top, 80, 20).build());

        List<MinerMarketView.Row> filtered = filteredRows();
        int pages = Math.max(1, (filtered.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        if (page >= pages) page = pages - 1;
        int start = page * ROWS_PER_PAGE;
        int end = Math.min(filtered.size(), start + ROWS_PER_PAGE);

        for (int i = start; i < end; i++) {
            MinerMarketView.Row row = filtered.get(i);
            int y = 90 + (i - start) * 34;
            Button favorite = Button.builder(Component.literal(favorites.contains(row.item()) ? "★" : "☆"), b -> {
                if (!favorites.add(row.item())) favorites.remove(row.item());
                MinerFavorites.save(playerId, favorites);
                if (favoritesOnly) rebuild();
                else b.setMessage(Component.literal(favorites.contains(row.item()) ? "★" : "☆"));
            }).bounds(left, y, 24, 20).build();
            addRenderableWidget(favorite);

            Button sell = Button.builder(Component.literal(sellLabel(row)), b -> trade("SELL", row.item()))
                    .bounds(left + 270, y, 145, 20).build();
            sell.active = row.sellOpen();
            addRenderableWidget(sell);

            Button buy = Button.builder(Component.literal(buyLabel(row)), b -> trade("BUY", row.item()))
                    .bounds(left + 422, y, 170, 20).build();
            buy.active = row.buyExists() && row.buyOpen();
            addRenderableWidget(buy);
        }

        Button prev = Button.builder(Component.literal("<"), b -> { if (page > 0) { page--; rebuild(); } })
                .bounds(left + 230, 306, 35, 20).build();
        prev.active = page > 0;
        addRenderableWidget(prev);
        Button next = Button.builder(Component.literal(">"), b -> { if (page + 1 < pages) { page++; rebuild(); } })
                .bounds(left + 350, 306, 35, 20).build();
        next.active = page + 1 < pages;
        addRenderableWidget(next);
    }

    private List<MinerMarketView.Row> filteredRows() {
        String q = searchText.trim().toLowerCase(Locale.ROOT);
        List<MinerMarketView.Row> rows = new ArrayList<>();
        for (MinerMarketView.Row row : view.rows()) {
            if (favoritesOnly && !favorites.contains(row.item())) continue;
            String name = displayName(row.item()).toLowerCase(Locale.ROOT);
            if (!q.isEmpty() && !row.item().toLowerCase(Locale.ROOT).contains(q) && !name.contains(q)) continue;
            rows.add(row);
        }
        if (sortByPrice) rows.sort(Comparator
                .comparingDouble((MinerMarketView.Row r) -> r.buyExists() ? (double) r.buyEmeralds() / Math.max(1, r.buyItems()) : Double.POSITIVE_INFINITY)
                .thenComparing(MinerMarketView.Row::item));
        return rows;
    }

    private void trade(String direction, String item) {
        ClientPlayNetworking.send(ExecuteMinerTradeC2SPayload.of(view.entityId(), direction, item));
    }

    private static String sellLabel(MinerMarketView.Row row) {
        if (!row.sellOpen()) return "매입 한도 소진";
        return row.tier() + " 매입 " + row.sellItems() + "→" + row.sellEmeralds() + "E (" + row.tierRemaining() + ")";
    }

    private static String buyLabel(MinerMarketView.Row row) {
        if (!row.buyExists()) return "판매 안 함";
        if (!row.buyOpen()) {
            if (row.buyReason().equals("sold_out")) return "품절";
            if (row.buyReason().equals("inactive_cycle")) return "이번 주기 미배정";
            if (row.buyReason().startsWith("gate:")) return "잠김";
            return "구매 불가";
        }
        return row.buyEmeralds() + "E→" + row.buyItems() + " (재고 " + row.stock() + ")";
    }

    private static String displayName(String id) {
        int colon = id.indexOf(':');
        String namespace = colon >= 0 ? id.substring(0, colon) : "minecraft";
        String path = colon >= 0 ? id.substring(colon + 1) : id;
        if ("minecraft".equals(namespace)) return Component.translatable("item.minecraft." + path).getString();
        return id;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        int left = Math.max(12, width / 2 - 310);
        graphics.text(font, "광부 중앙시장  |  계획주기 " + view.cycle() + " (" + view.cycleDays() + "일)  |  누적거래 " + view.turnoverEmeralds() + "E", left, 18, 0xFFFFFFFF, true);
        graphics.text(font, "품목", left + 30, 75, 0xFFBFBFBF, true);
        graphics.text(font, "국가 매입", left + 270, 75, 0xFFBFBFBF, true);
        graphics.text(font, "국가 판매 / 공용재고", left + 422, 75, 0xFFBFBFBF, true);

        List<MinerMarketView.Row> filtered = filteredRows();
        int start = Math.min(filtered.size(), page * ROWS_PER_PAGE);
        int end = Math.min(filtered.size(), start + ROWS_PER_PAGE);
        for (int i = start; i < end; i++) {
            MinerMarketView.Row row = filtered.get(i);
            int y = 96 + (i - start) * 34;
            graphics.text(font, displayName(row.item()), left + 30, y, 0xFFFFFFFF, true);
            graphics.text(font, "A:" + row.aRemaining() + " B:" + row.bRemaining(), left + 155, y, 0xFFAAAAAA, true);
        }
        int pages = Math.max(1, (filtered.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
        graphics.text(font, (page + 1) + "/" + pages, left + 300, 312, 0xFFFFFFFF, true);
        if (!view.message().isBlank()) graphics.text(font, view.message(), left, 340, 0xFFFFFF80, true);
    }
}
