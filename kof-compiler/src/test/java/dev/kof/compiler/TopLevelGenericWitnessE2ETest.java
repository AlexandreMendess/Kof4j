package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** §479 — witness EXPLÍTITO em função top-level genérica (`idf<Point>(...)`):
 * o retorno `T` ligado decide o unbox/checkcast do call-site em AMBOS os
 * paths (arquivo único e módulo/CLI). O golden do PR #593 (`idf<Int>` antes
 * de `idf<Point>`) + a forma solo do §477/#592. O path de MÓDULO (o ramo
 * selfMethod do ExpressionBareCallLowerer) é provado pelo golden
 * tests/golden/generic-toplevel-function via `kof bench` (a face que o
 * golden do PR pegou e a suíte JUnit não cobre). */
class TopLevelGenericWitnessE2ETest {

    private static final String B = """
            record Point(Int x, Int y)

            T idf<T>(T x) { return x }

            main() {
                println(idf<Int>(7))
                var p = idf<Point>(Point(5, 6))
                println(p.x() + "," + p.y())
            }
            """;

    @Test
    void bareAloneJvm(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("gtfa.kf");
        Files.writeString(src, """
                record Point(Int x, Int y)

                T idf<T>(T x) { return x }

                main() {
                    var p = idf<Point>(Point(5, 6))
                    println(p.x() + "," + p.y())
                }
                """);
        Path out = tempDir.resolve("gtfa-out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "deve compilar: " + r.diagnostics().getDiagnostics());
        Process javap = new ProcessBuilder("javap", "-c", "-p", "-cp", out.toString(), "Default.Main").start();
        String dis = new String(javap.getInputStream().readAllBytes());
        javap.waitFor();
        long casts = dis.lines().filter(l -> l.contains("checkcast")).count();
        System.out.println("GTFA-CHECKCASTS=" + casts);
        System.out.println(dis);
    }

    @Test
    void genericThenReferenceInstantiation(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("gtfb.kf");
        Files.writeString(src, B);
        Path out = tempDir.resolve("gtfb-out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "deve compilar: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + got);
        assertEquals("7\n5,6", got, "golden #593 no compilador atual");
    }
}
