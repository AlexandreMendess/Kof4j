package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §690: a {@code Char} read back out of a {@code Char[]} compared
 * WRONG on the Script target — {@code "abc".toCharArray()[0] == 'a'} was FALSE
 * there while the JVM/native/JS say true.
 *
 * <p>Root: the interpreter's {@code char[]} holds real {@code Character} boxes
 * (§185 — without them {@code Array.set} dies "argument type mismatch"), and
 * {@code KofInterpreterValues.numEq} only understood {@code Number}, so a
 * {@code Character} fell through to {@code Objects.equals(Character('a'),
 * Integer(97))} = false. The ORDERED path was already correct (it goes through
 * {@code KofInterpreter.unboxInt}, which accepts {@code Character}/{@code Boolean}),
 * which is exactly why {@code <} agreed while {@code ==} diverged.
 *
 * <p>The oracle is the JVM. RED-first: the equality faces below printed
 * {@code false} on Script pre-fix.
 */
class CharBoxedCanonEqualityE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** O valor lido de um `Char[]` e o `charAt` (canon `Integer`) juntos. */
    private static final String SRC = """
            main() {
                var cs = "abc".toCharArray()
                var c0 = cs[0]
                println(c0 == 'a')
                println(cs[0] == 'a')
                println(cs[2] == 'c')
                println(c0 != 'b')
                println(c0 < 'b')
                println(c0 > 'Z')
                println((c0 as Int) == 97)
                println(cs[0] == cs[0])
                println(cs[0] == "abc".charAt(0))
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "true", "true", "true", "true", "true", "true", "true", "true", "true", "end");

    @Test
    void jvmIsTheContractForBoxedCharCanon() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"));
    }

    @Test
    void scriptMatchesTheJvmOnBoxedCharCanon() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(),
                "§690: o `Char` boxado (Character do char[]) tem de comparar igual ao JVM");
    }

    @Test
    void nativeX86MatchesTheJvmForBoxedChar() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86", Target.NATIVE, null));
    }

    @Test
    void jsMatchesTheJvmForBoxedChar() throws Exception {
        Assumptions.assumeTrue(has("node"), "node absent");
        Path dir = tmp.resolve("js");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "js compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(GOLDEN, stdout.toString(java.nio.charset.StandardCharsets.UTF_8).strip());
    }

    @Test
    void nativeRiscv64MatchesTheJvmForBoxedChar() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("riscv64", Target.NATIVE_RISCV64, "riscv64"));
    }

    @Test
    void nativeAarch64MatchesTheJvmForBoxedChar() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(GOLDEN, runNative("aarch64", Target.NATIVE_AARCH64, "aarch64"));
    }

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String runNative(String name, Target target, String arch) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, target);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        ProcessBuilder pb = arch == null
                ? new ProcessBuilder(binary.toString())
                : NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " (" + target + ")");
        assertEquals(0, p.exitValue(), () -> name + " saída: " + o);
        return o;
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
