package dev.centraleconomy.miner.market;

public final class GateEvaluator {
    private GateEvaluator() {}

    public static boolean isOpen(String gate, MarketMutableState state) {
        return switch (gate == null ? "none" : gate) {
            case "none" -> true;
            case "market_warehouse" -> state.infrastructureFlags().contains("market_warehouse");
            case "regional_trade_route_or_50e_turnover" ->
                    state.infrastructureFlags().contains("regional_trade_route")
                            || state.cumulativeTurnoverEmeralds() >= 50;
            case "mineral_warehouse_and_150e_turnover" ->
                    state.infrastructureFlags().contains("mineral_warehouse")
                            && state.cumulativeTurnoverEmeralds() >= 150;
            case "library_and_150e_turnover" ->
                    state.infrastructureFlags().contains("public_library")
                            && state.cumulativeTurnoverEmeralds() >= 150;
            default -> false;
        };
    }
}
