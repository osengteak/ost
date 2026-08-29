package dev.centraleconomy.miner.market;

/** Current authoritative retail tier. C = planned stock, D = overflow stock. */
public record RetailQuote(boolean available, String reason, String tier, int itemCount, int emeralds, int stockItems) {
    public static RetailQuote unavailable(String reason) {
        return new RetailQuote(false, reason, "CLOSED", 0, 0, 0);
    }
}
