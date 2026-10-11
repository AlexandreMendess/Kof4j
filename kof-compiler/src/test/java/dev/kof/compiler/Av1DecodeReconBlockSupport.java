package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3ac AV1 intra predict+reconstruct
 * transform-block driver ({@code libs/image/Av1DecodeReconBlock.kf}).
 *
 * <p>The Kof driver reproduces the intra loop of libaom's
 * {@code decode_token_recon_block} ({@code av1/decoder/decodeframe.c:908}): for
 * every transform block of a plane, in the slice-3y enumeration order, it
 * derives the block's availability (slice 3ab), predicts it (slice 3u), then
 * dequantizes + inverse-transforms + reconstructs it into the plane (slice 3z) —
 * so a later block predicts from the samples the earlier ones already
 * reconstructed.
 *
 * <p>The golden is the REAL libaom path: {@code build_non_directional_intra_predictors}
 * / {@code build_directional_and_filter_intra_predictors} (from
 * {@code av1/common/reconintra.c}, the same builders {@code av1_predict_intra_block}
 * dispatches) driven over the enumeration, with the spec §7.12.2 dequantization
 * feeding the REAL {@code av1_highbd_inv_txfm_add_c} ({@code av1/common/idct.c})
 * into the plane. Over 12 {@code (txSz, mode, angleDelta, filterIntra, txType,
 * blockSize)} cases it pins the whole reconstructed plane. The dump is
 * gzip+base64 as {@code av1_decode_recon_block_golden.txt.b64}.
 *
 * <p>Format: one {@code C tag bd tx mode ad ufi fim tt bs mw mh dcq acq frameW
 * frameH} header per case followed by {@code frameH} {@code R tag r v...} rows.
 * The Kof probe emits the same header and the same rows (without the {@code R
 * tag r} prefix).
 */
final class Av1DecodeReconBlockSupport {

    private Av1DecodeReconBlockSupport() {}

    static final String RESOURCE = "/av1_decode_recon_block_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "8be02528d74c96c2d369e7da43329616e5dc37c7251c4df78e85443d30f08853";

    /** One oracle case: the header fields of a {@code C} line. */
    record Case(String tag, int bd, int tx, int mode, int ad, int ufi, int fim,
                int tt, int bs, int mw, int mh, int dcq, int acq,
                int frameW, int frameH) {}

    static String golden() {
        try (InputStream raw = Av1DecodeReconBlockSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** The {@code C} header lines, in golden order. */
    private static List<String> headerLines() {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("C ")) out.add(line);
        }
        return out;
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (String line : headerLines()) {
            String[] f = line.split(" ");
            out.add(new Case(f[1], Integer.parseInt(f[2]), Integer.parseInt(f[3]),
                    Integer.parseInt(f[4]), Integer.parseInt(f[5]),
                    Integer.parseInt(f[6]), Integer.parseInt(f[7]),
                    Integer.parseInt(f[8]), Integer.parseInt(f[9]),
                    Integer.parseInt(f[10]), Integer.parseInt(f[11]),
                    Integer.parseInt(f[12]), Integer.parseInt(f[13]),
                    Integer.parseInt(f[14]), Integer.parseInt(f[15])));
        }
        return out;
    }

    /** The reconstructed-plane rows belonging to case {@code ci} (the {@code R} lines). */
    private static List<String> sampleRows(int ci) {
        List<String> all = List.of(golden().split("\n"));
        int seen = -1;
        List<String> rows = new ArrayList<>();
        for (String line : all) {
            if (line.startsWith("C ")) {
                seen++;
                continue;
            }
            if (seen == ci && line.startsWith("R ")) {
                /* "R tag r v0 v1 ..." -> "v0 v1 ..." (what the Kof probe prints). */
                String[] f = line.split(" ");
                StringBuilder sb = new StringBuilder();
                for (int i = 3; i < f.length; i++) {
                    if (i > 3) sb.append(' ');
                    sb.append(f[i]);
                }
                rows.add(sb.toString());
            }
        }
        return rows;
    }

    static String expectedText() {
        List<String> out = new ArrayList<>();
        for (int ci = 0; ci < cases().size(); ci++) {
            out.add(headerLines().get(ci));
            out.addAll(sampleRows(ci));
        }
        return String.join("\n", out);
    }

    /** The golden text for a subset of cases (the cross-target per-case runs). */
    static String expectedText(List<Integer> indices) {
        List<String> out = new ArrayList<>();
        for (int ci : indices) {
            out.add(headerLines().get(ci));
            out.addAll(sampleRows(ci));
        }
        return String.join("\n", out);
    }

    /**
     * The case indices the cross native faces run one process per case. This is
     * the shape the sibling slices use as the {@code known-bugs} §602
     * conservative-GC workaround; here it does NOT actually work around the
     * reclaim (one 16-transform-block plane decode already crosses the
     * 4096-alloc collect tick), so the cross faces are guarded by a named §602
     * assumption in {@code Av1DecodeReconBlockE2ETest} and skip with the reason.
     */
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

    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeReconBlock\n\n");
        List<Case> all = cases();
        for (int ci : indices) {
            Case c = all.get(ci);
            int stride = c.frameW() + 2;
            int rows = c.frameH() + 2;
            int mask = (1 << c.bd()) - 1;
            sb.append("void case").append(ci).append("() {\n");
            sb.append("    var stride = ").append(stride).append('\n');
            sb.append("    var frame = new Int[").append(stride * rows).append("]\n");
            sb.append("    var i = 0\n");
            sb.append("    while (i < frame.size) {\n");
            sb.append("        frame[i] = (i * 37 + 11) & ").append(mask).append('\n');
            sb.append("        i = i + 1\n");
            sb.append("    }\n");
            sb.append("    var out = av1DecodeReconBlockPlane(frame, stride, ")
              .append(c.frameW()).append(", ").append(c.frameH()).append(", ")
              .append(c.bs()).append(", ").append(c.tx()).append(", ")
              .append(c.tt()).append(", ").append(c.mode()).append(", ")
              .append(c.ad()).append(", ").append(c.ufi()).append(", ")
              .append(c.fim()).append(", ").append(c.bd())
              .append(", 1, 0, ").append(c.mw()).append(", ").append(c.mh())
              .append(", ").append(c.dcq()).append(", ").append(c.acq()).append(")\n");
            sb.append("    println(\"C ").append(c.tag()).append(' ').append(c.bd())
              .append(' ').append(c.tx()).append(' ').append(c.mode())
              .append(' ').append(c.ad()).append(' ').append(c.ufi())
              .append(' ').append(c.fim()).append(' ').append(c.tt())
              .append(' ').append(c.bs()).append(' ').append(c.mw())
              .append(' ').append(c.mh()).append(' ').append(c.dcq())
              .append(' ').append(c.acq()).append(' ').append(c.frameW())
              .append(' ').append(c.frameH()).append("\")\n");
            sb.append("    var r = 0\n");
            sb.append("    while (r < ").append(c.frameH()).append(") {\n");
            sb.append("        var line = \"\"\n");
            sb.append("        var col = 0\n");
            sb.append("        while (col < ").append(c.frameW()).append(") {\n");
            sb.append("            if (col > 0) {\n");
            sb.append("                line = line + \" \"\n");
            sb.append("            }\n");
            sb.append("            line = line + out[r * ").append(c.frameW()).append(" + col]\n");
            sb.append("            col = col + 1\n");
            sb.append("        }\n");
            sb.append("        println(line)\n");
            sb.append("        r = r + 1\n");
            sb.append("    }\n");
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
