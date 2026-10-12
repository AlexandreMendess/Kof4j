package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the {@code known-bugs} §679 fix: the AV1 intra
 * tile driver ({@code libs/image/Av1DecodeTileRecon.kf}) must read the
 * {@code tx_type} symbol libaom signals for every non-skipped intra transform
 * block of size &lt;= 16x16.
 *
 * <p>The slice-3ag golden only exercises {@code BLOCK_32X32} -&gt;
 * {@code TX_32X32} -&gt; {@code get_ext_tx_types} = {@code EXT_TX_SET_DCTONLY}
 * = 1, so no {@code tx_type} symbol exists there and the driver's hardcoded
 * {@code txType = 0} agreed with the oracle by accident. libaom's
 * {@code av1_read_tx_type} ({@code av1/decoder/decodemv.c:626}, reached from
 * {@code av1_read_coeffs_txb} at {@code av1/decoder/decodetxb.c:158}) reads the
 * symbol whenever {@code get_ext_tx_types(tx, 0, reduced) > 1}, which for an
 * intra block is true for {@code TX_16X16} ({@code EXT_TX_SET_DTT4_IDTX}, 5
 * symbols) and false for {@code TX_32X32}.
 *
 * <p>The golden is the REAL libaom path: a C encoder writes the mode info, the
 * transform size, the (varying) transform type and the coefficients in the exact
 * order libaom reads them, with libaom's OWN default CDFs and its OWN range
 * encoder; the oracle decodes that stream with libaom's REAL
 * {@code read_intra_frame_mode_info} + {@code read_tx_size} +
 * {@code av1_read_coeffs_txb} + {@code av1_get_tx_type}, dumping each block's
 * decisions, each transform block's DECODED transform type and the nonzero
 * coefficient positions, and the final entropy context. gzip+base64 as
 * {@code av1_decode_tile_txtype_golden.txt.b64}.
 *
 * <p>Format per case: {@code C ci bd lossless txMode nblk mw mh}, one
 * {@code P ci r c bs} per block, {@code T ci bytes...}, one {@code D ci b skip
 * seg txSz mode angleY uvMode angleUV ufi fim} per block, one {@code X ci b gi
 * txType n [pos...]} per transform block, {@code A ci above... | left...}.
 */
final class Av1DecodeTileTxTypeSupport {

    private Av1DecodeTileTxTypeSupport() {}

    static final String RESOURCE = "/av1_decode_tile_txtype_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "42b7c972968e51bb55c465c91f9b5d86728cd735545a23bb909502dd866f733f";

    /** One mode-info block: the {@code P} line. */
    record Blk(int r, int c, int bs) {}

    /** One oracle case: the {@code C} header plus its blocks. */
    record Case(int ci, int bd, int lossless, int txMode, int nblk, int mw, int mh,
                List<Blk> blocks) {}

    static String golden() {
        try (InputStream raw = Av1DecodeTileTxTypeSupport.class.getResourceAsStream(RESOURCE)) {
            if (raw == null) {
                throw new IllegalStateException("missing test resource " + RESOURCE);
            }
            byte[] b64 = raw.readAllBytes();
            StringBuilder sb = new StringBuilder(b64.length);
            for (byte b : b64) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') sb.append(c);
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
            try (GZIPInputStream in = new GZIPInputStream(new java.io.ByteArrayInputStream(gz))) {
                String text = new String(in.readAllBytes(), StandardCharsets.UTF_8).strip();
                String actual = sha256(text);
                if (!actual.equals(GOLDEN_SHA256)) {
                    throw new IllegalStateException(
                            "stale golden " + RESOURCE + ": expected " + GOLDEN_SHA256
                                    + " but hashed " + actual);
                }
                return text;
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("cannot read " + RESOURCE, e);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(d.length * 2);
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        Case cur = null;
        List<Blk> blocks = null;
        for (String line : golden().split("\n")) {
            String[] f = line.split(" ");
            if (f[0].equals("C")) {
                if (cur != null) out.add(finish(cur, blocks));
                cur = new Case(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                        Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                        Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                        Integer.parseInt(f[7]), null);
                blocks = new ArrayList<>();
            } else if (f[0].equals("P")) {
                blocks.add(new Blk(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4])));
            }
        }
        if (cur != null) out.add(finish(cur, blocks));
        return out;
    }

    private static Case finish(Case c, List<Blk> blocks) {
        return new Case(c.ci(), c.bd(), c.lossless(), c.txMode(), c.nblk(),
                c.mw(), c.mh(), List.copyOf(blocks));
    }

    /** The tile bytes of case {@code ci} (the {@code T} line). */
    static int[] bytes(int ci) {
        for (String line : golden().split("\n")) {
            if (line.startsWith("T " + ci + " ")) {
                String[] f = line.split(" ");
                int[] out = new int[f.length - 2];
                for (int i = 2; i < f.length; i++) out[i - 2] = Integer.parseInt(f[i]);
                return out;
            }
        }
        throw new IllegalStateException("no T line for case " + ci);
    }

