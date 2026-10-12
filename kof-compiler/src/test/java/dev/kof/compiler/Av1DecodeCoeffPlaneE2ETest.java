package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.bytes;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.cases;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.crossCaseIndices;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.expectedText;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.golden;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.javaEncodedBytes;
import static dev.kof.compiler.Av1DecodeCoeffPlaneSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the slice-3ad AV1 coefficient-driven intra
 * predict+reconstruct plane driver (image-vision front, plan §34):
 * {@code av1DecodeCoeffPlaneRecon} in {@code libs/image/Av1DecodeCoeffPlane.kf}.
 *
 * <p>This is the intra loop of libaom's {@code decode_token_recon_block}
 * ({@code av1/decoder/decodeframe.c:908}) with the coefficient values coming
 * from the REAL tile entropy stream: per transform block, in the slice-3y
 * enumeration order, the driver decodes the block's coefficients (slices
 * 3e/3x — {@code av1_read_coeffs_txb}: {@code get_txb_ctx} neighbour context,
 * the symbol walk, {@code av1_set_entropy_contexts}), derives its availability
 * (slice 3ab), predicts it from the already reconstructed neighbours (slice 3u),
 * then dequantizes + inverse-transforms + reconstructs it into the plane
 * (slice 3z) with the DECODED levels. Slice 3ac composed the same loop over a
 * synthetic level pattern; this slice replaces the pattern with what the tile
 * actually carries.
 *
 * <p>The golden is the REAL libaom path: the tile bytes are produced by the
 * faithful Java port of {@code aom_dsp/entenc.c} (the slice-3e/3x/3y precedent,
 * re-derived here by {@link #tileBytesMatchJavaEncoder}), and the C oracle
 * decodes them with libaom's OWN {@code av1_read_coeffs_txb} (with
 * {@code allow_update_cdf = 1} — the CDF adaptation the writer performs),
 * predicts with the REAL {@code av1/common/reconintra.c} builders and adds the
 * residual with the REAL {@code av1_highbd_inv_txfm_add_c}
 * ({@code av1/common/idct.c}), over 13 {@code (bitDepth, txSz, mode, blockSize)}
 * cases.
 */
class Av1DecodeCoeffPlaneE2ETest implements LibraryInstallSupport {

    /**
     * The cross native faces (riscv64/aarch64) are BLOCKED by the open
     * {@code known-bugs} §602 conservative-GC reclaim, exactly as the sibling
     * slice 3ac found: this slice's unit of work is ONE plane, and a plane of
     * 16 transform blocks already crosses the {@code kof_gc_collect} alloc-tick
     * of 4096, so a mid-loop collect reclaims the still-live {@code frame}
     * buffer every later block predicts from. The per-process split the other
     * sibling slices use does not work around it here (the corruption is
     * intra-plane). The driver is nonetheless proven exact on riscv64/aarch64
     * by the GC-disabled differential slice 3ac recorded. FIXED 11/10 (§602
     * closed — the x86 §541 free-list-reuse zeroing the cross runtime never
     * received, see the ledger): the gate is lifted and the cross faces run.
     */
    private static final boolean BLOCKED_BY_602 = false;

    private static final String CROSS_BLOCKED_REASON =
            "known-bugs §602 (conservative-GC reclaim of the live plane buffer across the "
                    + "interleaved intra loop) — cross native faces blocked until §602 is fixed; "
                    + "driver proven exact on riscv64/aarch64 via the GC-disabled differential";

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void coeffPlaneGoldenIsPinned() {
        assertTrue(golden().startsWith("C g0 8 3 0 0 12 16 16 74 87 64 64"),
                "golden must be the libaom dump");
        assertEquals(13, cases().size(), "13 oracle cases");
    }

    /**
     * The golden's {@code T} tile bytes must equal what the faithful Java od_ec
     * encoder produces for the same geometry and intent: this pins the encoder
     * (and the slice-3y enumeration it runs over) that the C oracle's reader
     * consumed, so a drift on either side is caught before the plane compare.
     */
    @Test
    void tileBytesMatchJavaEncoder() {
        for (int ci = 0; ci < cases().size(); ci++) {
            assertArrayEquals(bytes(ci), javaEncodedBytes(ci),
                    "tile bytes drift at case " + ci + " (" + cases().get(ci).tag() + ")");
        }
    }

    @Test
    void av1DecodeCoeffPlaneOnJvm() throws Exception {
        assertEquals(expectedText(), runJvm(kofProbe()));
    }

    @Test
    void av1DecodeCoeffPlaneOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedText(), result.stdout().strip());
    }

    @Test
    void av1DecodeCoeffPlaneOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedText(), runNativeX86(kofProbe()));
    }

    @Test
    void av1DecodeCoeffPlaneOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(crossCaseIndices()),
                runCrossPerCase("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1DecodeCoeffPlaneOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(crossCaseIndices()),
                runCrossPerCase("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void av1DecodeCoeffPlaneOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> driver.compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(expectedText(), stdout.toString(StandardCharsets.UTF_8).strip());
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossPerCase(String arch, Target target) throws Exception {
        StringBuilder all = new StringBuilder();
        List<Integer> cs = crossCaseIndices();
        for (int ci = 0; ci < cs.size(); ci++) {
            final int caseIndex = ci;
            String code = kofProbe(List.of(cs.get(ci)));
            Path root = tmp.resolve("cross-" + arch + "-" + ci);
            Files.createDirectories(root);
            Files.writeString(root.resolve("Main.kf"), code);
            Path out = root.resolve("out");
            withLibrary(root, () -> {
                CompilationResult result = compile(root.resolve("Main.kf"), out, target);
                assertTrue(result.success(),
                        () -> arch + " case " + caseIndex + " compile: "
                                + result.diagnostics().getDiagnostics());
                return null;
            });
            Path binary = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(binary), arch + " case " + ci + " binary must exist");
            ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").strip();
            assertEquals(0, process.waitFor(), arch + " case " + ci + " output: " + output);
            if (all.length() > 0) all.append('\n');
            all.append(output);
        }
        return all.toString();
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    @Override
    public String libraryName() {
        return "image";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Av1DecodeCoeffPlane.kf");
    }
}
