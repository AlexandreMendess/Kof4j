package dev.kof.compiler;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Independent data source for the slice-3af AV1 intra MODE-INFO + TX-SIZE block
 * walk ({@code libs/image/Av1DecodeTile.kf}).
 *
 * <p>The Kof driver composes the landed slices 3p/3q/3r/3s into the walk
 * libaom runs as {@code decode_mbmi_block} + {@code read_tx_size} for every
 * block of a tile: the prefix (segment id + skip), the tail (CDEF + delta-Q +
 * delta-LF), the body (Y/UV mode, angles, CFL, palette, filter-intra) and the
 * transform size, per block, over ONE shared entropy stream.
 *
 * <p>The golden is the REAL libaom path: a C encoder writes the symbols in the
 * exact order {@code read_intra_frame_mode_info} reads them, with libaom's OWN
 * default CDFs ({@code av1_init_mode_probs}) and its OWN range encoder
 * ({@code aom_dsp/entenc.c}), and the oracle then decodes that stream with
 * libaom's REAL {@code read_intra_frame_mode_info} + {@code read_tx_size},
 * dumping every decision. The dump is gzip+base64 as
 * {@code av1_decode_tile_golden.txt.b64}.
 *
 * <p>Format per case: {@code C ci bd sbSize enableCdef cdefBits codedLossless
 * deltaQPresent deltaQRes deltaLfPresent deltaLfMulti deltaLfRes lossless
 * enableFilterIntra txMode nblk}, one {@code P ci r c bs} per block, {@code T
 * ci bytes...} and one {@code D ci b skip seg cdef qIndex txSz mode angleY
 * uvMode angleUV useFilterIntra filterIntraMode} per block.
 */
final class Av1DecodeTileSupport {

    private Av1DecodeTileSupport() {}

    static final String RESOURCE = "/av1_decode_tile_golden.txt.b64";

    /** Expected SHA-256 of the decoded golden text, to catch a stale resource. */
    static final String GOLDEN_SHA256 =
        "abb5d8e9e78b75f39329d44e75d83a9b017f2e66b1272def7cfcb17e838264ff";

    /** One mode-info block: {@code P} line. */
    record Blk(int r, int c, int bs) {}

    /** One oracle case: the {@code C} header plus its blocks. */
    record Case(int ci, int bd, int sbSize, int enableCdef, int cdefBits,
                int codedLossless, int deltaQPresent, int deltaQRes,
                int deltaLfPresent, int deltaLfMulti, int deltaLfRes,
                int lossless, int enableFilterIntra, int txMode, List<Blk> blocks) {}

