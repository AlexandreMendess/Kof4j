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

import static dev.kof.compiler.Av1IntraAvailSupport.cases;
import static dev.kof.compiler.Av1IntraAvailSupport.expectedText;
import static dev.kof.compiler.Av1IntraAvailSupport.golden;
import static dev.kof.compiler.Av1IntraAvailSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the slice-3ab AV1 intra prediction availability
 * derivation (image-vision front, plan §34): {@code av1IntraAvail} /
 * {@code av1ScaleChromaBsize} in {@code libs/image/Av1IntraAvail.kf}. The golden
 * is the availability block of the pinned libaom {@code av1_predict_intra_block}
 * ({@code av1/common/reconintra.c}) — the {@code extend_modes} mode gating, the
 * filter-intra override, the directional {@code pAngle} override,
 * {@code scale_chroma_bsize} and the {@code has_top_right}/{@code has_bottom_left}
 * calls — over 2500 deterministic cases (all three {@code haveAboveRight}/
 * {@code haveBelowLeft} states are exercised). JVM/Script/x86-64/JS run the whole
 * probe in one process; the cross targets batch it by
 * {@link Av1IntraAvailSupport#CROSS_BATCH} to stay under the {@code known-bugs}
 * §602 alloc-tick-4096 collect (which sweeps a live caller-frame array on
 * riscv64/aarch64), the same per-process split the sibling
 * {@code Av1DecodeBlockE2ETest} uses per grid.
 */
class Av1IntraAvailE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void av1IntraAvailGoldenIsPinned() {
        assertTrue(golden().startsWith("C 12 0 0 1"), "golden must be the libaom dump");
        assertEquals(2500, cases().size(), "2500 oracle cases");
    }

    @Test
    void av1IntraAvailOnJvm() throws Exception {
        assertEquals(expectedText(), runJvm(kofProbe()));
    }

    @Test
    void av1IntraAvailOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedText(), result.stdout().strip());
    }

    @Test
    void av1IntraAvailOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedText(), runNativeX86(kofProbe()));
    }

    @Test
    void av1IntraAvailOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(expectedText(), runCrossPerBatch("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1IntraAvailOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(expectedText(), runCrossPerBatch("aarch64", Target.NATIVE_AARCH64));
    }

    @Test
    void av1IntraAvailOnJs() throws Exception {
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

    /**
     * Cross targets run one process per {@link Av1IntraAvailSupport#CROSS_BATCH}
     * case batch and the outputs are concatenated. This keeps every process's
     * allocation count under the {@code known-bugs} §602 alloc-tick-4096 collect,
     * which otherwise sweeps a still-live caller-frame array on riscv64/aarch64
     * (the crash is an {@code array index out of bounds} mid-table; x86-64/JVM/
     * Script/JS are correct on the identical program).
     */
    private String runCrossPerBatch(String arch, Target target) throws Exception {
        StringBuilder all = new StringBuilder();
        int total = cases().size();
        for (int from = 0; from < total; from += Av1IntraAvailSupport.CROSS_BATCH) {
            int to = Math.min(from + Av1IntraAvailSupport.CROSS_BATCH, total);
            Path root = tmp.resolve("cross-" + arch + "-" + from);
            Files.createDirectories(root);
            Files.writeString(root.resolve("Main.kf"), kofProbe(from, to));
            Path out = root.resolve("out");
            final int batchFrom = from;
            final int batchTo = to;
            withLibrary(root, () -> {
                CompilationResult result = compile(root.resolve("Main.kf"), out, target);
                assertTrue(result.success(),
                        () -> arch + " cases " + batchFrom + ".." + batchTo + " compile: "
                                + result.diagnostics().getDiagnostics());
                return null;
            });
            Path binary = out.resolve("Default/Main");
            assertTrue(Files.isRegularFile(binary),
                    arch + " cases " + from + ".." + to + " binary must exist");
            ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").strip();
            assertEquals(0, process.waitFor(),
                    arch + " cases " + from + ".." + to + " output: " + output);
            assertEquals(expectedText(from, to), output,
                    arch + " cases " + from + ".." + to + " mismatch");
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
        return List.of("Av1Avail.kf");
    }
}
