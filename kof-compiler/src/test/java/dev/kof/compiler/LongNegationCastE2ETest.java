package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §694: the interpreter's {@code NEG} was a NESTED TERNARY mixing
 * {@code long}, {@code float}, {@code double} and {@code int} branches. Java's
 * conditional operator applies NUMERIC PROMOTION to its two branches, so the
 * whole expression had type {@code double} and the {@code long} branch was
 * WIDENED: {@code -bigL} produced a {@code Double} on the Script.
 *
 * <p>The value still PRINTED right ({@code println} formats a Double as
 * {@code -9000000000}), which is why this hid — but the next CAST saw a Double,
 * and {@code Double.intValue()} SATURATES ({@code -2147483648}) where the
 * JVM/JS/native TRUNCATE ({@code -410065408}). A silent wrong value in the
 * interpreter, reachable from the plainest expression.
 *
 * <p>The oracle is the JVM. RED-first: every {@code as Int} face below was RED
 * on Script pre-fix.
 */
class LongNegationCastE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** A largura que estoura o `int` — é onde saturar e truncar divergem. */
    private static final String SRC = """
            main() {
                var bigL = 9000000000L
                var neg = -bigL
                println(neg)
                println(neg as Int)
                println((-bigL) as Int)
                var n = -9000000000L
                println(n as Int)
                var small = 5L
                println(-small)
                println((-small) as Int)
                var dl = 2.5
                println(-dl)
                println(-(2.5))
                var f = 1.5
                println(-f)
                var i = 7
                println(-i)
                println("end")
            }
            """;

    private static final String GOLDEN = String.join("\n",
            "-9000000000", "-410065408", "-410065408", "-410065408",
            "-5", "-5", "-2.5", "-2.5", "-1.5", "-7", "end");

    @Test
    void jvmIsTheContractForLongNegation() throws Exception {
        assertEquals(GOLDEN, runJvm("oracle"), "o oráculo JVM mudou — revise o caso");
    }

    @Test
    void scriptMatchesTheJvmOnLongNegation() throws Exception {
        Path dir = tmp.resolve("script");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        KofInterpreter.Result r = driver.interpret(
                java.util.List.of(dir.resolve("Main.kf")), dir, new String[0]);
        assertEquals(0, r.exitCode(), () -> "script stderr: " + r.stderr());
        assertEquals(GOLDEN, r.stdout().strip(),
                "§694: o `-x` de um Long tem de continuar Long (o ternário aninhado alargava p/ Double)");
    }

    @Test
    void nativeX86MatchesTheJvmForLongNegation() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(GOLDEN, runNative("x86"));
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

    private String runNative(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name);
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
