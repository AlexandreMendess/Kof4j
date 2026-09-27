package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §601 — switch-EXPRESSION com o primeiro case cujo corpo = o identificador
 * bound pelo pattern (`case Lit(var v) -> v`): a inferência do resultado corria
 * ANTES do binding existir, o fallback sintético nascia boxado
 * (defaultValueOp(Unknown)) contra corpos int = merge int×referência =
 * VerifyError no load. O fix projeta os bindings num locals-descartável para a
 * inferência (o emit real não muda). */
class SwitchExprPatternBindingE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static String run(Path outDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + out);
        return out;
    }

    private Path compile(Path tempDir, String name, String source) throws IOException {
        Path src = tempDir.resolve(name + ".kf");
        Files.writeString(src, source);
        Path out = tempDir.resolve(name + "-out");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), name + " deve compilar: " + r.diagnostics().getDiagnostics());
        return out;
    }

    @Test
    void verbatimRecursiveEval(@TempDir Path tempDir) throws Exception {
        Path out = compile(tempDir, "s601a", """
                sealed interface Expr

                record Lit(Int value) implements Expr
                record Neg(Expr inner) implements Expr

                Int eval(Expr e) {
                    return switch (e) {
                        case Lit(var v) -> v
                        case Neg(var i) -> -eval(i)
                    }
                }

                main() {
                    println(eval(Neg(Lit(5))))
                }
                """);
        assertEquals("-5", run(out), "#601 verbatim");
    }

    @Test
    void allBarePatternIds(@TempDir Path tempDir) throws Exception {
        // todos os corpos = ids de pattern: TODA inferência pré-binding dá
        // Unknown — a projeção resolve pelos campos do record.
        Path out = compile(tempDir, "s601b", """
                sealed interface Shape

                record Circle(Int r) implements Shape
                record Square(Int s) implements Shape

                Int area(Shape sh) {
                    return switch (sh) {
                        case Circle(var r) -> r * 3
                        case Square(var s) -> s * s
                    }
                }

                main() {
                    println(area(Circle(2)))
                    println(area(Square(3)))
                }
                """);
        assertEquals("6\n9", run(out), "#601 all-bare-ids");
    }

    @Test
    void mixedBranchTypesStillBox(@TempDir Path tempDir) throws Exception {
        // o contrato §57/§70 (ramos de tipos distintos → Object) NÃO pode
        // regredir com a projeção.
        Path out = compile(tempDir, "s601c", """
                sealed interface Shape

                record Circle(Int r) implements Shape
                record Square(Int s) implements Shape

                main() {
                    var sh: Shape = Circle(2)
                    var label = switch (sh) {
                        case Circle(var r) -> r
                        case Square(var s) -> "quadrado " + s
                    }
                    println(label)
                }
                """);
        String got = run(out);
        assertTrue(got.equals("2") || got.startsWith("2"), "#601 differ: " + got);
    }

    @Test
    void withDefaultStillRuns(@TempDir Path tempDir) throws Exception {
        Path out = compile(tempDir, "s601d", """
                sealed interface Shape

                record Circle(Int r) implements Shape
                record Square(Int s) implements Shape

                Int area(Shape sh) {
                    return switch (sh) {
                        case Circle(var r) -> r * r
                        default -> -1
                    }
                }

                main() {
                    println(area(Circle(3)))
                    println(area(Square(4)))
                }
                """);
        assertEquals("9\n-1", run(out), "#601 com default");
    }
}
