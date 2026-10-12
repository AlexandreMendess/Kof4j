package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * known-bugs §671 — two class constructors with the SAME arity but different
 * parameter types collided on the native backend: the mangler keyed
 * constructors by arity only, so both emitted the same
 * {@code Class_init_N} symbol and the assembler failed
 * {@code symbol ... is already defined} (found by the image-vision lane while
 * composing AVIF slice 3af; JVM and Script ran the same program).
 *
 * <p>Fix: the constructor symbol gains the parameter signature tag
 * <em>only</em> when the class actually has more than one {@code <init>} with
 * the same parameter count — the single-constructor case keeps the plain
 * {@code Class_init_N} symbol, so no existing program's symbols change.
 * Verified on all three mangling sites (definition, x86 call site, riscv64/
 * aarch64 call site): every target must produce the same output as the JVM.
 */
class NativeConstructorOverloadE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String PROGRAM = """
            class A {
                Int x
                constructor(Int a, Int b) { this.x = a + b }
                constructor(String s, Int a) { this.x = a }
                Int get() { return this.x }
            }
            main() { var a = A(1, 2); var b = A("s", 7); println("" + a.get() + " " + b.get()) }
            """;

    private static final String THREE_WAY = """
            class B {
                Int x
                constructor(Int a, Int b) { this.x = a + b }
                constructor(String s, Int a) { this.x = a }
                constructor(Bool flag, Int a) { this.x = a * 2 }
                Int get() { return this.x }
            }
            main() {
                var b1 = B(1, 2)
                var b2 = B("s", 7)
                var b3 = B(true, 5)
                println("" + b1.get() + " " + b2.get() + " " + b3.get())
            }
            """;

    private static final String GOLDEN = "3 7";
    private static final String THREE_GOLDEN = "3 7 10";

    @TempDir
    Path tmp;

    @Test
    void sameArityOverloadsRunOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(PROGRAM));
        assertEquals(THREE_GOLDEN, runJvm(THREE_WAY));
    }

    @Test
    void sameArityOverloadsRunOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), PROGRAM);
        KofInterpreter.Result result = driver.interpret(
                java.util.List.of(root.resolve("Main.kf")), root, new String[0]);
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void sameArityOverloadsAssembleAndRunOnNativeX86() throws Exception {
        assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNativeX86(PROGRAM));
        assertEquals(THREE_GOLDEN, runNativeX86(THREE_WAY));
    }

    @Test
    void sameArityOverloadsAssembleAndRunOnCrossNatives() throws Exception {
        assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64",
                "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross natives + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runCross("riscv64", Target.NATIVE_RISCV64, PROGRAM));
        assertEquals(THREE_GOLDEN, runCross("riscv64", Target.NATIVE_RISCV64, THREE_WAY));
        assertEquals(GOLDEN, runCross("aarch64", Target.NATIVE_AARCH64, PROGRAM));
        assertEquals(THREE_GOLDEN, runCross("aarch64", Target.NATIVE_AARCH64, THREE_WAY));
    }

    // ------------------------------------------------------------------

    private String runJvm(String program) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(program.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), program);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
        assertTrue(result.success(), () -> "jvm compile: " + result.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, java.nio.charset.StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString().trim();
    }

    private String runNativeX86(String program) throws Exception {
        Path root = tmp.resolve("native-" + Math.abs(program.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), program);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCross(String arch, Target target, String program) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(program.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), program);
        Path out = root.resolve("out");
        CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
        assertTrue(result.success(),
                () -> arch + " compile: " + result.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
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
}
