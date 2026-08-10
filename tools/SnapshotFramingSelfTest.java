import dev.centraleconomy.miner.net.MarketSnapshotFraming;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SnapshotFramingSelfTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String small = "{\"market\":\"miner\"}";
        List<String> smallFrames = MarketSnapshotFraming.frame(small);
        check(smallFrames.size() == 1, "small snapshot stays one frame");
        MarketSnapshotFraming.Complete smallComplete = new MarketSnapshotFraming.Assembler().accept(smallFrames.get(0));
        check(smallComplete != null && smallComplete.json().equals(small), "single-frame snapshot reassembles exactly");

        StringBuilder large = new StringBuilder(220_000);
        large.append("{\"rows\":[");
        for (int i = 0; i < 7000; i++) {
            if (i > 0) large.append(',');
            large.append("\"성직자포션-").append(i).append("-효과\"");
        }
        large.append("]}");
        String json = large.toString();
        List<String> frames = MarketSnapshotFraming.frame(json);
        check(frames.size() > 1, "large catalog is split across multiple frames");
        for (String wire : frames) {
            check(wire.getBytes(StandardCharsets.UTF_8).length <= MarketSnapshotFraming.MAX_FRAME_UTF8_BYTES,
                    "each UTF-8 frame remains far below normal vanilla String packet limit");
            MarketSnapshotFraming.Frame parsed = MarketSnapshotFraming.parse(wire);
            check(parsed.chunk().length() <= MarketSnapshotFraming.MAX_CHUNK_CHARS, "chunk respects framing bound");
        }

        List<String> reversed = new ArrayList<>(frames);
        Collections.reverse(reversed);
        MarketSnapshotFraming.Assembler assembler = new MarketSnapshotFraming.Assembler();
        MarketSnapshotFraming.Complete complete = null;
        for (String frame : reversed) {
            MarketSnapshotFraming.Complete result = assembler.accept(frame);
            if (result != null) complete = result;
        }
        check(complete != null, "out-of-order frames eventually complete");
        check(complete.json().equals(json), "large snapshot reassembles byte-for-byte at String level");
        check(assembler.pendingTransfers() == 0, "completed transfer is removed from pending state");

        boolean malformedRejected = false;
        try { MarketSnapshotFraming.parse("not-a-frame"); }
        catch (IllegalArgumentException e) { malformedRejected = true; }
        check(malformedRejected, "malformed frame is rejected");

        MarketSnapshotFraming.Frame first = MarketSnapshotFraming.parse(frames.get(0));
        MarketSnapshotFraming.Assembler duplicateAssembler = new MarketSnapshotFraming.Assembler();
        duplicateAssembler.accept(frames.get(0));
        boolean conflictRejected = false;
        try {
            duplicateAssembler.accept(MarketSnapshotFraming.PREFIX + ":" + first.transferId() + ":" + first.index() + ":" + first.count() + ":DIFFERENT");
        } catch (IllegalArgumentException e) {
            conflictRejected = true;
        }
        check(conflictRejected, "conflicting duplicate frame is rejected");

        boolean oversizedRejected = false;
        try { MarketSnapshotFraming.frame("x".repeat(MarketSnapshotFraming.MAX_TOTAL_CHARS + 1)); }
        catch (IllegalArgumentException e) { oversizedRejected = true; }
        check(oversizedRejected, "oversized total snapshot is rejected before networking");

        System.out.println("PASS: chunked market snapshot framing invariants");
    }
}
