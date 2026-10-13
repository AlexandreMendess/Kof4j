package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** LANGUAGE EVOLUTION (the maintainer's 13/10 directive: "liberado evoluir
 *  a lang") — `for (var c in s)`: the for-in accepts String, the loop
 *  variable is Char (the charAt unit). Pre-fix this was SEM058 (bug 145:
 *  the backends DIVERGED — JVM VerifyError, native SIGSEGV, Script error,
 *  JS iterated chars silently). Now every target takes the SAME index-desugar
 *  through the standard `length`/`charAt` instance calls — no new runtime
 *  function. The non-iterable types keep SEM058. */
class ForInStringE2ETest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(String tag, String body) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() {\n" + body + "\n}");
        Path outDir = tmp.resolve("out-" + tag);
        CompilationResult r = driver.compile(file, outDir, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8",
                "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "JVM run: " + o);
        return o;
    }

    private String runScript(String tag, String body) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() {\n" + body + "\n}");
        KofInterpreter.Result i = driver.interpret(java.util.List.of(file),
                file.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), () -> "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        return i.stdout().trim();
    }

    private String runJs(String tag, String body) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "main() {\n" + body + "\n}");
        Path jsOut = tmp.resolve("js-" + tag);
        CompilationResult r = driver.compile(file, jsOut, Target.JS);
        assertTrue(r.success(), () -> "JS compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(TestJdk.onPath("node"),
                jsOut.resolve("Default.mjs").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "JS exit, output: " + o);
        return o;
    }

    private static final String COUNT = """
            var n = 0
            for (var c in "abc") {
                n = n + 1
            }
            println(n)
            """;

    @Test
    void forInStringCountsTheCharsOnJvm() throws Exception {
        assertEquals("3", runJvm("count", COUNT), "o for-in sobre String itera os chars");
    }

    @Test
    void forInStringMatchesJvmOnScriptAndJs() throws Exception {
        assertEquals("3", runScript("counts", COUNT), "Script ≡ JVM");
        assertEquals("3", runJs("countj", COUNT), "JS ≡ JVM (o ramo que já iterava — agora oficial)");
    }

    @Test
    void theLoopVariableIsChar() throws Exception {
        String out = runJvm("ischar", """
                var first = ' '
                for (var c in "Kof") {
                    if (first == ' ') { first = c }
                }
                println(first)
                println(first == 'K')
                """);
        assertEquals("K\ntrue", out, "a variável do loop é Char (compara com 'K')");
    }

    @Test
    void nonIterableTypesKeepSem058() throws Exception {
        Path file = tmp.resolve("sem58/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, """
                main() {
                    for (var x in 42) {
                        println(x)
                    }
                }
                """);
        CompilationResult r = driver.compile(file, tmp.resolve("out-sem58"), Target.JVM);
        assertFalse(r.success(), "Int não é iterável");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> String.valueOf(d).contains("SEM058")),
                "o SEM058 fica para os não-iteráveis: " + r.diagnostics().getDiagnostics());
    }
}
