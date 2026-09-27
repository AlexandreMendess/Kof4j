package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** #596 — o repro VERBATIM da issue (records implementando uma interface
 * compartilhada no `listOf`): guarda a face RECORD+INTERFACE do widening
 * (os testes de classe do #360/§481 cobrem a face classe; o §481 corrigiu o
 * BFS do ancestral implícito `Record`). */
class RecordInterfaceWideningE2ETest {

    @Test
    void recordInterfaceWidening(@TempDir Path tempDir) throws Exception {
        Path src = tempDir.resolve("s596.kf");
        Files.writeString(src, """
                interface Shape

                record Circle(Int r) implements Shape
                record Square(Int s) implements Shape

                main() {
                    var shapes = listOf(Circle(1), Square(2))
                    for (var sh in shapes) {
                        println(sh)
                    }
                }
                """);
        Path out = tempDir.resolve("s596-out");
        CompilationResult r = new CompilerDriver().compile(src, out, Target.JVM);
        assertTrue(r.success(), "deve compilar: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main");
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, p.waitFor(), "exit 0, output: " + got);
        assertEquals("Circle[r=1]\nSquare[s=2]", got, "#596 verbatim");
    }
}
