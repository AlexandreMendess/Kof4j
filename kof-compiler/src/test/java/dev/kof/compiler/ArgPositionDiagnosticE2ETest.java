package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §280 — SEM013/SEM014 argument diagnostics must carry the REAL source
 * position (the offending argument's line/column), not the useless :0:0.
 *
 * Before: `Int f() { return "x" }`-class errors pointed at nothing
 * (`error("", 0, 0, 0, …)` hard-coded in the typer). The constructor face
 * already had a position; this locks the method/top-level call faces.
 */
class ArgPositionDiagnosticE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(Path tempDir, String src) throws Exception {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        return driver.compile(source, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void topLevelCallArgMismatchReportsRealPosition(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                void show(String s) { println(s) }
                main() { show(42) }
                """);
        assertFalse(result.success(), "Int into (String) must not compile");
        Diagnostic d = result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM014".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM014: " + result.diagnostics().getDiagnostics()));
        assertEquals(2, d.line(), "SEM014 must point at the call line, not 0: " + d.format());
        assertTrue(d.column() > 0, "SEM014 must carry a real column: " + d.format());
        assertTrue(d.format().contains("Main.kf:2:"), "format carries file:line: " + d.format());
    }

    @Test
    void classMethodArgMismatchReportsRealPosition(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A {
                    constructor() {}
                    void show(String s) { println(s) }
                }
                main() {
                    var a = A()
                    a.show(42)
                }
                """);
        assertFalse(result.success(), "Int into (String) must not compile");
        Diagnostic d = result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM014".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM014: " + result.diagnostics().getDiagnostics()));
        assertEquals(7, d.line(), "SEM014 must point at a.show(42), not 0: " + d.format());
        assertTrue(d.column() > 0, "SEM014 must carry a real column: " + d.format());
    }

    @Test
    void wrongArityReportsRealPosition(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                void show(String s) { println(s) }
                main() { show("a", "b") }
                """);
        assertFalse(result.success(), "arity mismatch must stay a compile error");
        Diagnostic d = result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM013".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM013: " + result.diagnostics().getDiagnostics()));
        assertEquals(2, d.line(), "SEM013 must point at the call line: " + d.format());
    }

    @Test
    void redeclarationReportsRealLine(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                main() {
                    var x = 1
                    var x = 2
                }
                """);
        assertFalse(result.success(), "redeclaration must not compile");
        Diagnostic d = result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM024".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM024: " + result.diagnostics().getDiagnostics()));
        assertEquals(3, d.line(), "SEM024 must point at the redeclaration line: " + d.format());
    }

    @Test
    void unknownFieldReportsRealLine(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                class A { constructor() {} }
                main() {
                    var a = A()
                    println(a.bogus)
                }
                """);
        assertFalse(result.success(), "unknown field must not compile");
        Diagnostic d = result.diagnostics().getDiagnostics().stream()
                .filter(x -> "SEM025".equals(x.code()))
                .findFirst().orElseThrow(() -> new AssertionError(
                        "must report SEM025: " + result.diagnostics().getDiagnostics()));
        assertEquals(4, d.line(), "SEM025 must point at println(a.bogus): " + d.format());
    }

    @Test
    void correctArgsStillCompileAndRun(@TempDir Path tempDir) throws Exception {
        CompilationResult result = compile(tempDir, """
                void show(String s) { println(s) }
                main() { show("ok") }
                """);
        assertTrue(result.success(),
                "happy path (arg matches) must be untouched: " + result.diagnostics().getDiagnostics());
        String javaCmd = System.getProperty("java.home") + "/bin/java";
        Process p = new ProcessBuilder(javaCmd, "-cp", tempDir.resolve("out").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM exit code");
        assertEquals("ok", out, "call with the right arg still runs");
    }
}