    /** Case {@code ci}'s expected lines, in the order the probe emits them. */
    private static List<String> expected(int ci) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("D " + ci + " ") || line.startsWith("X " + ci + " ")
                    || line.startsWith("A " + ci + " ")) {
                out.add(line);
            }
        }
        return out;
    }

    static String expectedText() {
        List<String> out = new ArrayList<>();
        for (Case c : cases()) out.addAll(expected(c.ci()));
        return String.join("\n", out);
    }

    static List<Integer> allCaseIndices() {
        List<Integer> out = new ArrayList<>();
        for (Case c : cases()) out.add(c.ci());
        return out;
    }

    /** The transform types the golden records, per case, for the shape assertions. */
    static List<Integer> goldenTxTypes() {
        List<Integer> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            /* X ci gi txType n [pos...] — the tx type is field 3. */
            if (line.startsWith("X ")) out.add(Integer.parseInt(line.split(" ")[3]));
        }
        return out;
    }

    static String kofProbe() {
        return kofProbe(allCaseIndices());
    }

    /**
     * Kof probe source. Each case is a self-contained {@code txTypeTileCase}
     * call, so the cross native faces can run one case per process (the
     * documented {@code known-bugs} §602 workaround).
     */
    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeTileRecon\n");
        sb.append("import image.Av1ModeInfo\n");
        sb.append("import image.Av1Symbol\n");
        sb.append("import image.Av1TxType\n\n");
        sb.append("void txTypeTileCase(Int ci, Int bd, Int lossless, Int txMode, Int miw, ")
          .append("Int mih, Int[] data, List<Int> blocks) {\n");
        sb.append("    var mw = miw << 2\n");
        sb.append("    var mh = mih << 2\n");
        sb.append("    var stride = mw + 2\n");
        sb.append("    var rows = mh + 2\n");
        sb.append("    var frame = new Int[stride * rows]\n");
        sb.append("    var mask = (1 << bd) - 1\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < frame.size) {\n");
        sb.append("        frame[i] = (i * 37 + 11) & mask\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    var sym = Av1Symbol(data)\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var txStore = Av1TxType(sym)\n");
        sb.append("    var txTypes = new Int[(mw >> 2) * (mh >> 2)]\n");
        sb.append("    var above = new Int[miw]\n");
        sb.append("    var left = new Int[mih]\n");
        sb.append("    var levels = listOf<Int[]>()\n");
        sb.append("    var txTypeOut = listOf<Int>()\n");
        sb.append("    var modes = listOf<Int>()\n");
        sb.append("    var cfg = Av1ModeTail(12, 0, 0, 0, 0, 0, 0, 0, 0, 3)\n");
        sb.append("    var out = av1DecodeTileRecon(frame, stride, mw, mh, blocks, cfg, ")
          .append("lossless, 1, 0, 0, bd, txMode, 80, 0, 0, 0, 0, 74, 87, 1, 0, 0, ")
          .append("sym, store, txStore, txTypes, above, left, levels, txTypeOut, modes)\n");
        sb.append("    var nb = blocks.size / 3\n");
        sb.append("    var b = 0\n");
        sb.append("    while (b < nb) {\n");
        sb.append("        var o = b * 17\n");
        sb.append("        println(\"D \" + ci + \" \" + b + \" \" + modes[o] + \" \" ")
          .append("+ modes[o + 1] + \" \" + modes[o + 4] + \" \" + modes[o + 5] + \" \" ")
          .append("+ modes[o + 6] + \" \" + modes[o + 7] + \" \" + modes[o + 8] + \" \" ")
          .append("+ modes[o + 11] + \" \" + modes[o + 12])\n");
        sb.append("        b = b + 1\n");
        sb.append("    }\n");
        /* One X line per transform block: the DECODED transform type and the
         * nonzero coefficient positions, exactly the golden's shape. */
        sb.append("    var k = 0\n");
        sb.append("    while (k < levels.size) {\n");
        sb.append("        var lv = levels[k]\n");
        sb.append("        var line = \"X \" + ci + \" \" + k + \" \" + txTypeOut[k] + \" \" + lv.size\n");
        sb.append("        var p = 0\n");
        sb.append("        var pos = \"\"\n");
        sb.append("        while (p < lv.size) {\n");
        sb.append("            if (lv[p] != 0) {\n");
        sb.append("                pos = pos + \" \" + p\n");
        sb.append("            }\n");
        sb.append("            p = p + 1\n");
        sb.append("        }\n");
        sb.append("        println(line + pos)\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    var a = \"A \" + ci\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < above.size) {\n");
        sb.append("        a = a + \" \" + above[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    a = a + \" |\"\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < left.size) {\n");
        sb.append("        a = a + \" \" + left[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    println(a)\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        for (Case c : cases()) {
            if (!indices.contains(c.ci())) continue;
            int[] b = bytes(c.ci());
            sb.append("    var b").append(c.ci()).append(" = new Int[")
              .append(b.length).append("]\n");
            for (int k = 0; k < b.length; k++) {
                sb.append("    b").append(c.ci()).append('[').append(k).append("] = ")
                  .append(b[k]).append('\n');
            }
            sb.append("    var blk").append(c.ci()).append(" = listOf<Int>(");
            boolean first = true;
            for (Blk blk : c.blocks()) {
                for (int v : new int[]{blk.r(), blk.c(), blk.bs()}) {
                    if (!first) sb.append(", ");
                    sb.append(v);
                    first = false;
                }
            }
            sb.append(")\n");
            sb.append("    txTypeTileCase(").append(c.ci()).append(", ").append(c.bd())
              .append(", ").append(c.lossless()).append(", ").append(c.txMode())
              .append(", ").append(c.mw()).append(", ").append(c.mh())
              .append(", b").append(c.ci()).append(", blk").append(c.ci()).append(")\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
