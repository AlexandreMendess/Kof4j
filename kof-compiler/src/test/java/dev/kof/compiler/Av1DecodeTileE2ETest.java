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

import static dev.kof.compiler.Av1DecodeTileSupport.allCaseIndices;
import static dev.kof.compiler.Av1DecodeTileSupport.cases;
import static dev.kof.compiler.Av1DecodeTileSupport.expectedText;
import static dev.kof.compiler.Av1DecodeTileSupport.golden;
import static dev.kof.compiler.Av1DecodeTileSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the slice-3af AV1 intra mode-info + tx-size block walk
 * (image-vision front, plan §34): {@code av1DecodeTileBlocks} in
 * {@code libs/image/Av1DecodeTile.kf}.
 *
 * <p>The driver composes the landed slices 3p/3q/3r/3s into the walk libaom runs
 * as {@code decode_mbmi_block} + {@code read_tx_size} for every block of a tile:
 * per block, the prefix (segment id + skip), the tail (CDEF + delta-Q +
 * delta-LF), the body (Y/UV mode, angles, CFL, palette, filter-intra) and the
 * transform size, over ONE shared entropy stream — so a block's decisions depend
 * on the CDF adaptations and neighbour maps the previous blocks left behind.
 *
 * <p>The golden is the REAL libaom path: a C encoder writes the symbols in the
 * exact order {@code read_intra_frame_mode_info} reads them with libaom's OWN
 * default CDFs and its OWN range encoder, and the oracle decodes that stream
 * with libaom's REAL {@code read_intra_frame_mode_info} + {@code read_tx_size},
 * dumping every decision, over 8 cases (single and multi-block, both prefix
 * orders, cdef, delta-Q/LF, directional angle deltas, filter-intra, the skip
 * path, 10-bit and both transform modes).
 */
class Av1DecodeTileE2ETest implements LibraryInstallSupport {

    /**
     * The cross native faces (riscv64/aarch64) are BLOCKED by the open
     * {@code known-bugs} §602 conservative-GC reclaim, exactly as the sibling
     * slices found: a multi-block tile walk crosses the {@code kof_gc_collect}
     * alloc-tick of 4096, so a mid-walk collect reclaims the still-live arrays
     * the walk reads from. The faces SKIP with the reason named rather than pass
     * falsely.
     */
    private static final boolean BLOCKED_BY_602 = true;

    private static final String CROSS_BLOCKED_REASON =
            "known-bugs §602 (conservative-GC reclaim of live caller arrays across the tile "
                    + "walk) — cross native faces blocked until §602 is fixed";

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void tileGoldenIsPinned() {
        assertTrue(golden().startsWith("C 0 8 12 1 2 0 0 0 0 0 0 1 0 1 1"),
                "golden must be the libaom dump");
        assertEquals(8, cases().size(), "8 oracle cases");
        assertEquals(4, cases().get(1).blocks().size(), "case 1 is a four-block tile");
    }

    @Test
    void av1DecodeTileOnJvm() throws Exception {
        assertEquals(expectedText(), runJvm(kofProbe()));
    }

    @Test
    void av1DecodeTileOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedText(), result.stdout().strip());
    }

    @Test
    void av1DecodeTileOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedText(), runNativeX86(kofProbe()));
    }

    @Test
    void av1DecodeTileOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(), runCrossPerCase("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1DecodeTileOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Assumptions.assumeFalse(BLOCKED_BY_602, CROSS_BLOCKED_REASON);
        assertEquals(expectedText(), runCrossPerCase("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void av1DecodeTileOnJs() throws Exception {
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
        return List.of("Av1DecodeTile.kf");
    }
}
