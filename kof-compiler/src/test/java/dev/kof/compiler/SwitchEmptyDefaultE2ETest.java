package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #588 — pattern/destructuring switch-statement whose {@code default:}
 * case has an EMPTY body compiled to a self-referencing {@code goto}
 * (infinite loop / hang) on the JVM.
 *
 * <p>Root cause (measured in {@code SwitchStmtLowerer.lowerSwitchStmt},
 * pattern branch): when {@code ss.defaultBody()} is empty,
 * {@code defaultLabelPat} is ALIASED to {@code endLabelPat} itself, and the
 * code then emits {@code KofLabel(end)} immediately followed by
 * {@code KofJump(end)} at the same position — the backend resolves the jump
 * target to the jump's own offset ({@code 25: goto 25} in the issue's
 * bytecode evidence).
 */
class SwitchEmptyDefaultE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void emptyDefaultTerminatesJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(1, 2)
                    var total = 0
                    switch (p) {
                        case Point(var x, var y):
                            total = total + x + y
                        default:
                    }
                    println(total)
                }
                """);
        assertEquals("3", out, "#588: empty pattern-switch default must fall through, not hang");
    }

    @Test
    void emptyDefaultTerminatesJs(@TempDir Path tmp) throws Exception {
        String out = runJs(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(1, 2)
                    var total = 0
                    switch (p) {
                        case Point(var x, var y):
                            total = total + x + y
                        default:
                    }
                    println(total)
                }
                """);
        assertEquals("3", out, "#588: empty pattern-switch default must terminate on JS too");
    }

    @Test
    void emptyDefaultMatchedCaseTerminatesJvm(@TempDir Path tmp) throws Exception {
        // the MATCHED-case + empty-default path (a miss with an unrelated
        // record type would CCE on the instanceof-selected cast path — a
        // separate pre-existing face, not #588).
        String out = runJvm(tmp, """
                record Point(Int x, Int y)
                main() {
                    var p = Point(0, 0)
                    var total = 0
                    switch (p) {
                        case Point(var x, var y):
                            total = total + x + y + 100
                        default:
                    }
                    println(total)
                }
                """);
        assertEquals("100", out, "#588: non-empty case + empty default must exit 0");
    }

    private String runJvm(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                "-cp", outDir.toString() + ":kof-runtime/target/classes", "Default.Main")
                .redirectErrorStream(true).start();
        // §418: a regression here is a hang, never a wedged suite — bounded wait.
        boolean done = p.waitFor(30, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            fail("#588: program hung (empty pattern-switch default compiled to self-goto)");
        }
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), "JVM exit code, output: " + output);
        return output;
    }

    private String runJs(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path mjs = outDir.resolve("Default.mjs");
        Process p = new ProcessBuilder("node", mjs.toString()).redirectErrorStream(true).start();
        boolean done = p.waitFor(30, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            fail("#588: JS program hung (empty pattern-switch default)");
        }
        String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.exitValue(), "JS exit code, output: " + output);
        return output;
    }
}
