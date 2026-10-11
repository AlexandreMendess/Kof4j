package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3z per-block AV1 reconstruction
 * composition ({@code libs/image/Av1ReconBlock.kf}): dequantization (slice 3f,
 * spec §7.12.2) → inverse transform (slice 3g) → residual + prediction with the
 * FLIPADST flips and {@code Clip1} (slice 3i, §7.11.4 step 3).
 *
 * <p>The golden is produced by the pinned libaom build's real
 * {@code inv_txfm2d_add_facade} ({@code av1/common/av1_inv_txfm2d.c}, the
 * high-bitdepth add path with the FLIPADST index flips ENABLED) fed the SPEC
 * dequantized coefficients, over 27 {@code (txSz, txType, bitDepth)} cases: all
 * 19 transform sizes with {@code DCT_DCT} at bit depth 8 (exercising the
 * dequant denominators), the six 8x8 flip types ({@code flipUD}/{@code flipLR}
 * faces), and {@code DCT_DCT} at bit depths 10 and 12. The dump is
 * gzip+base64 as {@code av1_recon_block_golden.txt.b64}.
 *
 * <p>Layout: libaom's transform reads its input in its own column-major
 * coefficient layout, {@code input[c * txfm_size_row + r]}, so the oracle
 * writes the spec's row-major {@code Dequant[r][c]} raster to
 * {@code in[c * h + r]} before calling {@code inv_txfm2d_add_facade}. Feeding it
 * row-major instead transposes every non-square block — the Kof side is the
 * spec's row-major {@code Dequant}, so the oracle must match it there.
 *
 * <p>Format: one {@code C tx tt bd w h dcq acq} header line per case followed by
 * {@code h} sample rows of {@code w} values. The per-row shape (rather than one
 * flat line) keeps the Kof side's string concatenation bounded — a single
 * 64x64 line builds a 4096-element string one byte at a time and overflows the
 * fixed cross-native arena (16 MiB, the same ceiling the sibling slice-3i probe
 * respects).
 */
final class Av1ReconBlockSupport {

    private Av1ReconBlockSupport() {}

    static final String RESOURCE = "/av1_recon_block_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "f14b972601dadeb05b1dcfdaf6196da810d309eb50494d7e306a2a8302f2a8d1";

    /** One oracle case: {@code C tx tt bd w h dcq acq} + {@code h} rows of {@code w}. */
    record Case(int tx, int tt, int bd, int w, int h, int dcq, int acq) {}

    static String golden() {
        try (InputStream raw = Av1ReconBlockSupport.class.getResourceAsStream(RESOURCE)) {
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

    /** The case header lines, in golden order. */
    private static List<String> headerLines() {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("C ")) out.add(line);
        }
        return out;
    }

    /** The {@code h} sample rows belonging to case {@code ci}. */
    private static List<String> sampleRows(int ci) {
        List<String> all = List.of(golden().split("\n"));
        int seen = -1;
        List<String> rows = new ArrayList<>();
        for (String line : all) {
            if (line.startsWith("C ")) {
                seen++;
                continue;
            }
            if (seen == ci) rows.add(line);
        }
        return rows;
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (String line : headerLines()) {
            String[] f = line.split(" ");
            out.add(new Case(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                    Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                    Integer.parseInt(f[7])));
        }
        return out;
    }

    /**
     * The deterministic QUANTIZED levels the oracle and the probe both use.
     * {@code av1ReconstructBlock} dequantizes them internally (slice 3f), so the
     * probe must pass the raw levels, not the dequantized raster.
     */
    static int[] levels(int ci, int tx, int bd, int dcq, int acq) {
        int w = Av1TxSupport.TXW[tx];
        int h = Av1TxSupport.TXH[tx];
        int tw = Math.min(w, 32);
        int th = Math.min(h, 32);
        int[] in = new int[tw * th];
        for (int i = 0; i < th; i++) {
            for (int j = 0; j < tw; j++) {
                int pos = i * tw + j;
                in[pos] = ((pos * 7 + ci * 13) % 21) - 10;
            }
        }
        return in;
    }

