package dev.centraleconomy.miner.client;

/**
 * Pure layout math for the miner market.  Keeping this separate from Screen
 * makes width/height regressions testable without launching Minecraft.
 */
public final class MinerMarketLayout {
    public static final int ROW_HEIGHT = 30;
    public static final int ROW_BUTTON_HEIGHT = 20;

    private MinerMarketLayout() {}

    public static Metrics calculate(int screenWidth, int screenHeight) {
        int margin = 12;
        int availableWidth = Math.max(1, screenWidth - margin * 2);
        int panelWidth = Math.min(720, availableWidth);
        int left = Math.max(0, (screenWidth - panelWidth) / 2);

        int searchY = 42;
        int headerY = 75;
        int rowTop = 88;
        int footerReserve = 42;
        int usableBottom = Math.max(rowTop + ROW_HEIGHT, screenHeight - footerReserve);
        int visibleRows = Math.max(1, (usableBottom - rowTop) / ROW_HEIGHT);
        int listBottom = rowTop + visibleRows * ROW_HEIGHT;
        int messageY = Math.min(Math.max(12, screenHeight - 18), listBottom + 7);

        int controlGap = 4;
        int searchButtonWidth = 44;
        int favoritesWidth = 82;
        int sortWidth = 72;
        int fixedControls = searchButtonWidth + favoritesWidth + sortWidth + controlGap * 3;
        int searchWidth = Math.max(70, panelWidth - fixedControls);
        // Never allow controls to spill past the panel on unusually narrow screens.
        if (searchWidth + fixedControls > panelWidth) {
            searchWidth = Math.max(1, panelWidth - fixedControls);
        }
        int searchX = left;
        int searchButtonX = searchX + searchWidth + controlGap;
        int favoritesX = searchButtonX + searchButtonWidth + controlGap;
        int sortX = favoritesX + favoritesWidth + controlGap;

        int starWidth = 24;
        int gap = 4;
        int contentWidth = Math.max(1, panelWidth - starWidth - gap * 4);
        int itemWidth = Math.max(1, (int) Math.floor(contentWidth * 0.29));
        int quotaWidth = Math.max(1, (int) Math.floor(contentWidth * 0.14));
        int sellWidth = Math.max(1, (int) Math.floor(contentWidth * 0.285));
        int buyWidth = Math.max(1, contentWidth - itemWidth - quotaWidth - sellWidth);

        int favoriteX = left;
        int itemX = favoriteX + starWidth + gap;
        int quotaX = itemX + itemWidth + gap;
        int sellX = quotaX + quotaWidth + gap;
        int buyX = sellX + sellWidth + gap;

        return new Metrics(
                left, panelWidth,
                searchX, searchY, searchWidth,
                searchButtonX, searchButtonWidth,
                favoritesX, favoritesWidth,
                sortX, sortWidth,
                headerY, rowTop, visibleRows, listBottom, messageY,
                favoriteX, starWidth,
                itemX, itemWidth,
                quotaX, quotaWidth,
                sellX, sellWidth,
                buyX, buyWidth);
    }

    public record Metrics(
            int left, int panelWidth,
            int searchX, int searchY, int searchWidth,
            int searchButtonX, int searchButtonWidth,
            int favoritesX, int favoritesWidth,
            int sortX, int sortWidth,
            int headerY, int rowTop, int visibleRows, int listBottom, int messageY,
            int favoriteX, int favoriteWidth,
            int itemX, int itemWidth,
            int quotaX, int quotaWidth,
            int sellX, int sellWidth,
            int buyX, int buyWidth) {

        public int right() { return left + panelWidth; }
        public int rowY(int visibleIndex) { return rowTop + visibleIndex * ROW_HEIGHT; }
        public int maxScroll(int rowCount) { return Math.max(0, rowCount - visibleRows); }
        public boolean insideList(double mouseX, double mouseY) {
            return mouseX >= left && mouseX < right() && mouseY >= rowTop && mouseY < listBottom;
        }
    }
}
