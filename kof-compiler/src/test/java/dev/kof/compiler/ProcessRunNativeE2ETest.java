package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-FULL-PARITY-050 — linha 1 do ledger: {@code process.run} no Native x86-64
 * ({@code RuntimeProcess}) com golden vs o oráculo JVM (subprocesso vs
 * subprocesso — os dois binários reais, a mesma fonte Kof).
 *
 * <p>Q3: happy path (PATH + args), exit code ≠ 0, separação stdout/stderr,
 * stdout grande (drain sem deadlock — a JVM lê em threads, o nativo com
 * poll), stderr grande (buffer crescente) e exec falho (exitCode -1 com
 * mensagem no stderr — contrato JVM da exceção; o TEXTO da mensagem é a
 * única diferença documentada: "Cannot run program" vs "process: exec failed").
 */
class ProcessRunNativeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private record Run(int exit, String out, String err) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM deve compilar: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(false).start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String e = new String(p.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor(), o, e);
    }

    private Run runNative(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), "Native deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binário nativo deve existir");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(false).start();
        String o = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        String e = new String(p.getErrorStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        return new Run(p.waitFor(), o, e);
    }

    private void assertParity(String source, String expectedStdout) throws Exception {
        Path jvmSrc = tmp.resolve("jvm.kf");
        Files.writeString(jvmSrc, source);
        Run jvm = runJvm(jvmSrc, tmp.resolve("out-jvm"));
        Path natSrc = tmp.resolve("nat.kf");
        Files.writeString(natSrc, source);
        Run nat = runNative(natSrc, tmp.resolve("out-nat"));
        assertEquals(expectedStdout, jvm.out(), "JVM stdout inesperado");
        assertEquals(jvm.out(), nat.out(), "paridade JVM≡Native quebrou no stdout");
        assertEquals(jvm.exit(), nat.exit(), "paridade JVM≡Native quebrou no exit code");
    }

    @TempDir Path tmp;

    @Test
    void runEchoPathResolutionAndArgs() throws Exception {
        assertParity("""
            main() {
                val r = process.run("echo", "vivo")
                println(r.stdout)
                println(r.exitCode)
            }
            """, "vivo\n\n0\n");
    }

    @Test
    void runNonZeroExitCode() throws Exception {
        assertParity("""
            main() {
                val r = process.run("sh", "-c", "echo antes; exit 3")
                println(r.stdout)
                println(r.exitCode)
            }
            """, "antes\n\n3\n");
    }

    @Test
    void runStderrSeparateFromStdout() throws Exception {
        // redirectErrorStream(false) nos dois lados: o stderr do filho vai
        // para r.stderr (capturado), NAO para o stderr do processo Kof.
        assertParity("""
            main() {
                val r = process.run("sh", "-c", "echo fora >&2; echo dentro")
                println(r.stdout)
                println("|" + r.stderr + "|")
            }
            """, "dentro\n\n|fora\n|\n");
    }

    @Test
    void runLargeStdoutDrainsWithoutDeadlock() throws Exception {
        // 25KB > 4096 (buffer de leitura) e > pipe buffer (64KB? 5000 linhas
        // x ~5B = ~29KB): o filho BLOQUEIA escrevendo enquanto o pai lê —
        // o drain concorrente (poll) é o que prova a ausência de deadlock.
        assertParity("""
            main() {
                val r = process.run("sh", "-c", "seq 1 5000")
                println(r.stdout.contains("5000"))
                println(r.stdout.length)
                println("|" + r.stdout.substring(23888) + "|")
                println(r.exitCode)
            }
            """, "true\n23893\n|5000\n|\n0\n");
    }

    @Test
    void runLargeStderrGrowsBuffer() throws Exception {
        assertParity("""
            main() {
                val r = process.run("sh", "-c", "seq 1 3000 >&2; echo ok")
                println(r.stdout)
                println(r.stderr.contains("3000"))
                println(r.stderr.length)
            }
            """, "ok\n\ntrue\n13893\n");
    }

    @Test
    void runMissingProgramIsNegativeOneWithMessage() throws Exception {
        String src = """
            main() {
                val r = process.run("no-such-binary-9f3c7a")
                println(r.exitCode)
                println(r.stdout == "")
                println(r.stderr != "")
            }
            """;
        Path jvmSrc = tmp.resolve("jvm.kf");
        Files.writeString(jvmSrc, src);
        Run jvm = runJvm(jvmSrc, tmp.resolve("out-jvm"));
        Path natSrc = tmp.resolve("nat.kf");
        Files.writeString(natSrc, src);
        Run nat = runNative(natSrc, tmp.resolve("out-nat"));
        assertEquals(jvm.out(), nat.out(), "paridade no contrato de exec falho (-1, vazio, msg)");
    }
}
