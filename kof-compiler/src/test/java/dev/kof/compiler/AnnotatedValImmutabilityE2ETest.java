package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #816: `val` com tipo ANOTADO (`val x: Int = 1`) não impunha imutabilidade —
 * só a forma inferida (`val x = 1`) emitia SEM037. Causa: o parser guardava a
 * palavra de mutabilidade e a anotação no MESMO campo (`type`), e a anotação
 * sobrescrevia o "val"; o analyzer derivava `isVal` de `"val".equals(vds.type())`.
 * Agora a mutabilidade vive em campo próprio do {@link VarDeclStmt}.
 */
class AnnotatedValImmutabilityE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String diagnostics(TempDirHolder h, String source) throws IOException {
        Path src = h.dir.resolve("P.kf");
        Files.writeString(src, source);
        CompilationResult r = driver.compile(src, h.dir.resolve("out"), Target.JVM);
        return (r.success() ? "OK " : "ERR ") + r.diagnostics().getDiagnostics();
    }

    private record TempDirHolder(Path dir) {}

    @Test
    void annotatedValReassignmentIsRefused(@TempDir Path tmp) throws IOException {
        String diags = diagnostics(new TempDirHolder(tmp), """
            main() {
                val x: Int = 1
                x = 2
            }
            """);
        assertTrue(diags.startsWith("ERR "), "annotated val reassignment must not compile: " + diags);
        assertTrue(diags.contains("SEM037"), "must be the named SEM037, was: " + diags);
    }

    @Test
    void annotatedStringValReassignmentIsRefused(@TempDir Path tmp) throws IOException {
        String diags = diagnostics(new TempDirHolder(tmp), """
            main() {
                val s: String = "a"
                s = "b"
            }
            """);
        assertTrue(diags.startsWith("ERR "), "annotated String val reassignment must not compile: " + diags);
        assertTrue(diags.contains("SEM037"), "must be the named SEM037, was: " + diags);
    }

    @Test
    void annotatedValCompoundAssignmentIsRefused(@TempDir Path tmp) throws IOException {
        String diags = diagnostics(new TempDirHolder(tmp), """
            main() {
                val s: String = "a"
                s += "b"
            }
            """);
        assertTrue(diags.startsWith("ERR "), "annotated val += must not compile: " + diags);
        assertTrue(diags.contains("SEM037"), "must be the named SEM037, was: " + diags);
    }

    @Test
    void annotatedVarStaysMutable(@TempDir Path tmp) throws IOException {
        String diags = diagnostics(new TempDirHolder(tmp), """
            main() {
                var x: Int = 1
                x = 2
                println(x)
            }
            """);
        assertFalse(diags.startsWith("ERR "), "annotated var must stay mutable: " + diags);
    }

    @Test
    void annotatedValReadIsFine(@TempDir Path tmp) throws IOException {
        String diags = diagnostics(new TempDirHolder(tmp), """
            main() {
                val x: Int = 7
                println(x)
            }
            """);
        assertFalse(diags.startsWith("ERR "), "reading an annotated val is legal: " + diags);
    }
}
