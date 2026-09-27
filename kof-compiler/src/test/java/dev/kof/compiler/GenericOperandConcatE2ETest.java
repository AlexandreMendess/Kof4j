package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §244 — issue #267: `+` com DOIS operandos genéricos apagados (`Object`) emitia
 * `iadd` → `VerifyError: Bad type on operand stack` (o `else` final de
 * `ExpressionBinaryLowerer` emitia `KofBinaryOp.ADD` sobre referências, e o
 * `opcodeForArithmetic` do JVM devolvia o default int `IADD`).
 *
 * <p>Contrato Kof (`training/language/types.md:151`): `String + anything →
 * String`. Com ambos os lados não-numéricos, os dois são stringificados e
 * concatenados — o oráculo JVM/interpretador dá `helloworld`/`23`.
 */
class GenericOperandConcatE2ETest {

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
    void erasedGenericStringOperandsConcatenate(@TempDir Path tmp) throws IOException {
        // o repro exato da issue #267
        String out = runJvm(tmp, """
                class Pair<A, B> {
                    A first
                    B second
                }
                main() {
                    var p = new Pair<String, String>()
                    p.first = "hello"
                    p.second = "world"
                    println(p.first + p.second)
                }
                """);
        assertEquals("helloworld", out, "dois genéricos String apagados devem concatenar");
    }

    @Test
    void erasedGenericIntOperandsAddNumerically(@TempDir Path tmp) throws IOException {
        // Com a substituição §245 (`Pair<Int,Int>.first` → `Int`), os dois
        // operandos são numéricos e `+` é SOMA (não concatenação) — o campo
        // erasure `Object` é desboxado no load. O oráculo do interpretador
        // (`KofInterpreterOps`) dá 5; antes da §245 o tipo ficava TypeVariable
        // (não-numérico) e caía no concat → "23", que era comportamento errado.
        String out = runJvm(tmp, """
                class Pair<A, B> {
                    A first
                    B second
                }
                main() {
                    var p = new Pair<Int, Int>()
                    p.first = 2
                    p.second = 3
                    println(p.first + p.second)
                }
                """);
        assertEquals("5", out, "genéricos Int apagados somam (oráculo do interpretador)");
    }

    @Test
    void genuinelyErasedObjectOperandsConcatenate(@TempDir Path tmp) throws IOException {
        // o caso do §244 em estado PURO: dois operandos `Object` (sem
        // substituição) — nenhum é String nem numérico, então o ramo de concat
        // stringifica os dois (String + anything → String).
        String out = runJvm(tmp, """
                main() {
                    var x = new Object()
                    var y = new Object()
                    println(x + y)
                }
                """);
        assertTrue(out.startsWith("java.lang.Object@"), "Object+Object deve stringificar: " + out);
        assertTrue(out.indexOf("java.lang.Object@") != out.lastIndexOf("java.lang.Object@"),
                "os dois operandos devem aparecer concatenados: " + out);
    }

    @Test
    void interpreterAgreesWithJvmForErasedGenericPlus(@TempDir Path tmp) throws Exception {
        // paridade interpretador × JVM no mesmo programa (oráculo do contrato)
        Path d = tmp;
        Path f = d.resolve("Main.kf");
        Files.writeString(f, """
                class Pair<A, B> {
                    A first
                    B second
                }
                main() {
                    var p = new Pair<String, String>()
                    p.first = "hello"
                    p.second = "world"
                    println(p.first + p.second)
                }
                """);
        KofInterpreter.Result r = new CompilerDriver().interpret(java.util.List.of(f), d, new String[0]);
        assertEquals(0, r.exitCode(), "interpretador exit: " + r.stderr());
        assertEquals("helloworld", r.stdout().trim(), "interpretador deve concordar com o JVM");
    }

    @Test
    void concreteNumericAndStringConcatUnaffected(@TempDir Path tmp) throws IOException {
        // não-regressão: os caminhos concretos continuam idênticos
        assertEquals("a1", runJvm(tmp, "main() { println(\"a\" + 1) }"), "String + Int concreto");
        assertEquals("5", runJvm(tmp, "main() { println(2 + 3) }"), "Int + Int concreto");
        assertEquals("1.5", runJvm(tmp, "main() { println(1.5) }"), "Double concreto");
    }
}
