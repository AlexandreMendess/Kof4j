package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §213 — `as`-cast of a PRIMITIVE to a REFERENCE type emitted the raw
 * primitive followed by `checkcast` (`bipush 7` → `checkcast Object`) with no
 * boxing → `VerifyError: Bad type on operand stack` at the checkcast; the
 * class compiled but never loaded (R6). The var-annotation path
 * (`var o: Object = 7`) already boxed via `StatementLowerer`; only the
 * `as`-operator lowering missed the `kof_box`.
 *
 * <p>Guard: primitive→primitive casts (`i as Long`, `i as Char`) must stay
 * numeric conversions (no box); reference→reference (`x as Object`) must not
 * gain a spurious box; reference→primitive (`o as Int`) keeps its unbox (§205).
 */
class PrimitiveToReferenceCastE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void intLocalAsObjectBoxesAndRuns(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                main() {
                    var i = 7
                    var o = i as Object
                    println(o)
                }
                """);
        assertEquals("7", out, "`i as Object` must box the Int and print 7 (no VerifyError)");
    }

    @Test
    void intLiteralAsObjectBoxesAndRuns(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                main() {
                    var o = 7 as Object
                    println(o)
                }
                """);
        assertEquals("7", out, "`7 as Object` must box the literal and print 7");
    }

    @Test
    void eachPrimitiveAsObjectBoxesCorrectly(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                main() {
                    var a = 42 as Object
                    var b = 3.5 as Object
                    var c = true as Object
                    var d = 9 as Long as Object
                    println(a)
                    println(b)
                    println(c)
                    println(d)
                }
                """);
        assertEquals("42\n3.5\ntrue\n9", out, "every primitive width must box for `as Object`");
    }

    @Test
    void referenceAsObjectDoesNotBreak(@TempDir Path tmp) throws IOException {
        String out = runJvm(tmp, """
                class Dog { String bark() { return "woof" } }
                main() {
                    var d = new Dog()
                    var o = d as Object
                    println(o as Dog != null)
                }
                """);
        assertEquals("true", out, "reference→reference cast must still work");
    }

    @Test
    void primitiveToPrimitiveCastStaysNumeric(@TempDir Path tmp) throws IOException {
        // Q4 guard: `i as Long` is a numeric widening, NOT a box — must stay ec=0.
        // D-PRINT: o code point de um Char é obtido pela conversão explícita
        // `as Int` (antes `println(c)` imprimia o número por contrato; agora
        // imprime o CARÁTER — o teste pede o número de forma explícita).
        String out = runJvm(tmp, """
                main() {
                    var i = 7
                    var l = i as Long
                    var c = i as Char
                    println(l)
                    println(c as Int)
                }
                """);
        assertEquals("7\n7", out, "primitive→primitive cast must remain a numeric conversion");
    }

    @Test
    void referenceToPrimitiveUnboxes(@TempDir Path tmp) throws IOException {
        // §205 family: `o as Int` with o holding an Integer must unbox.
        String out = runJvm(tmp, """
                main() {
                    var o = 5 as Object
                    var n = o as Int
                    println(n + 1)
                }
                """);
        assertEquals("6", out, "reference→primitive cast must unbox");
    }
}
