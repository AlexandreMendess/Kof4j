package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3ae AV1 heterogeneous intra plane
 * driver ({@code libs/image/Av1DecodeTilePlane.kf}).
 *
 * <p>The Kof driver decodes a plane made of SEVERAL mode-info blocks, each with
 * its own block size, transform size, transform type and intra mode, in tile
 * order over ONE shared entropy stream — the composition libaom's
 * {@code decode_partition} -> {@code decode_block} ->
 * {@code decode_token_recon_block} runs for the blocks of a tile.
 *
 * <p>The golden is the REAL libaom path: the tile bytes are produced by the
 * faithful Java port of {@code aom_dsp/entenc.c} (the slice-3e/3x/3y/3ad
 * precedent), and the C oracle decodes them with libaom's OWN
 * {@code av1_read_coeffs_txb} (with {@code allow_update_cdf = 1}), predicts with
 * the REAL {@code av1/common/reconintra.c} builders and adds the residual with
 * the REAL {@code av1_highbd_inv_txfm_add_c} ({@code av1/common/idct.c}). The
 * dump is gzip+base64 as {@code av1_decode_tile_plane_golden.txt.b64}.
 *
 * <p>Format per case: {@code C tag bd mw mh dcq acq frameW frameH}, one
 * {@code P tag r c bs tx tt mode} per mode-info block, {@code T tag bytes...},
 * one {@code D tag bi levels...} per transform block (the decoded signed
 * coefficient raster, in enumeration order), {@code A tag above... | left...}
 * (the final packed entropy-context store) and {@code frameH} rows.
 */
final class Av1DecodeTilePlaneSupport {

    private Av1DecodeTilePlaneSupport() {}

    static final String RESOURCE = "/av1_decode_tile_plane_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "5d78218b1fea67f88ad715d767f5b518feb46533b1312c3d8d02487e7920f5e0";

    /** One mode-info block of a case: {@code P} line. */
    record Blk(int r, int c, int bs, int tx, int tt, int mode) {}

    /** One oracle case: the header fields of a {@code C} line plus its blocks. */
    record Case(String tag, int bd, int mw, int mh, int dcq, int acq,
                int frameW, int frameH, List<Blk> blocks) {}

