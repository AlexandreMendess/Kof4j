package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Regressão do null-safety `Type?` + narrowing (`if (x != null)`).
 *
 * 02/09: antes do fix o narrowing compilava mas o JVM emitia
 * `getfield "?".length` (owner "?" inválido) para `String?.length` →
 * erro de launcher/verificação. O idioma documentado no corpus
 * (`training/idioms/strings.md`, `errors.md`) agora roda de verdade.
 */
class NullSafetyE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void nullablePropertyAccessJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String? maybe() {
                return "kof"
            }
            main() {
                var s = maybe()
                if (s != null) {
                    println(s.length)
                    println(s.substring(0, 2))
                }
                println("done")
            }
            """, "3\nko\ndone");
    }

    @Test
    void nullableNullBranchJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String? maybe(Bool yes) {
                if (yes) return "abc"
                return null
            }
            main() {
                var s = maybe(false)
                if (s != null) {
                    println("non-null")
                } else {
                    println("null")
                }
                var t = maybe(true)
                if (t != null) {
                    println(t.length)
                }
                println("done")
            }
            """, "null\n3\ndone");
    }

    @Test
    void nullableParamPassingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            String shout(String s) {
                return s.toUpperCase()
            }
            main() {
                String? name = "mel"
                if (name != null) {
                    println(shout(name))
                }
                println("done")
            }
            """, "MEL\ndone");
    }

    @Test
    void nullableMapGetNarrowingNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            main() {
                var m = mapOf("k", "v")
                var v = m.get("k")
                if (v != null) {
                    println(v.length)
                } else {
                    println("null")
                }
                println("done")
            }
            """, "1\ndone");
    }

    @Test
    void nullableReadTextNarrowingJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
            main() {
                var t = readFile("/definitely/missing/file.txt")
                if (t != null) {
                    println("content")
                } else {
                    println("missing")
                }
                println("done")
            }
            """, "missing\ndone");
    }

    @Test
    void readLineEofIsNullJvm(@TempDir Path tmp) throws Exception {
        runJvmEmptyStdin(tmp, """
            main() {
                var line = readLine()
                if (line == null) {
                    println("eof")
                } else {
                    println("got:" + line)
                }
                println("done")
            }
            """, "eof\ndone");
    }

    @Test
    void readLineEofIsNullNative(@TempDir Path tmp) throws Exception {
        runNativeEmptyStdin(tmp, """
            main() {
                var line = readLine()
                if (line == null) {
                    println("eof")
                } else {
                    println("got:" + line)
                }
                println("done")
            }
            """, "eof\ndone");
    }

    @Test
    void nullableWhileNarrowingJvm(@TempDir Path tmp) throws Exception {
        // D-NARROW-WHILE (#159): `while (s != null)` narrows `s` in the body
        // (before the fix: SEM049 "receiver is nullable"), and reassigning a
        // nullable value inside the narrowed body keeps the DECLARATION's
        // nullability (before the fix: SEM012 false-positive).
        runJvm(tmp, """
            String? next(Int i) {
                if (i < 2) return "v" + i
                return null
            }
            main() {
                var i = 0
                var s = next(i)
                while (s != null) {
                    println(s.length)
                    i = i + 1
                    s = next(i)
                }
                println("done")
            }
            """, "2\n2\ndone");
    }

    @Test
    void nullableWhileNarrowingNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            String? next(Int i) {
                if (i < 2) return "v" + i
                return null
            }
            main() {
                var i = 0
                var s = next(i)
                while (s != null) {
                    println(s.length)
                    i = i + 1
                    s = next(i)
                }
                println("done")
            }
            """, "2\n2\ndone");
    }

    @Test
    void nullableFieldNarrowingInWhileJvm(@TempDir Path tmp) throws Exception {
        // D-NARROW-WHILE (#159) 2nd face: `while (b.data != null)` narrows the
        // FIELD on the receiver (before: SEM049 on `b.data.length`).
        runJvm(tmp, """
            class Box {
                String? data
                public constructor(String? data) {
                    this.data = data
                }
            }
            String? next(Int i) {
                if (i < 2) return "v" + i
                return null
            }
            main() {
                var i = 0
                var b = Box(next(i))
                while (b.data != null) {
                    println(b.data.length)
                    i = i + 1
                    b.data = next(i)
                }
                println("done")
            }
            """, "2\n2\ndone");
    }

    @Test
    void nullableFieldNarrowingInWhileNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
            class Box {
                String? data
                public constructor(String? data) {
                    this.data = data
                }
            }
            String? next(Int i) {
                if (i < 2) return "v" + i
                return null
            }
            main() {
                var i = 0
                var b = Box(next(i))
                while (b.data != null) {
                    println(b.data.length)
                    i = i + 1
                    b.data = next(i)
                }
                println("done")
            }
            """, "2\n2\ndone");
    }

    @Test
    void reassignmentInsideNarrowedIfStaysGreen(@TempDir Path tmp) throws Exception {
        // Regression guard (D-NARROW-WHILE): the narrowed local must keep the
        // DECLARATION's nullability for writes — `s = next(3)` inside
        // `if (s != null)` is valid and must not raise SEM012.
        runJvm(tmp, """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s != null) {
                    s = next(3)
                    println(s.length)
                }
                println("done")
            }
            """, "2\ndone");
    }

    @Test
    void earlyReturnNarrowsLocalsJvm(@TempDir Path tmp) throws Exception {
        // §353 revealed: the SAME SG-005 narrowing in early-return shape —
        // `if (x == null) { return/throw }` then `x.length` is the null-false
        // flow; must typecheck and run.
        runJvm(tmp, """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s == null) { return }
                println(s.length)
                var t = next(9)
                if (t != null) { println("nao") }
                if (t == null) { println("sim"); return }
                println(t.length)
            }
            """, "2\nsim");
    }

    @Test
    void earlyExitGuardRequired(@TempDir Path tmp) throws Exception {
        // The negative twin: without a guaranteed exit the null can still
        // fall through — SEM049 must keep firing (no false acceptance).
        Path file = tmp.resolve("Neg.kf");
        Files.writeString(file, """
            String? next(Int i) {
                if (i < 5) return "v" + i
                return null
            }
            main() {
                var s = next(0)
                if (s == null) { println("nil") }
                println(s.length)
            }
            """);
        CompilationResult result = driver.compile(file, tmp.resolve("out"), Target.JVM);
        assertFalse(result.success(), "SEM049 esperado: fluxo pode continuar com null");
        String diags = String.valueOf(result.diagnostics().getDiagnostics());
        assertTrue(diags.contains("SEM049"), () -> "esperado SEM049 em: " + diags);
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJvmEmptyStdin(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            p.getOutputStream().close();   // EOF no stdin do filho
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNativeEmptyStdin(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            p.getOutputStream().close();   // EOF no stdin do filho
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native compile failed: " + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }
}