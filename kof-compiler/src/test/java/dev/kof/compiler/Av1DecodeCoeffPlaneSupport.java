package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3ad AV1 coefficient-driven intra
 * predict+reconstruct plane driver ({@code libs/image/Av1DecodeCoeffPlane.kf}).
 *
 * <p>The Kof driver is the intra loop of libaom's
 * {@code decode_token_recon_block} ({@code av1/decoder/decodeframe.c:908}) with
 * the coefficient values coming from the REAL tile entropy stream: per transform
 * block, in the slice-3y enumeration order, it decodes the block's coefficients
 * (slices 3e/3x — {@code av1_read_coeffs_txb}: {@code get_txb_ctx} neighbour
 * context, the symbol walk, {@code av1_set_entropy_contexts}), derives its
 * availability (slice 3ab), predicts it from the already reconstructed
 * neighbours (slice 3u), then dequantizes + inverse-transforms + reconstructs it
 * into the plane (slice 3z) with the DECODED levels.
 *
 * <p>The golden is the REAL libaom path: the tile bytes are produced by the
 * faithful Java port of {@code aom_dsp/entenc.c} (the slice-3e/3x/3y precedent),
 * and the C oracle decodes them with libaom's OWN {@code av1_read_coeffs_txb}
 * (with {@code allow_update_cdf = 1}, the CDF adaptation the writer performs),
 * predicts with the REAL {@code av1/common/reconintra.c} builders and adds the
 * residual with the REAL {@code av1_highbd_inv_txfm_add_c}
 * ({@code av1/common/idct.c}). The dump is gzip+base64 as
 * {@code av1_decode_coeff_plane_golden.txt.b64}.
 *
 * <p>Format per case: {@code C tag bd tx mode ad bs mw mh dcq acq frameW
 * frameH}, {@code T tag bytes...}, one {@code D tag bi levels...} per transform
 * block (the decoded signed coefficient raster, in enumeration order), {@code A
 * tag above... | left...} (the final packed entropy-context store) and
 * {@code frameH} {@code R tag r v...} rows. The Kof probe emits the same lines
 * minus the {@code T} header (the bytes are its input) and with the {@code R tag
 * r} prefix stripped.
 */
final class Av1DecodeCoeffPlaneSupport {

    private Av1DecodeCoeffPlaneSupport() {}

    static final String RESOURCE = "/av1_decode_coeff_plane_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "89104a7cab81bfa9bde2f4567d4213e260e3fb1a75a464e2a29c37a343d129a9";

    /** One oracle case: the header fields of a {@code C} line. */
    record Case(String tag, int bd, int tx, int mode, int ad, int bs, int mw,
                int mh, int dcq, int acq, int frameW, int frameH) {}

    /** The per-block intent the Java od_ec encoder writes. */
    private static final int[] CULS = {3, 0, 5, 1, 2, 7, 4, 6};

