package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * #617 — two faces, now reconciled (maintainer order: `mkdir` is a REAL
 * implementation, not a rejection):
 *
 * (1) `File("x").mkdir()`/`mkdirs()` are POSIX-style aliases of
 *     `create()`/`createDirectories()` in the live `kof.io` table — they
 *     compile and really create the directory (never a silent no-op, never
 *     a `ClassFormatError`).
 * (2) any OTHER unknown method on a `kof.io` builtin (File/Path/Directory)
 *     is a clean `SEM102` at compile time (same family as the array guard
 *     SEM028) instead of a silent no-op whose UNKNOWN result later leaked an
 *     empty class name into the constant pool (`ClassFormatError`).
 */
class IoUnknownMethodGuardTest {

    private final CompilerDriver driver = new CompilerDriver();

    private CompilationResult compile(String source, Path tempDir) throws IOException {
        Path src = tempDir.resolve("Main.kf");
        Files.writeString(src, source);
        return driver.compile(src, tempDir.resolve("out"), Target.JVM);
    }

    @Test
    void fileMkdirIsRealAliasAndCompiles(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                File("/tmp/opencode/io_guard_a").mkdir()
                File("/tmp/opencode/io_guard_a/b/c").mkdirs()
                println("stmt ok")
            }
            """, tempDir);
        assertTrue(result.success(), "File.mkdir()/mkdirs() must compile (real aliases): "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void directoryMkdirIsRealAliasAndCompiles(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                Directory("/tmp/opencode/io_guard_c").mkdir()
            }
            """, tempDir);
        assertTrue(result.success(), "Directory.mkdir() must compile (real alias): "
                + result.diagnostics().getDiagnostics());
    }

    @Test
    void unknownIoMethodFailsWithSem102InsteadOfSilentNoOp(@TempDir Path tempDir) throws IOException {
        CompilationResult result = compile("""
            main() {
                File("/tmp/opencode/io_guard_x").notAMethod()
                println("stmt ok")
            }
            """, tempDir);
        assertFalse(result.success(), "unknown io method must fail to compile (SEM102)");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertTrue(diags.contains("notAMethod"), "Diagnostic must name the method, was: " + diags);
    }

    @Test
    void unknownIoMethodToStringFailsWithSem102NotClassFormatError(@TempDir Path tempDir) throws IOException {
        // the symptom-B face: `.toString()` over the UNKNOWN result reached the
        // JVM as `ClassFormatError: Illegal class name ""`.
        CompilationResult result = compile("""
            main() {
                println(File("/tmp/opencode/io_guard_b").notAMethod().toString())
            }
            """, tempDir);
        assertFalse(result.success(), "unknown io method .toString() must fail to compile");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("SEM102"), "Expected SEM102, was: " + diags);
        assertFalse(diags.contains("ClassFormatError"), "Must be a compile diagnostic, not a crash: " + diags);
    }

    @Test
    void validIoMethodsStillCompile(@TempDir Path tempDir) throws IOException {
        // control: the whole live table keeps compiling — the guard must not
        // reject a legitimate member (exists/delete/createDirectories/mkdir/path).
        // `path` is a METHOD (`File("x").path()`); the field form was a
        // false-green (it compiled clean but emitted `getfield kof/io/File.path`
        // against a class absent from the runtime → NoClassDefFoundError at
        // load; now SEM102, §491).
        CompilationResult result = compile("""
            main() {
                var f = File("/tmp/opencode/io_guard_d.txt")
                println(f.exists())
                var d = Directory("/tmp/opencode/io_guard_d")
                println(d.createDirectories())
                println(d.mkdir())
                println(f.path())
            }
            """, tempDir);
        assertTrue(result.success(), "Valid io members must compile: "
                + result.diagnostics().getDiagnostics());
    }
}