    static String golden() {
        try (InputStream raw = Av1DecodeTilePlaneSupport.class.getResourceAsStream(RESOURCE)) {
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
                cur = new Case(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                        Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                        Integer.parseInt(f[8]), null);
                blocks = new ArrayList<>();
            } else if (f[0].equals("P")) {
                blocks.add(new Blk(Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                        Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                        Integer.parseInt(f[6]), Integer.parseInt(f[7])));
            }
        }
        if (cur != null) out.add(finish(cur, blocks));
        return out;
    }

    private static Case finish(Case c, List<Blk> blocks) {
        return new Case(c.tag(), c.bd(), c.mw(), c.mh(), c.dcq(), c.acq(),
                c.frameW(), c.frameH(), List.copyOf(blocks));
    }

    private static List<String> lines(String prefix) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith(prefix)) out.add(line);
        }
        return out;
    }

    /** The tile bytes of case {@code ci} (the {@code T} line). */
    static int[] bytes(int ci) {
        String line = lines("T ").get(ci);
        String[] f = line.split(" ");
        int[] out = new int[f.length - 2];
        for (int i = 2; i < f.length; i++) out[i - 2] = Integer.parseInt(f[i]);
        return out;
    }

    /** Case {@code ci}'s expected lines: the {@code C}/{@code D}/{@code A} lines and the plane rows. */
    private static List<String> expected(int ci) {
        List<String> out = new ArrayList<>();
        List<String> all = List.of(golden().split("\n"));
        int seen = -1;
        for (String line : all) {
            if (line.startsWith("C ")) {
                seen++;
                if (seen == ci) out.add(line);
                continue;
            }
            if (seen != ci) continue;
            if (line.startsWith("D ") || line.startsWith("A ")) {
                out.add(line);
            } else if (line.startsWith("R ")) {
                /* "R tag r v0 v1 ..." -> "v0 v1 ..." (what the Kof probe prints). */
                String[] f = line.split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < f.length; i++) {
                    if (i > 3) sb.append(' ');
                    sb.append(f[i]);
                }
                out.add(sb.toString());
            }
        }
        return out;
    }

    static String expectedText() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < cases().size(); i++) all.add(i);
        return expectedText(all);
    }

    static String expectedText(List<Integer> indices) {
        List<String> out = new ArrayList<>();
        for (int ci : indices) out.addAll(expected(ci));
        return String.join("\n", out);
    }

    static List<Integer> allCaseIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cases().size(); i++) out.add(i);
        return out;
    }

    static String kofProbe() {
        return kofProbe(allCaseIndices());
    }

    /**
     * Kof probe source. The cross native targets run one case per process: the
     * conservative cross GC ({@code known-bugs} §602) reclaims a live receiver
     * once a single binary decodes a whole plane, so a case per process is the
     * documented workaround (the sibling slices split the same way). Output is
     * byte-for-byte identical.
     */
    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeTilePlane\n");
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void tilePlaneCase(String tag, Int bd, Int mw, Int mh, ")
          .append("Int dcq, Int acq, Int fw, Int fh, List<Int> blks, ")
          .append("Int[] bytes, Int[] above, Int[] left) {\n");
        sb.append("    var stride = fw + 2\n");
        sb.append("    var rows = fh + 2\n");
        sb.append("    var frame = new Int[stride * rows]\n");
        sb.append("    var mask = (1 << bd) - 1\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < frame.size) {\n");
        sb.append("        frame[i] = (i * 37 + 11) & mask\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("    var sym = Av1Symbol(bytes)\n");
        sb.append("    var store = Av1CoeffCdfStore(80)\n");
        sb.append("    var levels = listOf<Int[]>()\n");
        sb.append("    var out = av1DecodeTilePlaneRecon(frame, stride, fw, fh, ")
          .append("blks, bd, 1, 0, dcq, acq, sym, store, above, left, levels)\n");
        sb.append("    var s = \"C \" + tag + \" \" + bd + \" \" + mw + \" \" + mh + \" \" ")
          .append("+ dcq + \" \" + acq + \" \" + fw + \" \" + fh\n");
        sb.append("    println(s)\n");
        sb.append("    var k = 0\n");
        sb.append("    while (k < levels.size) {\n");
        sb.append("        s = \"D \" + tag + \" \" + k\n");
        sb.append("        var j = 0\n");
        sb.append("        while (j < levels[k].size) {\n");
        sb.append("            s = s + \" \" + levels[k][j]\n");
        sb.append("            j = j + 1\n");
        sb.append("        }\n");
        sb.append("        println(s)\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    s = \"A \" + tag\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < above.size) {\n");
        sb.append("        s = s + \" \" + above[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    s = s + \" |\"\n");
        sb.append("    k = 0\n");
        sb.append("    while (k < left.size) {\n");
        sb.append("        s = s + \" \" + left[k]\n");
        sb.append("        k = k + 1\n");
        sb.append("    }\n");
        sb.append("    println(s)\n");
        sb.append("    var r = 0\n");
        sb.append("    while (r < fh) {\n");
        sb.append("        var line = \"\"\n");
        sb.append("        var col = 0\n");
        sb.append("        while (col < fw) {\n");
        sb.append("            if (col > 0) {\n");
        sb.append("                line = line + \" \"\n");
        sb.append("            }\n");
        sb.append("            line = line + out[r * fw + col]\n");
        sb.append("            col = col + 1\n");
        sb.append("        }\n");
        sb.append("        println(line)\n");
        sb.append("        r = r + 1\n");
        sb.append("    }\n");
        sb.append("}\n\n");
        List<Case> all = cases();
        for (int ci : indices) {
            Case c = all.get(ci);
            int[] b = bytes(ci);
            sb.append("void case").append(ci).append("() {\n");
            sb.append("    var b = new Int[").append(b.length).append("]\n");
            for (int k = 0; k < b.length; k++) {
                sb.append("    b[").append(k).append("] = ").append(b[k]).append('\n');
            }
            sb.append("    var blks = listOf<Int>(");
            boolean first = true;
            for (Blk blk : c.blocks()) {
                for (int v : new int[]{blk.r(), blk.c(), blk.bs(), blk.tx(), blk.tt(), blk.mode()}) {
                    if (!first) sb.append(", ");
                    sb.append(v);
                    first = false;
                }
            }
            sb.append(")\n");
            sb.append("    var a = new Int[").append(c.mw()).append("]\n");
            sb.append("    var l = new Int[").append(c.mh()).append("]\n");
            sb.append("    tilePlaneCase(\"").append(c.tag()).append("\", ").append(c.bd())
              .append(", ").append(c.mw()).append(", ").append(c.mh())
              .append(", ").append(c.dcq()).append(", ").append(c.acq())
              .append(", ").append(c.frameW()).append(", ").append(c.frameH())
              .append(", blks, b, a, l)\n");
            sb.append("}\n\n");
        }
        sb.append("main() {\n");
        for (int ci : indices) {
            sb.append("    case").append(ci).append("()\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
