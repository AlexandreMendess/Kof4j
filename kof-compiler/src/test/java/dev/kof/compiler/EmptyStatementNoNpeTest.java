package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #822 — um `;` vazio no início de um bloco crashava o compilador com NPE
 *  (inconsistente: NPE em main, diagnóstico limpo em fun). O `;` solitário é
 *  aceito e ignorado — nunca um crash. */
class EmptyStatementNoNpeTest extends SemanticResolutionSupport {

    private void compiles(Path tmp, String src) throws Exception {
        Path f = tmp.resolve("E822.kf");
        Files.writeString(f, src);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "empty statement não pode crashar o compilador: "
                + r.diagnostics().getDiagnostics());
    }

    @Test
    void loneSemicolonInMain(@TempDir Path tmp) throws Exception {
        compiles(tmp, "main() { ; }");
    }

    @Test
    void semicolonsInControlBlocks(@TempDir Path tmp) throws Exception {
        compiles(tmp, "main() { if (true) { ; }\n while (false) { ; }\n try { ; } catch (String e) { } }");
    }
}
