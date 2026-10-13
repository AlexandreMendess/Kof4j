package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3o (the AV1 recursive
 * {@code decode_partition} tile walk, {@code libs/image/Av1Partition.kf}).
 *
 * <p>The golden is derived from libaom: an {@code aom_writer} (the real
 * {@code aom_dsp/entenc.c} range encoder with {@code allow_update_cdf}) encodes
 * a chosen partition tree while driving libaom's own partition context
 * ({@code partition_plane_context}/{@code update_ext_partition_context} over
 * {@code partition_context_lookup}) and the verbatim
 * {@code partition_gather_horz_alike}/{@code partition_gather_vert_alike}
 * helpers over {@code default_partition_cdf}. Four frame shapes (with different
 * superblock grids and edge clipping) are encoded; the golden is the sequence
 * of decisions — {@code P r c bSize partition} for a {@code @@partition} read
 * and {@code B r c subSize} for a {@code decode_block} call — in walk order.
 * The dump is gzip-compressed and base64-encoded as
 * {@code av1_partition_golden.txt.b64}.
 */
final class Av1PartitionSupport {

    private Av1PartitionSupport() {}

    static final String RESOURCE = "/av1_partition_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text (stripped), stale-resource guard. */
    static final String GOLDEN_SHA256 =
        "33b0ee9eb3b223011939a791765822dbdef3609776ebe99a2ea5242e8276f19c";

    /** The canonical dump the Kof probe must reproduce. */
    static String golden() {
        try (InputStream raw = Av1PartitionSupport.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') {
                    sb.append(c);
                }
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    /** Kof probe source that walks every encoded tile and dumps the decisions. */
    static String kofProbe() {
        return PROBE;
    }

    /**
     * The expected block grid, derived from the golden's own {@code B r c subSize}
     * records (libaom's {@code decode_block} calls) — the independent reference
     * for {@code av1TileBlockGrid}. Emitted in the same {@code G r c bs} shape the
     * grid probe prints, so the two compare line for line.
     */
    static String expectedGrid() {
        StringBuilder sb = new StringBuilder();
        for (String line : golden().split("\n")) {
            if (!line.startsWith("B ")) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append('G').append(line.substring(1));
        }
        return sb.toString();
    }

    /** Kof probe that prints ONLY the block grid the tile driver consumes. */
    static String kofGridProbe() {
        return GRID_PROBE;
    }

    /**
     * Kof probe for the SHARED-READER seam ({@code Av1Partition.useReader}).
     *
     * <p>A real tile's partition symbols and block payloads share ONE entropy
     * stream (libaom's {@code decode_partition} reads the partition symbol and
     * then calls {@code decode_block} on the same {@code aom_reader}), so a walk
     * that must stay in sync with a block decoder cannot own its reader. This
     * probe builds the {@code Av1Symbol} ONCE and hands it to the walk, then
     * prints the same decisions as {@link #kofProbe()} — the seam must not change
     * a single decision.
     */
    static String kofSharedReaderProbe() {
        return SHARED_PROBE;
    }

    /**
     * Kof probe that PROVES the seam binds: it walks the same bytes twice — once
     * with the walk bound to a caller-owned {@code Av1Symbol}, once with the
     * constructor's own reader — and prints each reader's final bit position. If
     * {@code useReader} really hands the walk the shared reader, both positions
     * are EQUAL and greater than zero; if it silently does nothing, the shared
     * reader is never touched and stays at 0.
     */
    static String kofSeamProbe() {
        return SEAM_PROBE;
    }

    /**
     * The FIRST REAL INTERLEAVED TILE STREAM the walk ever reads.
     *
     * <p>Every harness before this one fed the walk a byte stream carrying ONLY
     * partition symbols. This one is a real tile: libaom's {@code decode_partition}
     * reads the partition symbol and then calls {@code decode_block} on the SAME
     * {@code aom_reader}, so the symbols and the block payloads are interleaved.
     * The stream was produced by the {@code oracle4.c} harness (one 64x64
     * superblock: PARTITION_SPLIT then PARTITION_NONE in each of the four
     * quadrants) and read back by libaom's OWN {@code read_partition} +
     * {@code av1_read_coeffs_txb}, which recovered the intended four
     * {@code BLOCK_32X32}/TX_32X32/DC_PRED blocks and a final entropy context of
     * 17 — the harness is self-consistent.
     *
     * <p>Only the first TWO partition decisions are asserted: the walk has no
     * block decoder to consume the payload between symbols, so it stays in sync
     * exactly until the first {@code decode_block}. That boundary is the whole
     * point of the next slice.
     */
    static final String REAL_TILE_BYTES = "180, 72, 0, 0, 0, 0, 0, 0, 0, 16";

    /**
     * The two decisions the walk reads from the real interleaved stream:
     * PARTITION_SPLIT (3) at BLOCK_64X64 (bSize 12), then PARTITION_NONE (0) at
     * BLOCK_32X32 (bSize 9) — the sizes are the slice-3n BLOCK_SIZES indices, not
     * the mi dimensions.
     */
    static final String REAL_TILE_HEAD =
        "W 0 0 12 3\nW 0 0 9 0";

    /** Kof probe: walk the REAL interleaved tile and print its first decisions. */
    static String kofRealTileProbe() {
        return REAL_TILE_PROBE;
    }

    private static final String REAL_TILE_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    var bytes = listOf(180, 72, 0, 0, 0, 0, 0, 0, 0, 16)
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, 16, 16)
    p.useReader(shared)
    var steps = p.walk()
    var shown = 0
    var i = 0
    while (i < steps.size() && shown < 2) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("W " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
            shown = shown + 1
        }
        i = i + 1
    }
}
""";

    private static final String SEAM_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, w, h)
    p.useReader(shared)
    p.walk()
    var own = Av1Partition(data, w, h)
    own.walk()
    println("R " + shared.position() + " " + own.position())
}
""";

    private static final String SHARED_PROBE = """
import image.Av1Partition
import image.Av1Symbol

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var shared = Av1Symbol(data)
    var p = Av1Partition(data, w, h)
    p.useReader(shared)
    var steps = p.walk()
    var i = 0
    while (i < steps.size()) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("P " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
        } else {
            println("B " + s[1] + " " + s[2] + " " + s[3])
        }
        i = i + 1
    }
}
""";

    private static final String GRID_PROBE = """
import image.Av1Partition

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var p = Av1Partition(data, w, h)
    var grid = av1TileBlockGrid(p.walk())
    var i = 0
    while (i < grid.size()) {
        println("G " + grid.get(i) + " " + grid.get(i + 1) + " " + grid.get(i + 2))
        i = i + 3
    }
}
""";

    private static final String PROBE = """
import image.Av1Partition

main() {
    runCase(6, 5, listOf(236))
    runCase(24, 20, listOf(242, 194, 176))
    runCase(33, 21, listOf(176, 149, 80))
    runCase(16, 40, listOf(239, 12))
}

void runCase(Int w, Int h, List<Int> bytes) {
    var data = new Int[bytes.size()]
    var k = 0
    while (k < bytes.size()) {
        data[k] = bytes.get(k)
        k = k + 1
    }
    var p = Av1Partition(data, w, h)
    var steps = p.walk()
    var i = 0
    while (i < steps.size()) {
        var s = steps.get(i)
        if (s[0] == 0) {
            println("P " + s[1] + " " + s[2] + " " + s[3] + " " + s[4])
        } else {
            println("B " + s[1] + " " + s[2] + " " + s[3])
        }
        i = i + 1
    }
}
""";
}
