package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the AVIF chroma transform-size slice (image-vision
 * front, plan §34): {@code av1PlaneBlockSize} + {@code av1GetMaxUvTxSize} in
 * {@code libs/image/Av1IntraAvail.kf}.
 *
 * <p>libaom's {@code av1_get_max_uv_txsize(bsize, subsampling_x, subsampling_y)}
 * ({@code av1/common/blockd.h}) is what {@code decode_reconstruct_tx}
 * ({@code av1/decoder/decodeframe.c}) uses for {@code plane > 0}: it takes the
 * plane's block size through {@code get_plane_block_size}
 * ({@code av1_ss_size_lookup}), looks up its rectangular max transform and
 * applies {@code av1_get_adjusted_tx_size}. Chroma is NOT subdivided per block —
 * the whole chroma plane of a mode-info block is one transform.
 *
 * <p>{@code get_plane_block_size} is NOT {@code scale_chroma_bsize}
 * ({@code reconintra.c}, already in this library as {@code av1ScaleChromaBsize}):
 * the former SCALES DOWN for the plane's extent (8x8 -&gt; 4x4 under 4:2:0), the
 * latter REMAPS the smallest blocks UP for the intra predictor (4x4 -&gt; 8x8
 * under 4:2:0). Conflating them is the mistake this slice exists to prevent.
 *
 * <p>The golden is every VALID {@code (bsize, ssX, ssY)} triple dumped from
 * libaom's OWN tables (a C program that calls {@code av1_get_max_uv_txsize}),
 * one {@code bs ssX ssY txSize} row per line. The impossible combinations
 * ({@code get_plane_block_size} -&gt; {@code BLOCK_INVALID}, where libaom's
 * {@code assert} would fire) are excluded and refused by name in Kof instead.
 * gzip+base64 as {@code av1_uv_tx_size_golden.txt.b64}.
 */
final class Av1UvTxSizeSupport {

    private Av1UvTxSizeSupport() {}

    static final String RESOURCE = "/av1_uv_tx_size_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "06ef47bb821159c7e2bb7c4dabe61de5b003e17f3746876977b8735b8ccd0461";

    /** One oracle row: the block size, the subsampling flags and the tx size. */
    record Row(int bs, int ssX, int ssY, int txSize) {}

    static String golden() {
        try (InputStream raw = Av1UvTxSizeSupport.class.getResourceAsStream(RESOURCE)) {
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

    static List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            String[] f = line.split(" ");
            out.add(new Row(Integer.parseInt(f[0]), Integer.parseInt(f[1]),
                    Integer.parseInt(f[2]), Integer.parseInt(f[3])));
        }
        return out;
    }

    /** The subset the oracle marks valid — every row in the golden, by construction. */
    static int validCount() {
        return rows().size();
    }

    /**
     * Kof probe source: re-emits every golden row from the Kof primitive, in the
     * oracle's own order, so a byte-for-byte compare covers the whole table.
     */
    static String kofProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1IntraAvail\n\n");
        sb.append("main() {\n");
        for (Row r : rows()) {
            sb.append("    println(").append(r.bs()).append(" + \" \" + ")
              .append(r.ssX()).append(" + \" \" + ").append(r.ssY())
              .append(" + \" \" + av1GetMaxUvTxSize(").append(r.bs()).append(", ")
              .append(r.ssX()).append(", ").append(r.ssY()).append("))\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Kof probe for the refusal face: the impossible combinations must raise the
     * named diagnostic rather than index past the tables.
     */
    static String refusalProbe() {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1IntraAvail\n\n");
        sb.append("main() {\n");
        sb.append("    var refused = 0\n");
        for (int bs = 0; bs < 22; bs++) {
            for (int sx = 0; sx < 2; sx++) {
                for (int sy = 0; sy < 2; sy++) {
                    if (isValid(bs, sx, sy)) continue;
                    sb.append("    try {\n");
                    sb.append("        av1GetMaxUvTxSize(").append(bs).append(", ")
                      .append(sx).append(", ").append(sy).append(")\n");
                    sb.append("        println(\"NO-REFUSAL\")\n");
                    sb.append("    } catch (String e) {\n");
                    sb.append("        refused = refused + 1\n");
                    sb.append("    }\n");
                }
            }
        }
        sb.append("    println(\"refused \" + refused)\n");
        sb.append("}\n");
        return sb.toString();
    }

    /** The combinations libaom's tables mark valid (the golden's own row set). */
    private static boolean isValid(int bs, int sx, int sy) {
        for (Row r : rows()) {
            if (r.bs() == bs && r.ssX() == sx && r.ssY() == sy) return true;
        }
        return false;
    }
}
