package dev.centraleconomy.miner.market;

public record ProcurementQuote(String tier, int itemCount, int emeralds, int remainingLots) {
    public static ProcurementQuote closed() { return new ProcurementQuote("CLOSED", 0, 0, 0); }
    public boolean open() { return !"CLOSED".equals(tier); }
}