    static String golden() {
        try (InputStream raw = Av1DecodeTileSupport.class.getResourceAsStream(RESOURCE)) {
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
                        Integer.parseInt(f[7]), Integer.parseInt(f[8]),
                        Integer.parseInt(f[9]), Integer.parseInt(f[10]),
                        Integer.parseInt(f[11]), Integer.parseInt(f[12]),
                        Integer.parseInt(f[13]), Integer.parseInt(f[14]), null);
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
        return new Case(c.ci(), c.bd(), c.sbSize(), c.enableCdef(), c.cdefBits(),
                c.codedLossless(), c.deltaQPresent(), c.deltaQRes(),
                c.deltaLfPresent(), c.deltaLfMulti(), c.deltaLfRes(),
                c.lossless(), c.enableFilterIntra(), c.txMode(), List.copyOf(blocks));
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

    /** Case {@code ci}'s expected {@code D} lines, in block order. */
    private static List<String> expected(int ci) {
        List<String> out = new ArrayList<>();
        for (String line : golden().split("\n")) {
            if (line.startsWith("D " + ci + " ")) out.add(line);
        }
        return out;
    }

    static String expectedText() {
        List<String> out = new ArrayList<>();
        for (Case c : cases()) out.addAll(expected(c.ci()));
        return String.join("\n", out);
    }

    static String kofProbe() {
        return kofProbe(allCaseIndices());
    }

    static List<Integer> allCaseIndices() {
        List<Integer> out = new ArrayList<>();
        for (Case c : cases()) out.add(c.ci());
        return out;
    }

    /**
     * Kof probe source. Each case is a self-contained {@code runTile} call, so
     * the cross native faces can run one case per process (the documented
     * {@code known-bugs} §602 workaround). Output is byte-for-byte identical.
     */
    static String kofProbe(List<Integer> indices) {
        StringBuilder sb = new StringBuilder();
        sb.append("import image.Av1DecodeTile\n");
        sb.append("import image.Av1ModeInfo\n\n");
        sb.append("void runTile(Int ci, Int bd, Int sb, Int enCdef, Int cdefBits, ")
          .append("Int losslessFrame, Int dqP, Int dqR, Int dlfP, Int dlfMulti, ")
          .append("Int dlfR, Int lossless, Int enFi, Int txMode, Int mw, Int mh, ")
          .append("Int[] data, List<Int> blocks) {\n");
        sb.append("    var cfg = Av1ModeTail(sb, cdefBits, enCdef, losslessFrame, ")
          .append("dqP, dqR, dlfP, dlfMulti, dlfR, 3)\n");
        sb.append("    var out = av1DecodeTileBlocks(data, mw, mh, blocks, cfg, ")
          .append("lossless, 1, enFi, 0, bd, txMode, 80, 0, 0, 0, 0)\n");
        sb.append("    var nb = blocks.size / 3\n");
        sb.append("    var b = 0\n");
        sb.append("    while (b < nb) {\n");
        sb.append("        var o = b * 17\n");
        sb.append("        println(\"D \" + ci + \" \" + b + \" \" + out[o] + \" \" ")
          .append("+ out[o + 1] + \" \" + out[o + 2] + \" \" + out[o + 3] + \" \" ")
          .append("+ out[o + 4] + \" \" + out[o + 5] + \" \" + out[o + 6] + \" \" ")
          .append("+ out[o + 7] + \" \" + out[o + 8] + \" \" + out[o + 11] + \" \" ")
          .append("+ out[o + 12])\n");
        sb.append("        b = b + 1\n");
        sb.append("    }\n");
        sb.append("}\n\n");
        sb.append("main() {\n");
        for (Case c : cases()) {
            if (!indices.contains(c.ci())) continue;
            int[] b = bytes(c.ci());
            int mw = 0, mh = 0;
            for (Blk blk : c.blocks()) {
                int bw = Av1DecodeTileSupport.N4W[blk.bs()];
                int bh = Av1DecodeTileSupport.N4H[blk.bs()];
                if (blk.r() + bh > mh) mh = blk.r() + bh;
                if (blk.c() + bw > mw) mw = blk.c() + bw;
            }
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
            sb.append("    runTile(").append(c.ci()).append(", ").append(c.bd())
              .append(", ").append(c.sbSize()).append(", ").append(c.enableCdef())
              .append(", ").append(c.cdefBits()).append(", ").append(c.codedLossless())
              .append(", ").append(c.deltaQPresent()).append(", ").append(c.deltaQRes())
              .append(", ").append(c.deltaLfPresent()).append(", ").append(c.deltaLfMulti())
              .append(", ").append(c.deltaLfRes()).append(", ").append(c.lossless())
              .append(", ").append(c.enableFilterIntra()).append(", ").append(c.txMode())
              .append(", ").append(mw).append(", ").append(mh)
              .append(", b").append(c.ci()).append(", blk").append(c.ci()).append(")\n");
        }
        sb.append("}\n");
        return sb.toString();
    }

    /** {@code Num_4x4_Blocks_Wide/High[BLOCK_SIZES_ALL]} (spec §9.3 / Av1Block.kf). */
    static final int[] N4W = {1, 1, 2, 2, 2, 4, 4, 4, 8, 8, 8, 16, 16, 16, 32, 32, 1, 4, 2, 8, 4, 16};
    static final int[] N4H = {1, 2, 1, 2, 4, 2, 4, 8, 4, 8, 16, 8, 16, 32, 16, 32, 4, 1, 8, 2, 16, 4};
}
