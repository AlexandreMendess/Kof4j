package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 DIAG-01: `var x = nope` printed the SAME SEM011 twice
 *  (errorCount = 2 in --json) — the sink appended without dedup. Two
 *  diagnostics with the identical (severity, position, code, message, args)
 *  tuple are the same defect reported twice; the sink collapses exact
 *  duplicates only, distinct diagnostics are never touched. */
class DiagnosticSinkDedupTest {

    @TempDir
    Path tmp;

    @Test
    void sinkCollapsesExactDuplicatesOnly() {
        DiagnosticCollector c = new DiagnosticCollector();
        c.error("Main.kf", 1, 18, 4, "Undefined variable or type: 'nope'", "SEM011");
        c.error("Main.kf", 1, 18, 4, "Undefined variable or type: 'nope'", "SEM011");
        assertEquals(1, c.errorCount(), "the same defect reported twice collapses to one");
        c.error("Main.kf", 1, 25, 4, "Undefined variable or type: 'nada'", "SEM011");
        assertEquals(2, c.errorCount(), "a DIFFERENT defect at a different position stays");
        c.error("Main.kf", 1, 18, 4, "Type mismatch", "SEM012");
        assertEquals(3, c.errorCount(), "a different CODE at the same position stays");
        c.warning("Main.kf", 2, 1, 1, "unused", "W0001");
        c.warning("Main.kf", 2, 1, 1, "unused", "W0001");
        assertEquals(4, c.getDiagnostics().size(),
                "3 distinct errors + the warning pair collapsed to 1 = 4 records");
    }

    @Test
    void identicalSem011ReportsOnce() throws Exception {
        Path file = tmp.resolve("dup/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() { var x = nope }");
        DiagnosticCollector d = new CompilerDriver()
                .compile(file, tmp.resolve("out-dup"), Target.JVM).diagnostics();
        assertEquals(1, d.errorCount(),
                "the audit's exact repro: one SEM011, not two");
    }

    @Test
    void twoDistinctDefectsStayTwo() throws Exception {
        Path file = tmp.resolve("two/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() { var x = nope  var y = nada }");
        DiagnosticCollector d = new CompilerDriver()
                .compile(file, tmp.resolve("out-two"), Target.JVM).diagnostics();
        assertEquals(2, d.errorCount(),
                "two different undefined names are two distinct defects");
    }
}
