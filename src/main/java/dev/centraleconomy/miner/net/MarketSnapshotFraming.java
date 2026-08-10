package dev.centraleconomy.miner.net;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Splits large market snapshots into small UTF-8-safe string payloads and reassembles them client-side.
 *
 * The Minecraft/Fabric payload still carries one String, but no individual packet contains the full
 * librarian/cleric catalog. Keeping chunks at 4096 UTF-16 code units leaves a wide safety margin under
 * vanilla's normal String packet limits even when the chunk is mostly 3-byte Korean text.
 */
public final class MarketSnapshotFraming {
    public static final String PREFIX = "CE2";
    public static final int MAX_CHUNK_CHARS = 4096;
    public static final int MAX_TOTAL_CHARS = 1_000_000;
    public static final int MAX_FRAME_UTF8_BYTES = 16_384;
    public static final int MAX_PARTS = (MAX_TOTAL_CHARS + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS;
    private static final int MAX_PENDING_TRANSFERS = 8;
    private static final long STALE_NANOS = 30_000_000_000L;

    private MarketSnapshotFraming() {}

    public record Frame(String transferId, int index, int count, String chunk) {}
    public record Complete(String transferId, int partCount, int totalChars, String json) {}

    public static List<String> frame(String json) {
        if (json == null) throw new IllegalArgumentException("snapshot json is null");
        if (json.length() > MAX_TOTAL_CHARS) {
            throw new IllegalArgumentException("snapshot too large: " + json.length() + " chars");
        }

        int count = Math.max(1, (json.length() + MAX_CHUNK_CHARS - 1) / MAX_CHUNK_CHARS);
        if (count > MAX_PARTS) throw new IllegalArgumentException("snapshot requires too many parts: " + count);

        String transferId = UUID.randomUUID().toString();
        List<String> frames = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int start = i * MAX_CHUNK_CHARS;
            int end = Math.min(json.length(), start + MAX_CHUNK_CHARS);
            String chunk = json.substring(start, end);
            String wire = PREFIX + ":" + transferId + ":" + i + ":" + count + ":" + chunk;
            if (wire.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_UTF8_BYTES) {
                throw new IllegalArgumentException("snapshot frame exceeds UTF-8 safety bound");
            }
            frames.add(wire);
        }
        return List.copyOf(frames);
    }

    public static Frame parse(String wire) {
        if (wire == null) throw new IllegalArgumentException("snapshot frame is null");
        String[] fields = wire.split(":", 5);
        if (fields.length != 5 || !PREFIX.equals(fields[0])) throw new IllegalArgumentException("invalid snapshot frame header");
        String transferId = fields[1];
        if (transferId.isBlank() || transferId.length() > 64) throw new IllegalArgumentException("invalid snapshot transfer id");

        final int index;
        final int count;
        try {
            index = Integer.parseInt(fields[2]);
            count = Integer.parseInt(fields[3]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid snapshot part numbers", e);
        }
        if (count < 1 || count > MAX_PARTS || index < 0 || index >= count) {
            throw new IllegalArgumentException("snapshot part outside bounds");
        }
        if (fields[4].length() > MAX_CHUNK_CHARS) throw new IllegalArgumentException("snapshot chunk too large");
        if (wire.getBytes(StandardCharsets.UTF_8).length > MAX_FRAME_UTF8_BYTES) throw new IllegalArgumentException("snapshot frame UTF-8 size too large");
        return new Frame(transferId, index, count, fields[4]);
    }

    /** Stateful client-side reassembler. Thread-safe because networking callbacks need not be assumed to be UI-thread callbacks. */
    public static final class Assembler {
        private final Map<String, Pending> pending = new HashMap<>();

        public synchronized Complete accept(String wire) {
            long now = System.nanoTime();
            cleanup(now);
            Frame frame = parse(wire);
            Pending assembly = pending.get(frame.transferId());
            if (assembly == null) {
                if (pending.size() >= MAX_PENDING_TRANSFERS) evictOldest();
                assembly = new Pending(frame.count(), now);
                pending.put(frame.transferId(), assembly);
            } else if (assembly.parts.length != frame.count()) {
                pending.remove(frame.transferId());
                throw new IllegalArgumentException("snapshot part-count mismatch");
            }

            String existing = assembly.parts[frame.index()];
            if (existing != null) {
                if (!existing.equals(frame.chunk())) {
                    pending.remove(frame.transferId());
                    throw new IllegalArgumentException("conflicting duplicate snapshot part");
                }
                return null;
            }

            assembly.parts[frame.index()] = frame.chunk();
            assembly.received++;
            assembly.totalChars += frame.chunk().length();
            if (assembly.totalChars > MAX_TOTAL_CHARS) {
                pending.remove(frame.transferId());
                throw new IllegalArgumentException("assembled snapshot exceeds limit");
            }
            if (assembly.received != assembly.parts.length) return null;

            StringBuilder json = new StringBuilder(assembly.totalChars);
            for (String part : assembly.parts) {
                if (part == null) return null;
                json.append(part);
            }
            pending.remove(frame.transferId());
            return new Complete(frame.transferId(), assembly.parts.length, assembly.totalChars, json.toString());
        }

        public synchronized int pendingTransfers() { return pending.size(); }

        private void cleanup(long now) {
            pending.entrySet().removeIf(e -> now - e.getValue().createdAtNanos > STALE_NANOS);
        }

        private void evictOldest() {
            String oldestId = null;
            long oldest = Long.MAX_VALUE;
            for (var entry : pending.entrySet()) {
                if (entry.getValue().createdAtNanos < oldest) {
                    oldest = entry.getValue().createdAtNanos;
                    oldestId = entry.getKey();
                }
            }
            if (oldestId != null) pending.remove(oldestId);
        }

        private static final class Pending {
            private final String[] parts;
            private final long createdAtNanos;
            private int received;
            private int totalChars;

            private Pending(int count, long createdAtNanos) {
                this.parts = new String[count];
                this.createdAtNanos = createdAtNanos;
            }
        }
    }
}
