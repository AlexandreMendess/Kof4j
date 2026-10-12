package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for AVIF slice 3ai (image-vision front, plan §34): the
 * AV1 CHROMA plane composition ({@code av1DecodeChromaBlock} in
 * {@code libs/image/Av1DecodeChroma.kf}, driven from
 * {@code libs/image/Av1DecodeTileRecon.kf} when {@code numPlanes > 1}).
 *
 * <p>libaom's {@code decode_token_recon_block} ({@code av1/decoder/decodeframe.c})
 * runs {@code plane = 0} then U then V for the SAME mode-info block, each with
 * {@code tx_size = av1_get_tx_size(plane, xd)} = {@code av1_get_max_uv_txsize}
 * for {@code plane > 0} (ONE transform size for the whole chroma plane), and each
 * with its own {@code above_entropy_context[plane]} / {@code left_entropy_context
 * [plane]} arrays. The chroma transform type is DERIVED from the UV mode — libaom
 * transmits {@code tx_type} for the luma plane only — so the chroma path must
 * read NO type symbol.
 *
 * <p>The golden comes from the instrumented chroma oracle (a hand-written encoder
 * plus libaom's own decoder, both printing every symbol; the oracle is
 * self-checked: encoder and decoder agree symbol for symbol). Its records are
 * {@code C}/{@code P}/{@code T}/{@code D}/{@code X}/{@code U}/{@code A}:
 *
 * <ul>
 *   <li>{@code C ci bd lossless txmode nb mw mh} — the case, {@code mw}/{@code mh}
 *       in LUMA mi units (4x4).
 *   <li>{@code P ci r c bs} — one mode-info block.
 *   <li>{@code T ci <bytes...>} — the tile payload.
 *   <li>{@code D ci b skip segId tx yMode angleY uvMode angleUV useFilterIntra
 *       filterIntraMode} — the decoded mode info.
 *   <li>{@code U ci pl giC tt tw*th <q...>} — one chroma transform block: the
 *       plane, the index (CONTINUOUS across U then V), the derived transform type,
 *       the coefficient count and the RASTER indices of the non-zero
 *       coefficients.
 *   <li>{@code A ci <above...> | <left...>} — the LUMA entropy contexts.
 * </ul>
 *
 * <p>The case is 4:2:0 ({@code ssX = ssY = 1}) and, crucially, the fourth case
 * holds TWO mode-info blocks side by side, so the second block's chroma reads the
 * contexts the first wrote — the case that exposed the defect this slice's oracle
 * had (the {@code known-bugs} §682 finding).
 */
final class Av1DecodeChromaSupport {

    private Av1DecodeChromaSupport() {}

    static final String RESOURCE = "/av1_decode_chroma_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "af1ead88419fe16cde4cbe6a03e931590f12021e585a4b91bb3e77b11e016cc4";

    /** One case: {@code mw}/{@code mh} are the LUMA mi (4x4) extents. */
    record Case(int ci, int bd, int lossless, int txMode, int nblk, int mw, int mh) {}

    /** One mode-info block: LUMA 4x4 origin and block size index. */
    record Blk(int r, int c, int bs) {}

