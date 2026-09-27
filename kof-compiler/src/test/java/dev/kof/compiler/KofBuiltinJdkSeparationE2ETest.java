package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §240 — regressão do `8935c8a7`: `ExternalClasspath.knows()` passou a devolver
 * true para TODO `java/*`, então `SemanticAnalyzer.isExternal` classificava
 * `String` e as exceções como externas e contornava o registro de builtins do
 * Kof (39 reds: `String.indexOf`→SEM025, `throw RuntimeException`→SEM026,
 * `String.repeat` aceito indevidamente).
 *
 * <p>Trava as DUAS direções da separação:
 * <ul>
 *   <li>(A) builtin: `String` continua resolvida pelo registro do Kof —
 *       `repeat` é REJEITADO (SEM052), `indexOf(Char)` dá SEM051 (não SEM025),
 *       `throw`/`catch` de exceção tipada compilam e rodam;</li>
 *   <li>(B) interop: classes JDK que NÃO são builtin (`StringBuilder`,
 *       `String.join`) continuam resolvidas por reflexão com o descritor real
 *       — o motivo do `knows()` ampliado.</li>
 * </ul>
 */
class KofBuiltinJdkSeparationE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path tempDir, String source, String expected) throws IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
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
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            assertEquals(expected, output, "JVM output");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // ---- (A) builtin: o registro do Kof continua vencendo para String ----

    @Test
    void stringRepeatStillRejected(@TempDir Path tmp) throws IOException {
        // `String.repeat` é função de `strings.*` em Kof (não método de
        // instância) — o registry builtin REJEITA. Antes do fix passava como
        // "método externo do JDK".
        CompilationResult r = driver.compile(
                write(tmp, "main() { println(\"ab\".repeat(3)) }"),
                tmp.resolve("out-repeat"), Target.JVM);
        assertFalse(r.success(), "String.repeat deve ser rejeitado");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM052".equals(d.code())),
                "esperava SEM052, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void stringIndexOfCharKeepsSem051NotSem025(@TempDir Path tmp) throws IOException {
        // O contrato é SEM051 (argumento de tipo errado), NÃO SEM025
        // ("Cannot resolve method") — a resolução é a do builtin.
        CompilationResult r = driver.compile(
                write(tmp, "main() { println(\"abc\".indexOf('c')) }"),
                tmp.resolve("out-indexof"), Target.JVM);
        assertFalse(r.success(), "indexOf(Char) deve ser rejeitado");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM051".equals(d.code())),
                "esperava SEM051, foi: " + r.diagnostics().getDiagnostics());
        assertFalse(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM025".equals(d.code())),
                "SEM025 é o sintoma da regressão §240, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void typedExceptionThrowAndCatchStillCompile(@TempDir Path tmp) throws IOException {
        // `throw <objeto de exceção>` é builtin do Kof (exceções são Strings,
        // mas o tipo java.lang.* é conhecido) — antes do fix dava SEM026.
        runJvm(tmp, """
                main() {
                    try {
                        throw new java.lang.RuntimeException("boom")
                    } catch (java.lang.RuntimeException e) {
                        println("caught: " + e.getMessage())
                    }
                    try {
                        throw new java.io.IOException("io")
                    } catch (Exception e) {
                        println("any: " + e.getMessage())
                    }
                }
                """, "caught: boom\nany: io");
    }

    // ---- (B) interop: JDK não-builtin continua resolvido por reflexão ----

    @Test
    void jdkNonBuiltinInteropStillResolves(@TempDir Path tmp) throws IOException {
        // StringBuilder.append (retorno concreto) + String.join (param formal
        // Iterable + retorno String) — a razão do `knows()` ampliado. Ambos
        // devem continuar funcionando DEPOIS da separação.
        runJvm(tmp, """
                main() {
                    var sb = new java.lang.StringBuilder()
                    sb.append("hello").append(" ").append("world")
                    println(sb.toString())
                    val parts = listOf("a", "b", "c")
                    println(String.join(", ", parts))
                }
                """, "hello world\na, b, c");
    }

    private Path write(Path tmp, String source) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, source);
        return f;
    }
}
