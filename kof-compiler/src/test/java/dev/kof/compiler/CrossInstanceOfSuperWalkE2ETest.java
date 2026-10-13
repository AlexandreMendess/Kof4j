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
 * known-bugs §688 — {@code instanceof} on the CROSS natives (riscv64/aarch64)
 * HUNG FOREVER whenever the check had to WALK {@code kof_super_table}, i.e. for
 * every polymorphic subclass test where the object's own type id differs from
 * the target's. The riscv asm loaded the super type id into {@code a0} while the
 * loop head compares {@code t0}, so the walk re-read the SAME entry and never
 * advanced; it only escaped when the object's own type id was already the target
 * (the first {@code bne} hits and returns without walking). The x86 oracle
 * stores it in {@code %eax} — the register the loop compares — so x86 was
 * correct. Fixed by loading the super into {@code t0}; aarch64 inherits through
 * the riscv-&gt;aarch64 translation.
 *
 * <p>Pre-fix the cross faces do not print a wrong value, they never RETURN
 * (the test fails on the bounded qemu wait). The oracle is the JVM.
 */
class CrossInstanceOfSuperWalkE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String PROGRAM = """
            class Animal { }
            class Dog extends Animal { }
            class Cat extends Animal { }
            main() {
                var a: Animal = Animal()
                println(a instanceof Dog)
                var d: Animal = Dog()
                println(d instanceof Animal)
                println(d instanceof Dog)
                println(d instanceof Cat)
                var c: Animal = Cat()
                println(c instanceof Animal)
                println(c instanceof Dog)
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "false", "true", "true", "false", "true", "false", "end");

    @Test
    void superWalkOnJvmIsTheContract() throws Exception {
        assertEquals(GOLDEN, crossRunJvm());
    }

    @Test
    void superWalkOnScriptMatchesTheJvm() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void superWalkOnJsMatchesTheJvm() throws Exception {
        assertEquals(GOLDEN, crossRunJs());
    }

    @Test
    void superWalkOnNativeX86MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(crossHas("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, crossRunNativeX86());
    }

    @Test
    void superWalkOnNativeRiscv64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(crossHas("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, crossRunCross("riscv64", Target.NATIVE_RISCV64));
    }

    @Test
    void superWalkOnNativeAarch64MatchesTheJvm() throws Exception {
        Assumptions.assumeTrue(crossHas("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, crossRunCross("aarch64", Target.NATIVE_AARCH64));
    }

    private String crossRunJvm() throws Exception {
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

    private String crossRunJs() throws Exception {
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

    private String crossRunNativeX86() throws Exception {
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
        String output = NativeRiscv64E2ETest.runBounded(process, "native x86 binary");
        assertEquals(0, process.exitValue(), "native output: " + output);
        return output;
    }

    private String crossRunCross(String arch, Target target) throws Exception {
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
        // §688: BOUNDED (the shared harness helper) — a regression here is an
        // infinite loop in the asm walk, so an unbounded wait/read would hang
        // CI instead of failing; runBounded kills the child and throws.
        String output = NativeRiscv64E2ETest.runBounded(process, arch + " binary");
        assertEquals(0, process.exitValue(), arch + " output: " + output);
        return output;
    }

    private static boolean crossHas(String... cmds) {
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
