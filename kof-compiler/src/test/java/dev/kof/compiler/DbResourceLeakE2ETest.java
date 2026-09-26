package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-MEMORY-SAFETY Fase 3 fatia 5 — L-05/{@code MEM014} db-connection
 * resource-lifetime warning. Extends {@link ResourceLeakAnalysis} to the second
 * measured close-bearing creator: a {@code db.connect(...)} handle (a String
 * closed via {@code db.close(handle)}) that is never closed anywhere in the
 * body and never handed to anyone leaks. Same shared frontend as the web face,
 * so JVM/Native/JS emit the same diagnostic by construction and the Script
 * target stays byte-green for valid programs.
 *
 * <p>{@code kof.io} has NO close-bearing file handle (reads/writes are stateless
 * by path), so there is no file face here — inventing one would be a stub.
 * Closing via an alias is silenced by the escape rule (the alias may be closed
 * elsewhere), keeping the check zero-false-positive.</p>
 */
class DbResourceLeakE2ETest {

    /** Created, never closed, never escaped. */
    private static final String LEAK = """
            main() {
                var conn = db.connect("jdbc:h2:mem:t")
                println("up")
            }
            """;

    private static final String CLOSED = """
            main() {
                var conn = db.connect("jdbc:h2:mem:t")
                db.close(conn)
                println("released")
            }
            """;

    private static final String GUARDED_CLOSE = """
            main() {
                var conn = db.connect("jdbc:h2:mem:t")
                if (1 == 1) {
                    db.close(conn)
                }
                println("released")
            }
            """;

    private static final String ESCAPES_VIA_ALIAS = """
            main() {
                var conn = db.connect("jdbc:h2:mem:t")
                var other = conn
                println("up")
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path tmp, String dir, String source) throws IOException {
        Path out = tmp.resolve(dir);
        Files.createDirectories(out);
        Path src = out.resolve("Main.kf");
        Files.writeString(src, source);
        return src;
    }

    @Test
    void dbLeakWarnsIdenticallyOnJvmNativeJs(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-db-leak", LEAK);
        String expected = null;
        for (Target t : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(src, tmp.resolve("mem014-db-leak-" + t.name()), t);
            assertTrue(r.success(), t + ": MEM014 is a warning, the build must stay green: "
                    + r.diagnostics().getDiagnostics());
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("MEM014"), t + ": expected MEM014, got: " + diags);
            assertTrue(diags.contains("line=2"), t + ": must point at creation, got: " + diags);
            assertTrue(diags.contains("db.connect()"), t + ": must name the db creator, got: " + diags);
            assertFalse(diags.contains("MEM001"), t + ": no claim happens here: " + diags);
            if (expected == null) {
                expected = diags;
            } else {
                assertEquals(expected, diags, "cross-target parity of MEM014 (db) (" + t + ")");
            }
        }
    }

    @Test
    void closedDbHandleStaysSilentOnJvm(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-db-closed", CLOSED);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-db-closed-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "correct lifecycle must be silent: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void guardedDbCloseAnywhereSilencesTheWarning(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-db-guard", GUARDED_CLOSE);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-db-guard-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "conservative: close exists somewhere in the body: "
                        + r.diagnostics().getDiagnostics());
    }

    @Test
    void aliasedDbHandleSilencesTheWarning(@TempDir Path tmp) throws IOException {
        Path src = write(tmp, "mem014-db-alias", ESCAPES_VIA_ALIAS);
        CompilationResult r = driver.compile(src, tmp.resolve("mem014-db-alias-classes"), Target.JVM);
        assertTrue(r.success(), "JVM build");
        assertFalse(r.diagnostics().getDiagnostics().toString().contains("MEM"),
                "the alias may close it elsewhere — never a false positive: "
                        + r.diagnostics().getDiagnostics());
    }
}