    /** The deterministic prediction ramp the oracle and the probe both use. */
    static int[] pred(int ci, int w, int h, int bd) {
        int[] p = new int[w * h];
        for (int pos = 0; pos < p.length; pos++) {
            p[pos] = (pos * 5 + ci * 3 + 20) % (1 << bd);
        }
        return p;
    }

    static String expectedText() {
        return golden();
    }

    /** The golden text for a subset of cases (the cross-target per-case runs). */
    static String expectedText(List<Integer> indices) {
        List<String> headers = headerLines();
        List<String> out = new ArrayList<>();
        for (int i : indices) {
            out.add(headers.get(i));
            out.addAll(sampleRows(i));
        }
        return String.join("\n", out);
    }

    /**
     * The representative case indices the cross native faces run one process per
     * case (the {@code known-bugs} §602 conservative-GC workaround): a 4x4 and a
     * 64x64 {@code DCT_DCT} (dequant denominators 1 and 4), a 32x32
     * (denominator 2), the two 8x8 flip extremes, and an 8x8 at bit depth 12.
     */
    static List<Integer> crossCaseIndices() {
        List<Case> all = cases();
        List<Integer> out = new ArrayList<>();
        for (int ci = 0; ci < all.size(); ci++) {
            Case c = all.get(ci);
            boolean keep = (c.tx() == 0 && c.tt() == 0 && c.bd() == 8)
                    || (c.tx() == 3 && c.tt() == 0 && c.bd() == 8)
                    || (c.tx() == 4 && c.tt() == 0 && c.bd() == 8)
                    || (c.tx() == 1 && c.tt() == 4 && c.bd() == 8)
                    || (c.tx() == 1 && c.tt() == 6 && c.bd() == 8)
                    || (c.tx() == 1 && c.tt() == 0 && c.bd() == 12);
            if (keep) out.add(ci);
        }
        return out;
    }

    static String kofProbe() {
        List<Integer> all = new ArrayList<>();
        for (int i = 0; i < cases().size(); i++) all.add(i);
        return kofProbe(all);
    }

    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1ReconBlock\n\n");
        List<Case> all = cases();
        for (int ci : indices) {
            Case c = all.get(ci);
            int[] lv = levels(ci, c.tx(), c.bd(), c.dcq(), c.acq());
            int[] pd = pred(ci, c.w(), c.h(), c.bd());
            sb.append("void case").append(ci).append("() {\n");
            sb.append("    var l = new Int[").append(lv.length).append("]\n");
            for (int k = 0; k < lv.length; k++) {
                sb.append("    l[").append(k).append("] = ").append(lv[k]).append('\n');
            }
            sb.append("    var p = new Int[").append(pd.length).append("]\n");
            for (int k = 0; k < pd.length; k++) {
                sb.append("    p[").append(k).append("] = ").append(pd[k]).append('\n');
            }
            sb.append("    var rec = av1ReconstructBlock(l, p, ").append(c.tx())
              .append(", ").append(c.tt()).append(", ").append(c.bd())
              .append(", false, ").append(c.dcq()).append(", ").append(c.acq()).append(")\n");
            sb.append("    println(\"C ").append(c.tx()).append(' ').append(c.tt())
              .append(' ').append(c.bd()).append(' ').append(c.w()).append(' ')
              .append(c.h()).append(' ').append(c.dcq()).append(' ').append(c.acq()).append("\")\n");
            sb.append("    var r = 0\n");
            sb.append("    while (r < ").append(c.h()).append(") {\n");
            sb.append("        var line = \"\"\n");
            sb.append("        var col = 0\n");
            sb.append("        while (col < ").append(c.w()).append(") {\n");
            sb.append("            if (col > 0) {\n");
            sb.append("                line = line + \" \"\n");
            sb.append("            }\n");
            sb.append("            line = line + rec[r * ").append(c.w()).append(" + col]\n");
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
