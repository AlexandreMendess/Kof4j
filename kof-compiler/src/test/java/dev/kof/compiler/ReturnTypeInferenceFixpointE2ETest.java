package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #785 — quando o fixpoint de inferência de retorno esgota as 4 passadas ainda
 * `changed`, os tipos de expressão da ÚLTIMA passada eram DESCARTADOS
 * (`clearExpressionTypes()` no último passo, sem reposição).
 *
 * Face 1: a classe converge exatamente na 4ª passada e fica sem tipos de
 * expressão (silencioso — o lowering re-infere). Face 2: a cadeia de 5 perde o
 * tipo de um método e a chamada é tipada como `void` → `SEM033` (e o resultado
 * depende da ordem de declaração).
 */
class ReturnTypeInferenceFixpointE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    /** Face 1 (do relato): cadeia de 4 converge EXATAMENTE na 4ª passada. */
    private static final String CHAIN4 = """
            class Engine {
                f3() {
                    return this.f2()
                }
                f2() {
                    return this.f1()
                }
                f1() {
                    return this.f0()
                }
                f0() {
                    return 1.5
                }
                Int soma(Int a, Int b) {
                    var c = a + b
                    return c * 2
                }
            }

            main() {
                var e = new Engine()
                println(e.soma(2, 3))
                println(e.f3())
            }
            """;

    /** Face 2 (do relato): cadeia de 5, ordem mais-dependente-primeiro. */
    private static final String CHAIN5 = """
            class Engine {
                f4() {
                    return this.f3()
                }
                f3() {
                    return this.f2()
                }
                f2() {
                    return this.f1()
                }
                f1() {
                    return this.f0()
                }
                f0() {
                    return 1.5
                }
            }

            main() {
                var e = new Engine()
                var x = e.f4()
                println(x)
                println(x + 1.0)
            }
            """;

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Exit code should be 0, output: '" + output + "'");
            assertEquals(expected, output, "Unexpected output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted while running JVM class", e);
        }
    }

    @Test
    void chainConvergingOnTheFourthPassKeepsItsExpressionTypes(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Chain4.kf");
        Files.writeString(src, CHAIN4);
        CompilationResult result = driver.compile(src, dir.resolve("out"), Target.JVM);
        assertTrue(result.success(), "Compilation should succeed: " + result.diagnostics().getDiagnostics());
        var types = driver.semanticAnalyzer.expressionTypes();
        boolean typedF2 = types.keySet().stream()
                .anyMatch(e -> e instanceof MethodCallExpr mc && "f2".equals(mc.methodName()));
        boolean typedF1 = types.keySet().stream()
                .anyMatch(e -> e instanceof MethodCallExpr mc && "f1".equals(mc.methodName()));
        // `f2`/`f1` só são chamados DENTRO de Engine (via `this`); se o grupo de
        // expressões da classe foi descartado, nenhum tem tipo (Face 1).
        assertTrue(typedF2, "#785 Face 1: `this.f2()` ficou sem tipo — os tipos da última passada foram descartados");
        assertTrue(typedF1, "#785 Face 1: `this.f1()` ficou sem tipo — os tipos da última passada foram descartados");
    }

    @Test
    void fiveDeepChainCompilesAndRuns(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("Chain5.kf");
        Files.writeString(src, CHAIN5);
        // 1.5 + 1.0 = 2.5
        runJvm(src, dir.resolve("out"), "1.5\n2.5");
    }

    @Test
    void fiveDeepChainIsIndependentOfDeclarationOrder(@TempDir Path dir) throws IOException {
        StringBuilder rev = new StringBuilder("class Engine {\n");
        for (int i = 0; i <= 4; i++) {
            rev.append("    f").append(i).append("() {\n");
            if (i == 0) {
                rev.append("        return 1.5\n");
            } else {
                rev.append("        return this.f").append(i - 1).append("()\n");
            }
            rev.append("    }\n");
        }
        rev.append("}\n\nmain() {\n    var e = new Engine()\n    var x = e.f4()\n"
                + "    println(x)\n    println(x + 1.0)\n}\n");
        Path src = dir.resolve("Chain5Rev.kf");
        Files.writeString(src, rev.toString());
        runJvm(src, dir.resolve("out"), "1.5\n2.5");
    }
}
