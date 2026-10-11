package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.kof.compiler.Av1DecodeTilePlaneSupport.allCaseIndices;
import static dev.kof.compiler.Av1DecodeTilePlaneSupport.cases;
import static dev.kof.compiler.Av1DecodeTilePlaneSupport.expectedText;
import static dev.kof.compiler.Av1DecodeTilePlaneSupport.golden;
import static dev.kof.compiler.Av1DecodeTilePlaneSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the slice-3ae AV1 heterogeneous intra plane driver
 * (image-vision front, plan §34): {@code av1DecodeTilePlaneRecon} in
 * {@code libs/image/Av1DecodeTilePlane.kf}.
 *
 * <p>Slice 3ad drove ONE mode-info block per plane with a single uniform mode,
 * transform size and transform type. This slice is the composition libaom runs
 * for the blocks of a tile: a plane made of SEVERAL mode-info blocks, each with
 * its own size, transform size, transform type and intra mode, decoded in tile
 * order over ONE shared entropy stream (one reader, one adapting coefficient-CDF
 * store, one above/left entropy-context pair) — the cross-block interleaving of
 * {@code decode_partition} -> {@code decode_block} ->
 * {@code decode_token_recon_block}.
 *
 * <p>The golden is the REAL libaom path: the tile bytes are produced by the
 * faithful Java port of {@code aom_dsp/entenc.c} (the slice-3e/3x/3y/3ad
 * precedent), and the C oracle decodes them with libaom's OWN
 * {@code av1_read_coeffs_txb} (with {@code allow_update_cdf = 1}), predicts with
 * the REAL {@code av1/common/reconintra.c} builders and adds the residual with
 * the REAL {@code av1_highbd_inv_txfm_add_c} ({@code av1/common/idct.c}), over 7
 * heterogeneous cases (multi-block planes, mixed transform sizes, mixed modes
 * including the D45/D67/D203 availability-dependent ones).
 */
class Av1DecodeTilePlaneE2ETest implements LibraryInstallSupport {

    /**
     * The cross native faces (riscv64/aarch64) are BLOCKED by the open
     * {@code known-bugs} §602 conservative-GC reclaim, exactly as the sibling
     * slice 3ad found: this slice's unit of work is ONE plane of several blocks,
     * and the interleaved decode crosses the {@code kof_gc_collect} alloc-tick of
     * 4096, so a mid-loop collect reclaims the still-live {@code frame} buffer
     * every later block predicts from. The per-process split does not work around
     * it (the corruption is intra-plane). The faces SKIP with the reason named
     * rather than pass falsely.
     */
    private static final boolean BLOCKED_BY_602 = true;

    private static final String CROSS_BLOCKED_REASON =
            "known-bugs §602 (conservative-GC reclaim of the live plane buffer across the "
                    + "interleaved intra loop) — cross native faces blocked until §602 is fixed";

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void tilePlaneGoldenIsPinned() {
        assertTrue(golden().startsWith("C t0 8 16 8 74 87 64 32"),
                "golden must be the libaom dump");
        assertEquals(7, cases().size(), "7 oracle cases");
        assertEquals(2, cases().get(0).blocks().size(), "t0 is a two-block plane");
        assertEquals(6, cases().get(4).blocks().size(), "t4 mixes transform sizes");
    }

    @Test
    void av1DecodeTilePlaneOnJvm() throws Exception {
        assertEquals(expectedText(), runJvm(kofProbe()));
    }

    @Test
    void av1DecodeTilePlaneOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedText(), result.stdout().strip());
    }

    @Test
    void av1DecodeTilePlaneOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedText(), runNativeX86(kofProbe()));
    }

    @Test
    void av1DecodeTilePlaneOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(allCaseIndices()),
                runCrossPerCase("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1DecodeTilePlaneOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(allCaseIndices()),
                runCrossPerCase("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void av1DecodeTilePlaneOnJs() throws Exception {
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
        List<Integer> cs = allCaseIndices();
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
        return List.of("Av1DecodeTilePlane.kf");
    }
}
