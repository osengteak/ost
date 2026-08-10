package dev.centraleconomy.miner.net;

/** Pure, testable representation of one client market action. */
public record MinerTradeRequest(int entityId, Direction direction, String commodityId) {
    public enum Direction { BUY, SELL }

    public MinerTradeRequest {
        if (entityId < 0) throw new IllegalArgumentException("entityId must be non-negative");
        if (direction == null) throw new IllegalArgumentException("direction required");
        if (commodityId == null || commodityId.isBlank()) throw new IllegalArgumentException("commodityId required");
        if (commodityId.indexOf('|') >= 0) throw new IllegalArgumentException("invalid commodityId");
    }

    public static MinerTradeRequest parse(String encoded) {
        if (encoded == null) throw new IllegalArgumentException("request missing");
        String[] parts = encoded.split("\\|", 3);
        if (parts.length != 3) throw new IllegalArgumentException("wrong field count");
        int entityId = Integer.parseInt(parts[0]);
        Direction direction = Direction.valueOf(parts[1]);
        return new MinerTradeRequest(entityId, direction, parts[2]);
    }

    public String encode() {
        return entityId + "|" + direction.name() + "|" + commodityId;
    }
}
