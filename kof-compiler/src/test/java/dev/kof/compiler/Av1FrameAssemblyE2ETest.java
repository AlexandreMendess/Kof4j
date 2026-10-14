package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.kof.compiler.Av1FrameAssemblySupport.GOLDEN;
import static dev.kof.compiler.Av1FrameAssemblySupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 3av (image-vision front, plan §34): the
 * frame assembly, {@code libs/image/Av1FrameAssembly.kf}. The tile driver
 * reconstructs into BORDERED plane buffers (the chroma planes travel in the
 * {@code Av1ChromaPlanes} bundle still carrying their one-sample border);
 * assembly strips the border and yields the flat planar frame.
 *
 * <p>No libaom oracle is claimed here: the assembly is pure addressing and the
 * driver's output is already pinned against libaom by the driver suites. What
 * this suite pins is the strip property those goldens cannot see — the probe
 * fills every buffer with a POSITION-derived value, so a border read, an
 * off-by-one or a wrong-stride read changes the number.
 */
class Av1FrameAssemblyE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void av1FrameAssemblyOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(kofProbe()));
    }

    @Test
    void av1FrameAssemblyOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(java.util.List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void av1FrameAssemblyOnJs() throws Exception {
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
        assertEquals(GOLDEN, stdout.toString(StandardCharsets.UTF_8).strip());
    }

    @Test
    void av1FrameAssemblyOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path root = tmp.resolve("native-x86");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        assertEquals(GOLDEN, runBinary(out.resolve("Default/Main")));
    }

    @Test
    void av1FrameAssemblyOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void av1FrameAssemblyOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64));
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm");
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

    private String runCrossCode(String arch, Target target) throws Exception {
        Path root = tmp.resolve("cross-" + arch);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        return runBinary(NativeRiscv64E2ETest.qemu(arch, binary));
    }

    @Override
    public String libraryName() {
        return "image";
    }

    @Override
    public java.util.List<String> libraryMarkers() {
        return java.util.List.of("Image.kf");
    }

    private String runBinary(Path binary) throws Exception {
        return runBinary(new ProcessBuilder(binary.toString()));
    }

    private String runBinary(ProcessBuilder pb) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "binary output: " + out);
        return out.strip();
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
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

    private static boolean has(String... tools) {
        for (String t : tools) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + t).start();
                if (p.waitFor() != 0) {
                    return false;
                }
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }
}
