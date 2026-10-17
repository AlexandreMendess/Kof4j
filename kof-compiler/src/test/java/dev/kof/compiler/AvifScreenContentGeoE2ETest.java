package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for a SMALL screen-content frame that bounds the open {@code 3bd}
 * divergence far more tightly than the 64x64 {@code testsrc} fixture.
 *
 * <ul>
 *   <li>{@code avif_real_sq1.avif} — 32x32, an 18-byte payload. The left half
 *       is ONE {@code BLOCK_16X16} that reconstructs EXACTLY (flat 100) and the
 *       payload is consumed to its LAST BIT (144/144), while the right half
 *       (vertical 4px 16/235 stripes) decodes WRONG. It is the smallest
 *       reproducer on this front and the first whose entropy is FULLY consumed
 *       yet whose pixels still diverge — so full consumption alone is not a
 *       correctness criterion.</li>
 * </ul>
 */
class AvifScreenContentGeoE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    private final CompilerDriver compiler = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void smallFrameConsumesTheWholePayloadYetDivergesOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_sq1.avif", "sq1.avif"));
        assertTrue(line.contains("symBits=144 payloadBits=144"),
                () -> "the whole payload must be consumed: " + line);
        assertTrue(line.contains("Y 00=100 "), () -> "the left half is exact: " + line);
        // RECORDED, not asserted as correct. The palette-token stage of block 1
        // (window 107..144) costs 37 bits. Its symbol count is a LOWER BOUND:
        // the reader saturates at the payload end, so every later read reports
        // the SAME position and cannot be told apart from a palette read.
        // RECORDED: block 1 IS signalled as a 2-colour palette block (palY=2)
        // over a 16x8 map, so "Kof takes the palette path where the encoder
        // wrote none" is REFUTED — a 2-colour palette is exactly right for the
        // reference's 4px 16/235 stripes, and ~37 bits for its 128-cell index
        // map is plausible.
        // RECORDED: the block-ENTRY reader positions decompose each block into
        // partition / mode-info / residual. sq1: b0 entry 19, b1 entry 76 —
        // so block 0 is 1 bit of partition + 5 body + 1 tx size + 49 residual,
        // and block 1 is 2 bits of partition + 31 body + 37 palette.
        // RECORDED: the BODY field positions localize the over-consumption to the
        // UV/CFL/UV-angle group PLUS the palette MODE info — block 1's second and
        // third fields (76->78->78) and then 78->105, i.e. ~25 bits for the palette
        // MODE info of a 2-colour palette, against a handful of symbols.
        assertTrue(line.contains("BODYF 21 21 22 24 78 78 105 107"),
                () -> "the body-field decomposition moved: " + line);
        assertTrue(line.contains("BSTART b0:19 b1:76 b2:144"),
                () -> "the block-entry decomposition moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the palette MODE info moved: " + line);
        assertTrue(line.contains("PALTOK window=107..144 bits=37 symbols=61"),
                () -> "the palette-stage measurement moved: " + line);
        // Recorded, NOT asserted as correct: the left half is exact, the right
        // half (reference: 16/235 vertical stripes) is not.
        assertTrue(line.contains(
                "PATCH r0 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 235 235"),
                () -> "recorded divergence moved: " + line);
    }

    /**
     * Frame heights that are NOT a multiple of the block grid (32x24 and 32x40,
     * the same screen content as {@code sq1}) — built to exercise the palette
     * OVERHANG defect found in the 65th pass. RECORDED, not asserted as correct:
     * both fail in the SAME pattern as the 32x32 frame, with block 1's
     * palette-token stage consuming the rest of the payload
     * ({@code b0:24>24>25>74 b1:107>136>136>136} at 32x24,
     * {@code b0:25>25>26>76 b1:109>152>152>152} at 32x40), and the bottom-right
     * pixel reads 81 where the reference is 235.
     *
     * <p>They also REFUTE the overhang defect as the trigger of this desync:
     * block 1 is at (0,4) BLOCK_16X8 in a 32-wide frame, i.e. its
     * {@code mb_to_right_edge == 0}, so {@code av1_get_block_dimensions} does
     * NOT reduce it — the failure reproduces at a NON-overhanging block.
     */
    @Test
    void overhangingHeightFramesRecordTheSameBlockOneDesyncOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_oh24.avif", "oh24.avif"));
        assertTrue(line.contains("STAGES b0:24>24>25>74 b1:107>136>136>136"),
                () -> "the recorded stage positions moved (32x24): " + line);
        var line2 = runKof(canonical("avif_real_oh40.avif", "oh40.avif"));
        assertTrue(line2.contains("STAGES b0:25>25>26>76 b1:109>152>152>152"),
                () -> "the recorded stage positions moved (32x40): " + line2);
    }

    /**
     * The 64x256 TALL fixture (built to exercise the palette-Y MODE context at
     * the 64x64 superblock row starts). RECORDED, not asserted as correct: it
     * consumes its payload to the last bit (296/296) and its RIGHT half is wrong
     * while the LEFT half is exact. Geometry it records: block 0 is a
     * `BLOCK_32X32` covering the flat left half (reconstructed 100 = the
     * reference) and the FIRST right-half block — `(0,8)` `BLOCK_16X8` — is a
     * 2-colour palette block (`PALINFO b1:palY2`) whose palette-token stage costs
     * 90 bits (118 -> 208) of the 296, after which the reader is exhausted.
     */
    @Test
    void tallFrameRecordsTheRightHalfDivergenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_tall.avif", "tall.avif"));
        assertTrue(line.contains("blocks=14 covered=1024 frame=1024 overlap=0 "
                + "symBits=296 payloadBits=296"), () -> "the tall walk moved: " + line);
        assertTrue(line.contains("Y 00=100 "), () -> "the left half must be exact: " + line);
        assertTrue(line.contains("BLKY (0,0,bs9)=100 (0,8,bs5)=235"),
                () -> "the half-frame split moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the tall palette MODE info moved: " + line);
        assertTrue(line.contains("STAGES b0:22>22>23>82 b1:118>208>209>213"),
                () -> "the tall stage positions moved: " + line);
    }

    /**
     * The SAME 32x32 screen content re-encoded by libaom with `-lossless 1`.
     * RECORDED, not asserted as correct — and it carries a SHARPER symptom than
     * any previous fixture: the LEFT half is exact (`100` x16) and the right half
     * is painted with the TWO PALETTE COLOURS INVERTED — Kof writes `235` at
     * x = 16..19 where the reference writes `16` (the reference's stripes start
     * with 16 at x = 16). A 2-colour palette whose index map or colour order is
     * swapped produces exactly this, and the payload is fully consumed
     * (152/152), so the entropy stays in sync: this is a MAP/COLOUR-ORDER
     * symptom, not a desync.
     */
    @Test
    void losslessEncodeRecordsTheInvertedPaletteOnJvm() throws Exception {
        var line = runKof(canonical("avif_enc_ll.avif", "ll.avif"));
        assertTrue(line.contains("symBits=152 payloadBits=152"),
                () -> "consumption moved: " + line);
        // The reference rows 0..5 are `100` x16 then `16 16` at x = 16,17.
        assertTrue(line.contains("PATCH r0 100 100 100 100 100 100 100 100 100 100 "
                + "100 100 100 100 100 100 235 235"),
                () -> "the recorded inverted palette moved: " + line);
        assertTrue(line.contains("Y 00=100 032=235"), () -> "samples moved: " + line);
        // THE DECISIVE MEASUREMENT (RECORDED): the palette block's COLOURS are
        // CORRECT (16 and 235, the reference's two values in monotone order) and
        // its INDICES are WRONG — all eight cells of the map's first row decode
        // to 1 where the reference needs 0,0,0,0,1,1,1,1. So the paint is fed a
        // wrong INDEX, not a wrong colour, and the fault is in the MAP's decode.
        assertTrue(line.contains("PALBLK pal(r0,c4,bs5,n2) col0=16 col1=235 "
                + "idx0=1 idx1=1 idx2=1 idx3=1 idx4=1 idx5=1 idx6=1 idx7=1"),
                () -> "the palette colour/index split moved: " + line);
    }

    /** The fixture with a temporal delimiter prepended (the canonical OBU order). */
    private Path canonical(String resource, String name) throws Exception {
        byte[] raw = Files.readAllBytes(Path.of("src/test/resources", resource));
        int m = -1;
        for (int i = 0; i + 4 <= raw.length; i++) {
            if (raw[i] == 'm' && raw[i + 1] == 'd' && raw[i + 2] == 'a' && raw[i + 3] == 't') {
                m = i;
                break;
            }
        }
        int size = ((raw[m - 4] & 0xff) << 24) | ((raw[m - 3] & 0xff) << 16)
                | ((raw[m - 2] & 0xff) << 8) | (raw[m - 1] & 0xff);
        byte[] item = new byte[size - 8];
        System.arraycopy(raw, m + 4, item, 0, item.length);
        byte[] delim = AvifSeqSupport.delimiter();
        byte[] both = new byte[delim.length + item.length];
        System.arraycopy(delim, 0, both, 0, delim.length);
        System.arraycopy(item, 0, both, delim.length, item.length);
        Path out = tmp.resolve(name);
        Files.write(out, AvifObuSupport.containerWithItem(both));
        return out;
    }

    private String runKof(Path fixture) throws Exception {
        String code = AvifRealTileProbe.probe(fixture.toString());
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        installLib(root);
        CompilationResult result = compiler.compile(source, out, Target.JVM);
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private void installLib(Path root) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
    }
}
