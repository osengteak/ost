import dev.centraleconomy.miner.client.MinerMarketLayout;
import dev.centraleconomy.miner.net.MinerTradeRequest;

public final class UiAndRequestSelfTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        int[][] sizes = {
                {320, 240}, {426, 240}, {640, 360}, {824, 464}, {1280, 720}, {1648, 928}
        };
        for (int[] size : sizes) {
            MinerMarketLayout.Metrics m = MinerMarketLayout.calculate(size[0], size[1]);
            String suffix = " for " + size[0] + "x" + size[1];
            check(m.left() >= 0, "layout left >= 0" + suffix);
            check(m.right() <= size[0], "layout right fits screen" + suffix);
            check(m.searchX() >= m.left(), "search starts inside panel" + suffix);
            check(m.sortX() + m.sortWidth() <= m.right(), "search/filter controls fit panel" + suffix);
            check(m.favoriteX() >= m.left(), "favorite column starts inside panel" + suffix);
            check(m.itemX() >= m.favoriteX() + m.favoriteWidth(), "item column follows favorite control" + suffix);
            check(m.quotaX() >= m.itemX() + m.itemWidth(), "quota column follows item column" + suffix);
            check(m.sellX() >= m.quotaX() + m.quotaWidth(), "sell column follows quota column" + suffix);
            check(m.buyX() >= m.sellX() + m.sellWidth(), "buy column follows sell column" + suffix);
            check(m.buyX() + m.buyWidth() == m.right(), "columns exactly fill panel" + suffix);
            check(m.visibleRows() >= 1, "at least one row visible" + suffix);
            check(m.listBottom() <= size[1], "list bottom fits screen" + suffix);
        }

        MinerMarketLayout.Metrics compact = MinerMarketLayout.calculate(426, 240);
        check(compact.maxScroll(128) > 0, "large librarian catalog is vertically scrollable");
        check(compact.maxScroll(152) >= compact.maxScroll(128), "cleric catalog scroll range scales with rows");
        check(compact.insideList(compact.left() + 1, compact.rowTop() + 1), "list hit testing works");
        check(!compact.insideList(compact.left() - 1, compact.rowTop() + 1), "list rejects x outside panel");
        check(!compact.insideList(compact.left() + 1, compact.listBottom()), "list rejects y below list");

        MinerTradeRequest sell = new MinerTradeRequest(42, MinerTradeRequest.Direction.SELL, "minecraft:iron_ingot");
        check(MinerTradeRequest.parse(sell.encode()).equals(sell), "SELL request round-trip");
        MinerTradeRequest specialBuy = new MinerTradeRequest(7, MinerTradeRequest.Direction.BUY,
                "central_economy:enchanted_book/lunge/3");
        check(MinerTradeRequest.parse(specialBuy.encode()).equals(specialBuy), "special variant BUY request round-trip");

        boolean malformedRejected = false;
        try { MinerTradeRequest.parse("bad|REQUEST"); }
        catch (RuntimeException e) { malformedRejected = true; }
        check(malformedRejected, "malformed trade request rejected");

        boolean delimiterRejected = false;
        try { new MinerTradeRequest(1, MinerTradeRequest.Direction.BUY, "minecraft:iron|ingot"); }
        catch (IllegalArgumentException e) { delimiterRejected = true; }
        check(delimiterRejected, "delimiter injection rejected");

        boolean negativeEntityRejected = false;
        try { new MinerTradeRequest(-1, MinerTradeRequest.Direction.SELL, "minecraft:coal"); }
        catch (IllegalArgumentException e) { negativeEntityRejected = true; }
        check(negativeEntityRejected, "negative entity id rejected");

        System.out.println("PASS: responsive UI and generic trade request invariants");
    }
}
