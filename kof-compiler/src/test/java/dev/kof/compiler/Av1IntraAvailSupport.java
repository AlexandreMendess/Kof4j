package dev.kof.compiler;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3ab AV1 intra prediction availability
 * derivation ({@code av1IntraAvail}/{@code av1ScaleChromaBsize} in
 * {@code libs/image/Av1IntraAvail.kf}). The golden is the availability block of
 * the pinned libaom {@code av1_predict_intra_block}
 * ({@code av1/common/reconintra.c}) — the {@code extend_modes} mode gating, the
 * filter-intra override, the directional {@code pAngle} override,
 * {@code scale_chroma_bsize} and the {@code has_top_right}/{@code has_bottom_left}
 * calls — driven over 2500 deterministic cases, dumped as one
 * {@code C ...} / {@code R tr bl ntop nleft} pair per case.
 */
final class Av1IntraAvailSupport {

    private static final String RESOURCE = "/av1_intra_avail_golden.txt.b64";
    private static final String GOLDEN_SHA256 =
            "3c106491559b49a0b0fc13c97cc236a35545d3b752f46a62362acc0e92b76b07";

    private Av1IntraAvailSupport() {
    }

    record Case(int sb, int bs, int pt, int tx, int sx, int sy, int ro, int co,
                int mode, int ad, int ufi, int ht, int hl, int mr, int mc,
                int ce, int re, int fw, int fh) {
    }

    static String golden() {
        try {
            byte[] raw = Av1IntraAvailSupport.class.getResourceAsStream(RESOURCE).readAllBytes();
            StringBuilder sb = new StringBuilder(raw.length);
            for (byte b : raw) {
                char c = (char) (b & 0xFF);
                if (c != '\n' && c != '\r' && c != ' ' && c != '\t') sb.append(c);
            }
            byte[] gz = Base64.getDecoder().decode(sb.toString());
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
                    Integer.parseInt(f[13]), Integer.parseInt(f[14]),
                    Integer.parseInt(f[15]), Integer.parseInt(f[16]),
                    Integer.parseInt(f[17]), Integer.parseInt(f[18]),
                    Integer.parseInt(f[19])));
        }
        return out;
    }

    /** The golden {@code R tr bl ntop nleft} lines — the expected probe output. */
    static List<String> expectedLines() {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("R ")) out.add(line);
        }
        return out;
    }

    static String expectedText() {
        return String.join("\n", expectedLines());
    }

    /** The expected output for the case range {@code [from, to)}. */
    static String expectedText(int from, int to) {
        return String.join("\n", expectedLines().subList(from, to));
    }

    /**
     * Case count per cross-native process. The full 2500-case probe is one
     * {@code main()} that allocates well past the {@code known-bugs} §602
     * alloc-tick-4096 collect; on riscv64/aarch64 that collect sweeps a still-live
     * caller-frame array, so the run dies with {@code array index out of bounds}
     * mid-table (x86-64/JVM/Script/JS are unaffected). Batches stay comfortably
     * under the tick — the same per-process split the sibling
     * {@code Av1DecodeBlockE2ETest} uses per grid.
     */
    static final int CROSS_BATCH = 400;

    /**
     * A single Kof program that embeds every case as a flat data table and loops
     * it, printing {@code R tr bl ntop nleft} per case. The probe has no frame,
     * so JVM/Script/x86-64/JS run all cases in one process; the cross targets are
     * batched by {@link #CROSS_BATCH} (see that field). The flat table exceeds
     * the JVM constant-pool string limit, so it is split into comma-aligned
     * chunks reassembled into one {@code Int[]}.
     */
    static String kofProbe() {
        return kofProbe(0, cases().size());
    }

    /** The probe over the case range {@code [from, to)}. */
    static String kofProbe(int from, int to) {
        List<Case> subset = cases().subList(from, to);
        StringBuilder data = new StringBuilder();
        int n = 0;
        for (Case c : subset) {
            if (n > 0) data.append(',');
            data.append(c.sb()).append(',').append(c.bs()).append(',').append(c.pt())
                .append(',').append(c.tx()).append(',').append(c.sx()).append(',').append(c.sy())
                .append(',').append(c.ro()).append(',').append(c.co()).append(',').append(c.mode())
                .append(',').append(c.ad()).append(',').append(c.ufi()).append(',').append(c.ht())
                .append(',').append(c.hl()).append(',').append(c.mr()).append(',').append(c.mc())
                .append(',').append(c.ce()).append(',').append(c.re()).append(',').append(c.fw())
                .append(',').append(c.fh());
            n++;
        }
        // Split the flat table into comma-aligned chunks under the JVM's
        // 65535-byte string-literal limit.
        List<String> chunks = new ArrayList<>();
        int start = 0;
        final int maxChunk = 40000;
        while (start < data.length()) {
            int end = Math.min(start + maxChunk, data.length());
            if (end < data.length()) {
                int comma = data.lastIndexOf(",", end);
                if (comma > start) end = comma;
            }
            chunks.add(data.substring(start, end));
            start = (end < data.length()) ? end + 1 : end;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1IntraAvail\n");
        sb.append("import image.Av1Avail\n\n");
        sb.append("main() {\n");
        sb.append("    var n = ").append(n).append("\n");
        sb.append("    var d = new Int[").append(n * 19).append("]\n");
        sb.append("    var off = 0\n");
        for (int ci = 0; ci < chunks.size(); ci++) {
            sb.append("    var c").append(ci).append(" = av1AvailParse(\"")
              .append(chunks.get(ci)).append("\")\n");
        }
        for (int ci = 0; ci < chunks.size(); ci++) {
            sb.append("    var k").append(ci).append(" = 0\n");
            sb.append("    while (k").append(ci).append(" < c").append(ci).append(".size) {\n");
            sb.append("        d[off] = c").append(ci).append("[k").append(ci).append("]\n");
            sb.append("        off = off + 1\n");
            sb.append("        k").append(ci).append(" = k").append(ci).append(" + 1\n");
            sb.append("    }\n");
        }
        sb.append("    var i = 0\n");
        sb.append("    while (i < n) {\n");
        sb.append("        var b = i * 19\n");
        sb.append("        var r = av1IntraAvail(d[b], d[b + 1], d[b + 2], d[b + 3],")
          .append(" d[b + 4], d[b + 5], d[b + 6], d[b + 7], d[b + 8], d[b + 9],")
          .append(" d[b + 10], d[b + 11], d[b + 12], d[b + 13], d[b + 14],")
          .append(" d[b + 15], d[b + 16], d[b + 17], d[b + 18])\n");
        sb.append("        println(\"R \" + r[0] + \" \" + r[1] + \" \" + r[2] + \" \" + r[3])\n");
        sb.append("        i = i + 1\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }
}
