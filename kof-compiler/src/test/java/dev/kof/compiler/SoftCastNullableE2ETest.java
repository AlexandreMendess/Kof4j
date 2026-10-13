package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §678 face A — the nullable reference cast {@code x as T?} is the
 * ALWAYS-NULL soft cast (D-SEM-REJECTS-1210, maintainer 12/10: "plain `as`
 * THROWS on all four targets; `as T?` is the always-null spelling").
 *
 * <p>Measured pre-fix: the JVM THREW {@code ClassCastException} and
 * Script/JS/native returned the OBJECT ITSELF (the value-typed backends lower
 * {@code KofCheckCast} to a no-op) — so the nullable face was wrong on every
 * target, in two different ways. The fix desugars the nullable REFERENCE cast in
 * the SHARED lowerer to {@code if (x instanceof T) x else null} (the value routed
 * through a temp local so it is evaluated once and each branch has ops), which
 * makes the targets agree BY CONSTRUCTION with no new runtime. The oracle is the
 * JVM.
 *
 * <p>The first attempt appeared to hang riscv64/aarch64; the trail led to a
 * SEPARATE pre-existing bug in the cross {@code kof_instanceof} super-walk
 * (known-bugs §688, FIXED 12/10) which the desugar merely exposed. The HARD cast
 * ({@code x as T}) is a SEPARATE face (Script/JS/native still return the object
 * unchecked where the JVM throws) and stays OPEN — deliberately not pinned here.
 */
class SoftCastNullableE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PROGRAM = """
            class Animal { }
            class Dog extends Animal { }
            main() {
                var a: Animal = Animal()
                var s = a as Dog?
                println(s == null)
                println(s instanceof Animal)
                var a2: Animal = Dog()
                var t = a2 as Dog?
                println(t != null)
                println(t instanceof Dog)
                var o: Object = 42
                var bad = o as String?
                println(bad == null)
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "true", "false", "true", "true", "true", "end");

    @Test
    void softCastJvmIsTheContract() throws Exception {
        assertEquals(GOLDEN, softRunJvm());
    }

    @Test
    void softCastScriptMatchesTheJvm() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void softCastJsMatchesTheJvm() throws Exception {
        assertEquals(GOLDEN, softRunJs());
    }

    @Test
    void softCastNativeX86MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(softHas("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, softRunNativeX86());
    }

    @Test
    void softCastNativeRiscv64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(softHas("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, softRunCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void softCastNativeAarch64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(softHas("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, softRunCross("aarch64", Target.NATIVE_AARCH64));
    }

    private String softRunJvm() throws Exception {
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
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

    private String softRunJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JS);
        assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String softRunNativeX86() throws Exception {
        Path root = tmp.resolve("native-x86");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), "native binary must exist");
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        // BOUNDED (the shared harness helper) — a regression on the cross is a
        // non-terminating child (§688), so an unbounded read would hang CI.
        String output = NativeRiscv64E2ETest.runBounded(process, "native x86 binary");
        assertEquals(0, process.exitValue(), "native output: " + output);
        return output;
    }

    private String softRunCross(String arch, Target target) throws Exception {
        Path root = tmp.resolve("cross-" + arch);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
        assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = NativeRiscv64E2ETest.runBounded(process, arch + " binary");
        assertEquals(0, process.exitValue(), arch + " output: " + output);
        return output;
    }

    private static boolean softHas(String... cmds) {
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
}