    static String golden() {
        try (InputStream raw = Av1DecodeChromaSupport.class.getResourceAsStream(RESOURCE)) {
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
        for (String line : golden().split("\n")) {
            if (!line.startsWith("C ")) continue;
            String[] f = line.split(" ");
            out.add(new Case(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                    Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                    Integer.parseInt(f[7])));
        }
        return out;
    }

    static List<Blk> blocks(int ci) {
        List<Blk> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("P " + ci + " ")) {
                String[] f = line.split(" ");
                out.add(new Blk(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4])));
            }
        }
        return out;
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

    /** Case {@code ci}'s expected lines: {@code D}, {@code U} and {@code A}. */
    private static List<String> expected(int ci) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("D " + ci + " ") || line.startsWith("A " + ci + " ")
                    || line.startsWith("U " + ci + " ")) {
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

    /**
     * Kof probe source. Each case is a self-contained {@code chromaTileCase} call,
     * so the cross native faces can run one case per process (the documented
     * {@code known-bugs} §602 workaround). Output is byte-for-byte identical.
     */
    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeTileRecon\n");
        sb.append("import image.Av1DecodeChroma\n");
        sb.append("import image.Av1ModeInfo\n");
        sb.append("import image.Av1Symbol\n");
        sb.append("import image.Av1TxType\n");
        sb.append("import image.Av1IntraAvail\n");
        sb.append("import image.Av1Tx\n");
        sb.append("import image.Av1Block\n\n");
        sb.append("void chromaTileCase(Int ci, Int bd, Int lossless, Int txMode, ")
          .append("Int miw, Int mih, Int[] data, List<Int> blocks) {\n");
        sb.append("void emitU(Int ci, Int pl, Int gi, List<Int[]> lv, List<Int> tv, ")
          .append("Int idx) {\n");
        sb.append("    var arr = lv.get(idx)\n");
        sb.append("    var tt = tv.get(idx)\n");
        sb.append("    var s = \"U \" + ci + \" \" + pl + \" \" + gi + \" \" + tt ")
          .append("+ \" \" + arr.size\n");
        sb.append("    var q = 0\n");
        sb.append("    while (q < arr.size) {\n");
        sb.append("        if (arr[q] != 0) {\n");
        sb.append("            s = s + \" \" + q\n");
        sb.append("        }\n");
        sb.append("        q = q + 1\n");
        sb.append("    }\n");
        sb.append("    println(s)\n");
        sb.append("}\n\n");

        sb.append("    var mw = miw << 2\n");
        sb.append("    var mh = mih << 2\n");
        sb.append("    var mask = (1 << bd) - 1\n");
        /* LUMA plane, one-sample border. */
        sb.append("    var stride = mw + 2\n");
        sb.append("    var frame = new Int[stride * (mh + 2)]\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < frame.size) {\n");
        sb.append("        frame[i] = (i * 37 + 11) & mask\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        /* CHROMA planes (4:2:0), each with its own border and contexts. */
        sb.append("    var cw = mw >> 1\n");
        sb.append("    var ch = mh >> 1\n");
        sb.append("    var cstride = cw + 2\n");
        sb.append("    var crows = ch + 2\n");
        sb.append("    var frameU = new Int[cstride * crows]\n");
        sb.append("    var frameV = new Int[cstride * crows]\n");
        sb.append("    i = 0\n");
        sb.append("    while (i < frameU.size) {\n");
        sb.append("        frameU[i] = (i * 29 + 7) & mask\n");
        sb.append("        frameV[i] = (i * 53 + 3) & mask\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    var aboveU = new Int[miw]\n");
        sb.append("    var leftU = new Int[mih]\n");
        sb.append("    var aboveV = new Int[miw]\n");
        sb.append("    var leftV = new Int[mih]\n");
        sb.append("    var chroma = Av1ChromaPlanes(frameU, cstride, cw, ch, ")
          .append("aboveU, leftU, frameV, cstride, cw, ch, aboveV, leftV)\n");
        sb.append("    var sym = Av1Symbol(data)\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var txStore = Av1TxType(sym)\n");
        sb.append("    var txTypes = new Int[(mw >> 2) * (mh >> 2)]\n");
        sb.append("    var above = new Int[miw]\n");
        sb.append("    var left = new Int[mih]\n");
        sb.append("    var levels = listOf<Int[]>()\n");
        sb.append("    var txTypeOut = listOf<Int>()\n");
        sb.append("    var modes = listOf<Int>()\n");
        sb.append("    var levelsU = listOf<Int[]>()\n");
        sb.append("    var levelsV = listOf<Int[]>()\n");
        sb.append("    var txU = listOf<Int>()\n");
        sb.append("    var txV = listOf<Int>()\n");
        sb.append("    var cfg = Av1ModeTail(12, 0, 0, 0, 0, 0, 0, 0, 0, 3)\n");
        sb.append("    var out = av1DecodeTileRecon(frame, stride, mw, mh, blocks, cfg, ")
          .append("lossless, 1, 3, 0, 0, bd, txMode, 80, 0, 0, 0, 0, 74, 87, 1, 0, 0, ")
          .append("sym, store, txStore, txTypes, above, left, levels, txTypeOut, modes, ")
          .append("chroma, 1, 1, levelsU, levelsV, txU, txV)\n");
        /* D: the decoded mode info, one line per mode-info block. */
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
        /* U: one line per chroma transform block, in libaom's BLOCK-major order
         * (for each mode-info block: U then V), with the index continuous across
         * the whole case. The per-plane block count of a mode-info block is its
         * chroma plane extent divided by its chroma transform size (slice 3ah). */
        sb.append("    var gi = 0\n");
        sb.append("    var ui = 0\n");
        sb.append("    var vi = 0\n");
        sb.append("    var bb = 0\n");
        sb.append("    while (bb < nb) {\n");
        sb.append("        var bbs = blocks.get(bb * 3 + 2)\n");
        sb.append("        var uvt = av1GetMaxUvTxSize(bbs, 1, 1)\n");
        sb.append("        var uvb = av1PlaneBlockSize(bbs, 1, 1)\n");
        sb.append("        var cnt = (av1BlockNum4x4Wide(uvb) / (av1TxWidth(uvt) >> 2)) * ")
          .append("(av1BlockNum4x4High(uvb) / (av1TxHeight(uvt) >> 2))\n");
        sb.append("        var pl = 1\n");
        sb.append("        while (pl <= 2) {\n");
        sb.append("            var k = 0\n");
        sb.append("            while (k < cnt) {\n");
        sb.append("                if (pl == 1) {\n");
        sb.append("                    emitU(ci, 1, gi, levelsU, txU, ui)\n");
        sb.append("                    ui = ui + 1\n");
        sb.append("                } else {\n");
        sb.append("                    emitU(ci, 2, gi, levelsV, txV, vi)\n");
        sb.append("                    vi = vi + 1\n");
        sb.append("                }\n");
        sb.append("                gi = gi + 1\n");
        sb.append("                k = k + 1\n");
        sb.append("            }\n");
        sb.append("            pl = pl + 1\n");
        sb.append("        }\n");
        sb.append("        bb = bb + 1\n");
        sb.append("    }\n");
        /* A: the LUMA entropy contexts. */
        sb.append("    var a = \"A \" + ci\n");
        sb.append("    var j = 0\n");
        sb.append("    while (j < above.size) {\n");
        sb.append("        a = a + \" \" + above[j]\n");
        sb.append("        j = j + 1\n");
        sb.append("    }\n");
        sb.append("    a = a + \" |\"\n");
        sb.append("    j = 0\n");
        sb.append("    while (j < left.size) {\n");
        sb.append("        a = a + \" \" + left[j]\n");
        sb.append("        j = j + 1\n");
        sb.append("    }\n");
        sb.append("    println(a)\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        for (Case c : cases()) {
            if (!indices.contains(c.ci())) continue;
            int[] bytes = bytes(c.ci());
            sb.append("    var b").append(c.ci()).append(" = new Int[")
              .append(bytes.length).append("]\n");
            for (int k = 0; k < bytes.length; k++) {
                sb.append("    b").append(c.ci()).append('[').append(k).append("] = ")
                  .append(bytes[k]).append('\n');
            }
            sb.append("    var blk").append(c.ci()).append(" = listOf<Int>(");
            boolean first = true;
            for (Blk blk : blocks(c.ci())) {
                for (int v : new int[]{blk.r(), blk.c(), blk.bs()}) {
                    if (!first) sb.append(", ");
                    sb.append(v);
                    first = false;
                }
            }
            sb.append(")\n");
            sb.append("    chromaTileCase(").append(c.ci()).append(", ").append(c.bd())
              .append(", ").append(c.lossless()).append(", ").append(c.txMode())
              .append(", ").append(c.mw()).append(", ").append(c.mh())
              .append(", b").append(c.ci()).append(", blk").append(c.ci()).append(")\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    static String kofProbe() {
        return kofProbe(allCaseIndices());
    }
}
