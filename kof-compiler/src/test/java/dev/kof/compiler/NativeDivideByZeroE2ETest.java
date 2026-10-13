package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §693: integer division by a RUNTIME zero diverged three ways.
 * With a runtime divisor (so the compile-time {@code ARITH001} guard does not
 * fire), {@code var a = 10 / zero()}:
 *
 * <ul>
 *   <li>JVM: {@code ArithmeticException: / by zero} — and a Kof
 *       {@code try}/{@code catch} RECOVERS;</li>
 *   <li>Script: the same throw and the same recovery;</li>
 *   <li>JS: printed a SILENT {@code 0} and continued (the try/catch also
 *       printed {@code 0} — nothing was raised);</li>
 *   <li>native x86-64: died with a hardware {@code SIGFPE} (rc=136) printing
 *       nothing, which Kof's own {@code try}/{@code catch} did NOT catch.</li>
 * </ul>
 *
 * <p>The ledger's own parity table already recorded this scenario as
 * "ArithmeticException runtime, ARITH001 only catches constants", so
 * throw-and-catch is the contract, JVM/Script are the reference, and the JS
 * and native faces are the divergent ones.
 *
 * <p>The oracle is the JVM. RED-first: every face below was RED on native
 * pre-fix (the process died with rc=136 and the catch never ran).
 *
 * <p><b>Scope of this file:</b> the NATIVE face only — the process death, the
 * worst of the three. The JS face (the silent {@code 0}) is a SEPARATE unit
 * and stays OPEN in the ledger: the JS IR has no conditional EXPRESSION node
 * ({@code JsIf} is a statement) and {@code binaryExpr} returns an expression,
 * so the guard there wants a runtime helper in its OWN slice block — the same
 * shape the JS-STRICT cast face needed ({@code JsRuntimeCastFail} + a dedicated
 * block), not an inline ternary.
 */
class NativeDivideByZeroE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    /** O castigo é capturável: a guarda de runtime tem de virar um throw do Kof. */
    private static final String INT_TRY_SRC = """
            zero(): Int { return 0 }
            main() {
                var r = 0
                try {
                    r = 10 / zero()
                } catch (String e) {
                    r = -1
                }
                println(r)
                println("end")
            }
            """;

    private static final String LONG_TRY_SRC = """
            zeroL(): Long { return 0L }
            main() {
                var r = 0L
                try {
                    r = 10L / zeroL()
                } catch (String e) {
                    r = -1L
                }
                println(r)
                println("end")
            }
            """;

    /** O MOD tem a MESMA armadilha (`idiv` serve aos dois). */
    private static final String INT_MOD_TRY_SRC = """
            zero(): Int { return 0 }
            main() {
                var r = 0
                try {
                    r = 10 % zero()
                } catch (String e) {
                    r = -2
                }
                println(r)
                println("end")
            }
            """;

    /** O caso NÃO-zero segue intacto (a guarda não pode quebrar a aritmética). */
    private static final String OK_SRC = """
            main() {
                var a = 10
                var b = 3
                println(a / b)
                println(a % b)
                println(10L / 3L)
                println(10L % 3L)
                println(-7 / 2)
                println(-7 % 2)
                println("end")
            }
            """;

    private static final String OK_GOLDEN = String.join("\n", "3", "1", "3", "1", "-3", "-1", "end");

    // ── nativo: o divisor zero de runtime agora LANÇA (era SIGFPE) ──────────

    @Test
    void nativeIntDivisionByRuntimeZeroThrowsLikeTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals("-1\nend", runNative("div-int", INT_TRY_SRC),
                "§693: `10 / zero()` tem de ser CAPTURÁVEL no nativo (era SIGFPE, rc=136)");
    }

    @Test
    void nativeLongDivisionByRuntimeZeroThrowsLikeTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals("-1\nend", runNative("div-long", LONG_TRY_SRC),
                "§693: a largura Long tem a mesma armadilha (`idivq`)");
    }

    @Test
    void nativeModuloByRuntimeZeroThrowsLikeTheJvm() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals("-2\nend", runNative("mod-int", INT_MOD_TRY_SRC),
                "§693: o MOD usa o mesmo `idiv` e tinha a mesma armadilha");
    }

    @Test
    void theNonZeroArithmeticIsUntouched() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(OK_GOLDEN, runJvm("ok-jvm"), "o oráculo JVM mudou — revise o caso");
        assertEquals(OK_GOLDEN, runNative("ok-native", OK_SRC),
                "§693: a guarda não pode alterar a aritmética válida");
    }

    // ── oráculo ────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), OK_SRC);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String runNative(String name, String src) throws Exception {
        Path dir = tmp.resolve(name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.NATIVE);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), name + ": binário nativo ausente");
        Process p = new ProcessBuilder(binary.toString()).redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name);
        assertEquals(0, p.exitValue(),
                () -> "§693: " + name + " morreu em vez de LANÇAR (o SIGFPE dá rc=136). saída: " + o);
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