    static String golden() {
        try (InputStream raw = Av1DecodeCoeffPlaneSupport.class.getResourceAsStream(RESOURCE)) {
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

    private static List<String> lines(String prefix) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith(prefix)) out.add(line);
        }
        return out;
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (String line : lines("C ")) {
            String[] f = line.split(" ");
            out.add(new Case(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                    Integer.parseInt(f[8]), Integer.parseInt(f[9]),
                    Integer.parseInt(f[10]), Integer.parseInt(f[11]),
                    Integer.parseInt(f[12])));
        }
        return out;
    }

    /** The tile bytes of case {@code ci} (the {@code T} line), as the Kof probe inputs them. */
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

    /** The golden text for a subset of cases (the cross-target per-case runs). */
    static String expectedText(List<Integer> indices) {
        List<String> out = new ArrayList<>();
        for (int ci : indices) out.addAll(expected(ci));
        return String.join("\n", out);
    }

    /** The case indices the cross native faces run one process per case. */
    static List<Integer> crossCaseIndices() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < cases().size(); i++) out.add(i);
        return out;
    }

    static String kofProbe() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < cases().size(); i++) all.add(i);
        return kofProbe(all);
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
        sb.append("import image.Av1DecodeCoeffPlane\n");
        sb.append("import image.Av1Coeffs\n");
        sb.append("import image.Av1Symbol\n\n");
        sb.append("void coeffPlaneCase(String tag, Int bd, Int tx, Int mode, Int ad, Int bs, ")
          .append("Int mw, Int mh, Int dcq, Int acq, Int fw, Int fh, ")
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
        sb.append("    var out = av1DecodeCoeffPlaneRecon(frame, stride, fw, fh, bs, tx, ")
          .append("0, mode, ad, 0, 0, bd, 1, 0, mw, mh, dcq, acq, sym, store, bs, 0, ")
          .append("0, 0, above, left, levels)\n");
        sb.append("    var s = \"C \" + tag + \" \" + bd + \" \" + tx + \" \" + mode + \" \" ")
          .append("+ ad + \" \" + bs + \" \" + mw + \" \" + mh + \" \" + dcq + \" \" + acq + \" \" ")
          .append("+ fw + \" \" + fh\n");
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
            int twu = Av1TxSupport.TXW[c.tx()] >> 2;
            int thu = Av1TxSupport.TXH[c.tx()] >> 2;
            int[] b = bytes(ci);
            sb.append("void case").append(ci).append("() {\n");
            sb.append("    var b = new Int[").append(b.length).append("]\n");
            for (int k = 0; k < b.length; k++) {
                sb.append("    b[").append(k).append("] = ").append(b[k]).append('\n');
            }
            sb.append("    var a = new Int[").append(c.mw() * twu).append("]\n");
            sb.append("    var l = new Int[").append(c.mh() * thu).append("]\n");
            sb.append("    coeffPlaneCase(\"").append(c.tag()).append("\", ").append(c.bd())
              .append(", ").append(c.tx()).append(", ").append(c.mode())
              .append(", ").append(c.ad()).append(", ").append(c.bs())
              .append(", ").append(c.mw()).append(", ").append(c.mh())
              .append(", ").append(c.dcq()).append(", ").append(c.acq())
              .append(", ").append(c.frameW()).append(", ").append(c.frameH())
              .append(", b, a, l)\n");
            sb.append("}\n\n");
        }
        sb.append("main() {\n");
        for (int ci : indices) {
            sb.append("    case").append(ci).append("()\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Regenerates the tile bytes of case {@code ci} with the faithful Java od_ec
     * encoder over the slice-3y enumeration — the same code the golden's
     * {@code T} line came from, so a drift in either the encoder or the
     * enumeration is caught by {@code tileBytesMatchJavaEncoder}.
     */
    static int[] javaEncodedBytes(int ci) {
        Case c = cases().get(ci);
        int twu = Av1TxSupport.TXW[c.tx()] >> 2;
        int thu = Av1TxSupport.TXH[c.tx()] >> 2;
        int[] above = new int[c.mw() * twu];
        int[] left = new int[c.mh() * thu];
        Av1CoeffsSupport.Store store = new Av1CoeffsSupport.Store(80);
        Av1CoeffPlaneEnc enc = new Av1CoeffPlaneEnc();
        List<int[]> blocks = enumerate(c.mw(), c.mh(), c.tx());
        for (int i = 0; i < blocks.size(); i++) {
            int br = blocks.get(i)[0];
            int bc = blocks.get(i)[1];
            int[] ctx = Av1CoeffPlaneSupport.getTxbCtx(
                    Av1CoeffPlaneSupport.slice(above, bc, twu),
                    Av1CoeffPlaneSupport.slice(left, br, thu),
                    twu, thu, c.bs(), c.tx(), 0);
            int cul = CULS[i % CULS.length];
            int dcCat = i % 3;
            Av1CoeffPlaneSupport.Block blk = Av1CoeffPlaneSupport.blockLevels(cul, dcCat, c.tx());
            Av1CoeffPlaneSupport.encodeBlock(enc, store, c.tx(), 0, ctx[1], ctx[0],
                    blk.levels(), blk.eob());
            int packed = Av1CoeffPlaneSupport.av1TxbEntropyContext(cul, dcCat);
            Av1CoeffPlaneEdgeSupport.storeAbove(above, bc, twu, packed, c.mw());
            Av1CoeffPlaneEdgeSupport.storeLeft(left, br, thu, packed, c.mh());
        }
        return enc.done();
    }

    /** The slice-3y enumeration: superblock-raster order, then row-major inside. */
    private static List<int[]> enumerate(int mw, int mh, int tx) {
        int muW = Math.min(mw, 16);
        int muH = Math.min(mh, 16);
        int stepc = Av1TxSupport.TXW[tx] >> 2;
        int stepr = Av1TxSupport.TXH[tx] >> 2;
        List<int[]> out = new ArrayList<>();
        for (int row = 0; row < mh; row += muH) {
            for (int col = 0; col < mw; col += muW) {
                int unitH = Math.min(muH + row, mh);
                int unitW = Math.min(muW + col, mw);
                for (int br = row; br < unitH; br += stepr) {
                    for (int bc = col; bc < unitW; bc += stepc) {
                        out.add(new int[]{br, bc});
                    }
                }
            }
        }
        return out;
    }
}
