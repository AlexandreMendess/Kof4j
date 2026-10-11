package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Golden reader for the slice-3z+ AV1 intra block availability
 * ({@code av1HasTopRight}/{@code av1HasBottomLeft} in
 * {@code libs/image/Av1Avail.kf}). The golden is the pinned libaom
 * {@code has_top_right}/{@code has_bottom_left} ({@code av1/common/reconintra.c})
 * driven over 1203 deterministic {@code (sbSize, bsize, miRow, miCol, flags,
 * partition, txSz, rowOff, colOff, ssX, ssY)} cases, dumped as one
 * {@code R tr bl} line per case.
 */
final class Av1AvailSupport {

    private static final String RESOURCE = "/av1_avail_golden.txt.b64";
    private static final String GOLDEN_SHA256 =
            "59802ca39fac2b996ef909c5b96759e59fa60c0429a16ae97b25f2932dd3eb4e";

    private Av1AvailSupport() {
    }

    record Case(int sb, int bs, int mr, int mc, int ta, int ra, int ba, int la,
                int pt, int tx, int ro, int co, int sx, int sy) {
    }

    static String golden() {
        try {
            String b64 = new String(Av1AvailSupport.class.getResourceAsStream(RESOURCE)
                    .readAllBytes(), StandardCharsets.UTF_8);
            byte[] gz = Base64.getDecoder().decode(b64);
            try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
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

    /** The {@code C}-line cases (one per golden case, in order). */
    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (!line.startsWith("C ")) continue;
            String[] f = line.split(" ");
            out.add(new Case(Integer.parseInt(f[1]), Integer.parseInt(f[2]),
                    Integer.parseInt(f[3]), Integer.parseInt(f[4]),
                    Integer.parseInt(f[5]), Integer.parseInt(f[6]),
                    Integer.parseInt(f[7]), Integer.parseInt(f[8]),
                    Integer.parseInt(f[9]), Integer.parseInt(f[10]),
                    Integer.parseInt(f[11]), Integer.parseInt(f[12]),
                    Integer.parseInt(f[13]), Integer.parseInt(f[14])));
        }
        return out;
    }

    /** The golden {@code R tr bl} lines — the expected probe output. */
    static String expectedText() {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("R ")) out.add(line);
        }
        return String.join("\n", out);
    }

    /**
     * A single Kof program that embeds every case as a flat data table and
     * loops it, printing {@code R tr bl} per case. The probe is small (no frame,
     * a ~36 KB data string), so it runs all cases in one process on every
     * target — the {@code known-bugs} §602 per-case split is not needed here.
     */
    static String kofProbe() {
        StringBuilder data = new StringBuilder();
        int n = 0;
        for (Case c : cases()) {
            if (n > 0) data.append(',');
            data.append(c.sb()).append(',').append(c.bs()).append(',').append(c.mr())
                .append(',').append(c.mc()).append(',').append(c.ta()).append(',').append(c.ra())
                .append(',').append(c.ba()).append(',').append(c.la()).append(',').append(c.pt())
                .append(',').append(c.tx()).append(',').append(c.ro()).append(',').append(c.co())
                .append(',').append(c.sx()).append(',').append(c.sy());
            n++;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1Avail\n\n");
        sb.append("main() {\n");
        sb.append("    var d = av1AvailParse(\"").append(data).append("\")\n");
        sb.append("    var n = ").append(n).append("\n");
        sb.append("    var i = 0\n");
        sb.append("    while (i < n) {\n");
        sb.append("        var b = i * 14\n");
        sb.append("        var tr = av1HasTopRight(d[b], d[b + 1], d[b + 2], d[b + 3],")
          .append(" d[b + 4], d[b + 5], d[b + 8], d[b + 9], d[b + 10], d[b + 11],")
          .append(" d[b + 12], d[b + 13])\n");
        sb.append("        var bl = av1HasBottomLeft(d[b], d[b + 1], d[b + 2], d[b + 3],")
          .append(" d[b + 6], d[b + 7], d[b + 8], d[b + 9], d[b + 10], d[b + 11],")
          .append(" d[b + 12], d[b + 13])\n");
        sb.append("        println(\"R \" + tr + \" \" + bl)\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
