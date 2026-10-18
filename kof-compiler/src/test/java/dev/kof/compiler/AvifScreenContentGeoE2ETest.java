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
        assertTrue(line.contains("Y 00=100 032=235"), () -> "the samples are not the reference: " + line);
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
        // The BODY field positions still decompose block 1's body.
        assertTrue(line.contains("BODYF n=8 stages=8 21 21 22 24 78 78 "),
                () -> "the body-field decomposition moved: " + line);
        assertTrue(line.contains("BSTART b0:19 b1:76 b2:129"),
                () -> "the block-entry decomposition moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the palette MODE info moved: " + line);
        assertTrue(line.contains("PALBLK pal(r0,c4,bs5,n2) col0=16 col1=235 idx0=0 idx1=0 "
                + "idx2=0 idx3=0 idx4=1"), () -> "the palette map is not exact: " + line);
        // Recorded, NOT asserted as correct: the left half is exact, the right
        // half (reference: 16/235 vertical stripes) is not.
        assertTrue(line.contains(
                "PATCH r0 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 100 16 16"),
                () -> "the palette block is not the reference: " + line);
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
        assertTrue(line.contains("BLKY (0,0,bs6)=100 (0,4,bs5)=16"),
                () -> "the 32x24 first palette block is not the reference: " + line);
        var line2 = runKof(canonical("avif_real_oh40.avif", "oh40.avif"));
        assertTrue(line2.contains("covered=80 frame=80"), () -> "the 32x40 walk moved: " + line2);
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
        assertTrue(line.contains("blocks=30 covered=1024 frame=1024 overlap=0 "
                + "symBits=296 payloadBits=296"), () -> "the tall walk moved: " + line);
        assertTrue(line.contains("Y 00=100 "), () -> "the left half must be exact: " + line);
        assertTrue(line.contains("BLKY (0,0,bs9)=100 (0,8,bs5)=16"),
                () -> "the half-frame split moved: " + line);
        assertTrue(line.contains("PALINFO b0:palY0/palUv0 b1:palY2/palUv0"),
                () -> "the tall palette MODE info moved: " + line);
        assertTrue(line.contains("STAGES b0:22>22>23>82 b1:117>139"),
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
    void losslessEncodePaletteMapIsExactOnJvm() throws Exception {
        var line = runKof(canonical("avif_enc_ll.avif", "ll.avif"));
        assertTrue(line.contains("symBits=152 payloadBits=152"),
                () -> "consumption moved: " + line);
        // The reference rows 0..5 are `100` x16 then `16 16` at x = 16,17.
        assertTrue(line.contains("PATCH r0 100 100 100 100 100 100 100 100 100 100 "
                + "100 100 100 100 100 100 16 16"),
                () -> "the palette block is not the reference: " + line);
        assertTrue(line.contains("Y 00=100 032=235"), () -> "samples moved: " + line);
        // THE FIX'S PROOF: the colours are the reference's two values AND the
        // index map is EXACT — 0,0,0,0,1,1,1,1 is the reference's stripe pattern
        // (x = 16..23 are 16,16,16,16,235,235,235,235).
        assertTrue(line.contains("PALBLK pal(r0,c4,bs5,n2) col0=16 col1=235 "
                + "idx0=0 idx1=0 idx2=0 idx3=0 idx4=1 idx5=1 idx6=1 idx7=1"),
                () -> "the palette map is not exact after the fix: " + line);
    }

    /**
     * A NON-SQUARE screen-content frame: 64x16, carrying the same 4px 16/235
     * checkerboard the exact fixtures use. Every fixture that decoded EXACTLY
     * before this one was square (or flat), and a 64x16 frame was measured to
     * THROW inside the Kof decode (`Index 64 out of bounds for length 64`,
     * recorded in the plan with its recipe and deliberately NOT committed).
     * ffmpeg's reference Y is (0,0)=16 (32,0)=16 (63,0)=235 (0,15)=235
     * (32,15)=235 (63,15)=16.
     */
    @Test
    void nonSquareFrameDecodesToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_ns6416.avif", "ns.avif"));
        assertTrue(line.contains("Y 00=16 032=16 063=235 320=235 3232=235 3263=16"),
                () -> "must match the ffmpeg reference: " + line);
    }

    /**
     * A frame whose dimensions are NOT multiples of 8: 20x20, carrying a 2px
     * 16/235 checkerboard — the finest partition this encoder will choose at
     * this size, which is what makes the edge block reachable. The spec and libaom size
     * the mi grid as {@code 2 * ceil(w / 8)} (the spec's {@code MiCols} — the mi
     * extent ROUNDED UP to a whole 8x8 block; fetched, av1/common/alloccommon.c
     * {@code ALIGN_POWER_OF_TWO(width, 3) >> MI_SIZE_LOG2}), while the probe used
     * {@code w >> 2}, a FLOOR: on a 20x20 frame the walk's {@code has_rows} /
     * {@code has_cols} at the edge answered from a 5x5 grid instead of the
     * reference's 6x6 and therefore took different partition BRANCHES (a full
     * partition read where libaom gathers, or the reverse). ffmpeg's reference
     * rows (columns 0..17) are {@code 16 16 235 235 16 16 235 235 16 16 235 235
     * 16 16 235 235 16 16} at y = 0 and the inverse at y = 2 (a 2px checkerboard,
     * the finest partition the encoder can reach at this size).
     */
    @Test
    void unalignedFrameDecodesToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_ua20.avif", "ua.avif"));
        assertTrue(line.contains("PATCH r0 16 16 235 235 16 16 235 235 16 16 235 235 16 16 235 235 16 16"),
                () -> "row 0 must match the ffmpeg reference: " + line);
        assertTrue(line.contains("PATCH r2 235 235 16 16 235 235 16 16 235 235 16 16 235 235 16 16 235 235"),
                () -> "row 2 must match the ffmpeg reference: " + line);
    }

    /**
     * A frame coded with MULTIPLE TILES: 128x128, the same 4px checkerboard,
     * encoded with {@code -tiles 2x2}. Every other fixture in this suite is
     * SINGLE-tile, so the tile-group header's per-tile sizes and the payload
     * split were never exercised at all. The probe decodes the FIRST tile (the
     * top-left 64x64), whose samples ffmpeg's plane gives as (0,0)=16 (32,0)=16
     * (63,0)=235 (0,32)=16 (32,32)=16 (63,32)=235.
     */
    @Test
    void multiTileFrameDecodesItsFirstTileToReferenceOnJvm() throws Exception {
        var line = runKof(canonical("avif_real_mt128.avif", "mt.avif"));
        assertTrue(line.contains("TILES n=4"), () -> "the fixture must be 2x2 tiles: " + line);
        assertTrue(line.contains("Y 00=16 032=16 063=235 320=16 3232=16 3263=235"),
                () -> "tile 0 must match the ffmpeg reference: " + line);
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
