package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * known-bugs §693, the JS face: INTEGER division by a RUNTIME zero used to
 * compute a SILENT {@code 0} and continue, where the JVM and the Script throw
 * {@code ArithmeticException: / by zero} and a Kof {@code try}/{@code catch}
 * RECOVERS. The ledger's own parity table already recorded throw-and-catch as
 * the contract, so the JS was the divergent face (a wrong VALUE, R6 — not a
 * failure).
 *
 * <p>Root: the JS {@code /} is the host's IEEE division, so {@code 10 / 0} is
 * {@code Infinity}, and the {@code | 0} of {@code intWrap} turned that into
 * {@code 0}. The {@code Long} face was ALREADY catchable — the host raises a
 * {@code RangeError} from the {@code BigInt} division and the Kof {@code catch}
 * receives it (measured; only the message differs from the JVM's). So the unit
 * is the {@code Int} face, {@code DIV} and {@code MOD}.
 *
 * <p>The oracle is the JVM. RED-first: every face below printed {@code 0} (or
 * ran through) pre-fix.
 */
class JsDivideByZeroE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private static final String INT_DIV_SRC = """
            zero(): Int { return 0 }
            main() {
                var t = ""
                try {
                    t = "x" + (10 / zero())
                } catch (String e) {
                    t = "caught:" + e
                }
                println(t)
                println("end")
            }
            """;

    private static final String INT_MOD_SRC = """
            zero(): Int { return 0 }
            main() {
                var t = ""
                try {
                    t = "x" + (10 % zero())
                } catch (String e) {
                    t = "caught:" + e
                }
                println(t)
                println("end")
            }
            """;

    /** A aritmética válida NÃO pode mudar (a guarda só olha o divisor). */
    private static final String OK_SRC = """
            main() {
                var a = 10
                var b = 3
                println(a / b)
                println(a % b)
                println(-7 / 2)
                println(-7 % 2)
                println(7 / -2)
                println(0 / 5)
                println(2147483647 / 1)
                println("end")
            }
            """;

    private static final String OK_GOLDEN = String.join("\n", "3", "1", "-3", "-1", "-3", "0", "2147483647", "end");

    @Test
    void jsIntDivisionByRuntimeZeroThrowsLikeTheJvm() throws Exception {
        Assumptions.assumeTrue(has("node"), "node absent");
        String oracle = runJvm("div");
        assertEquals("caught:/ by zero\nend", oracle, "o oráculo JVM mudou — revise o caso");
        assertEquals(oracle, runJs("div", INT_DIV_SRC),
                "§693: `10 / zero()` tem de LANÇAR no JS (devolvia um `0` silencioso)");
    }

    @Test
    void jsIntModuloByRuntimeZeroThrowsLikeTheJvm() throws Exception {
        Assumptions.assumeTrue(has("node"), "node absent");
        String oracle = runJvm("mod");
        assertEquals(oracle, runJs("mod", INT_MOD_SRC),
                "§693: `10 % zero()` tem a mesma armadilha (o `%` do host é NaN)");
    }

    @Test
    void theValidArithmeticIsUntouched() throws Exception {
        Assumptions.assumeTrue(has("node"), "node absent");
        String oracle = runJvm("ok");
        assertEquals(OK_GOLDEN, oracle, "o oráculo JVM mudou — revise o caso");
        assertEquals(oracle, runJs("ok", OK_SRC),
                "§693: a guarda não pode alterar a aritmética válida");
    }

    /**
     * O bloco do helper é SEMEADO só quando a face é emitida — um programa sem
     * divisão inteira não pode carregar o `kofDivZero` (foi a colocação no CORE
     * que quebrou o slicing na face do cast).
     */
    @Test
    void theHelperIsSeededOnlyWhenTheFaceIsEmitted() throws Exception {
        Path dir = tmp.resolve("seeds");
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), "main() {\n    println(1 + 1)\n}\n");
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> "compile: " + r.diagnostics().getDiagnostics());
        String runtime = Files.readString(out.resolve("kof-runtime.mjs"), StandardCharsets.UTF_8);
        assertFalse(runtime.contains("kofDivZero"),
                "§693: um programa sem divisão inteira não pode carregar o helper");
    }

    // ── harness ────────────────────────────────────────────────────────────

    private String runJvm(String name) throws Exception {
        Path dir = tmp.resolve("jvm-" + name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), srcOf(name));
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JVM);
        assertTrue(r.success(), () -> name + " compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", out.toString(), "Default.Main").redirectErrorStream(true).start();
        String o = NativeRiscv64E2ETest.runBounded(p, name + " jvm");
        assertEquals(0, p.exitValue(), () -> name + " (oráculo JVM): " + o);
        return o;
    }

    private String runJs(String name, String src) throws Exception {
        Path dir = tmp.resolve("js-" + name);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.kf"), src);
        Path out = dir.resolve("out");
        CompilationResult r = driver.compile(dir.resolve("Main.kf"), out, Target.JS);
        assertTrue(r.success(), () -> name + " js compile: " + r.diagnostics().getDiagnostics());
        var stdout = new java.io.ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(out.resolve("Default.mjs"), stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, () -> name + " js saída: " + stdout);
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private static String srcOf(String name) {
        return switch (name) {
            case "div" -> INT_DIV_SRC;
            case "mod" -> INT_MOD_SRC;
            default -> OK_SRC;
        };
    }

    private static boolean has(String cmd) {
        try {
            Process p = new ProcessBuilder("sh", "-c", "command -v " + cmd)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
