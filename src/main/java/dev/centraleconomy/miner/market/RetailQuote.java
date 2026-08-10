package dev.centraleconomy.miner.market;

public record RetailQuote(boolean available, String reason, int itemCount, int emeralds, int stockItems) {
    public static RetailQuote unavailable(String reason) { return new RetailQuote(false, reason, 0, 0, 0); }
}
