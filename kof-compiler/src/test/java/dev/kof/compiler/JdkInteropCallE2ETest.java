package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #237 & #231 — Interop method call descriptor resolution for JDK classes:
 * - #237 (§234): String.join with concrete collection type (List<String>)
 *   matching interface parameter (Iterable) and String return type.
 * - #231 (§224): StringBuilder.append returning concrete StringBuilder
 *   instead of erased Object.
 */
class JdkInteropCallE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @SuppressWarnings("ProcessBuilderCommandInjection")
    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            // Use reflection runner as documented in AGENTS.md to bypass JavaFX launcher swallowing
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder("javac", "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            String javaCmd = System.getProperty("java.home") + "/bin/java";
            Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void stringJoinWithListJvm(@TempDir Path tmp) throws Exception {
        // #237 minimal reproduction
        runJvm(tmp, """
                main() {
                    var parts = new List<String>()
                    parts.add("a")
                    parts.add("b")
                    parts.add("c")
                    println(String.join(", ", parts))
                }
                """, "a, b, c");
    }

    @Test
    void stringJoinListOfJvm(@TempDir Path tmp) throws Exception {
        // Control: listOf literal
        runJvm(tmp, """
                main() {
                    val parts = listOf("alpha", "beta", "gamma")
                    println(String.join(" - ", parts))
                }
                """, "alpha - beta - gamma");
    }

    @Test
    void stringBuilderAppendAndChainJvm(@TempDir Path tmp) throws Exception {
        // #231 minimal reproduction & chained calls
        runJvm(tmp, """
                main() {
                    var sb = new java.lang.StringBuilder()
                    sb.append("hello")
                    sb.append(" ")
                    sb.append("world")
                    println(sb.toString())
                }
                """, "hello world");
    }

    @Test
    void stringBuilderAppendPrimitivesJvm(@TempDir Path tmp) throws Exception {
        // #231 variant with primitive overloads (int, boolean, double)
        runJvm(tmp, """
                main() {
                    var sb = new java.lang.StringBuilder()
                    sb.append("count: ")
                    sb.append(42)
                    sb.append(", ok: ")
                    sb.append(true)
                    println(sb.toString())
                }
                """, "count: 42, ok: true");
    }

    @Test
    void stringJoinEmptyListAndDelimiterJvm(@TempDir Path tmp) throws Exception {
        // Q3 boundary: empty list, empty delimiter
        runJvm(tmp, """
                main() {
                    val empty = listOf<String>()
                    println("[" + String.join(",", empty) + "]")
                    val single = listOf("one")
                    println(String.join("", single))
                }
                """, "[]\none");
    }

    @Test
    void stringBuilderChainedMethodCallsJvm(@TempDir Path tmp) throws Exception {
        // Q3: chained return value dispatching next method call on returned StringBuilder
        runJvm(tmp, """
                main() {
                    var sb = new java.lang.StringBuilder()
                    sb.append("A").append("B").append("C")
                    println(sb.toString())
                }
                """, "ABC");
    }
}
