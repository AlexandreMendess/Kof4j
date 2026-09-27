package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #471 (PR #480) — {@code as Byte}/{@code as Short} chamavam
 * {@code Byte.valueOf:(B)} sem emitir {@code i2b}/{@code i2s} antes: o int cru
 * na pilha era lido como byte/signado e valores fora de -128..127 (ou
 * -32768..32767) estouravam {@code ArrayIndexOutOfBoundsException} no cache do
 * {@code Byte.valueOf} (R6: crash em runtime em vez de truncamento Java).
 * Fix: unary {@code I2B}/{@code I2S} no IR + emissão nos 4 alvos
 * (JvmOpEmitter, KofInterpreterOps, JsCallEmitter Int8Array/Int16Array,
 * constant-fold). Oracle = semântica de narrowing do JDK (Q3 edges:
 * estouro ±, limites exatos, fold de constante e não-constante).
 */
class ByteShortNarrowE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private static final String SOURCE = """
            main() {
                println(300 as Byte)
                println((0 - 300) as Byte)
                println(127 as Byte)
                println(128 as Byte)
                var n = 200
                println(n as Byte)
                println(40000 as Short)
                println((0 - 40000) as Short)
                println(32767 as Short)
                println(32768 as Short)
                var m = 70000
                println(m as Short)
            }
            """;

    private static final String EXPECTED = "44\n-44\n127\n-128\n-56\n-25536\n25536\n32767\n-32768\n4464";

    @Test
    void byteShortNarrowTruncatesLikeJvmOracle(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("bs.kf");
        Files.writeString(src, SOURCE);
        Path out = tempDir.resolve("bs-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "compilar: " + r.diagnostics().getDiagnostics());
        assertEquals(EXPECTED, runJvm(out), "#471: narrowing sem i2b/i2s estourava o cache do Byte.valueOf");
    }

    @Test
    void byteShortNarrowSameOnScriptAndJs(@TempDir Path tempDir) throws IOException, InterruptedException {
        Path src = tempDir.resolve("bsj.kf");
        Files.writeString(src, SOURCE);
        KofInterpreter.Result i = driver.interpret(java.util.List.of(src), src.getParent(), new String[0]);
        assertEquals(0, i.exitCode(), "Script exit/stderr: " + i.stdout() + " " + i.stderr());
        assertEquals(EXPECTED, i.stdout().trim(), "Script");
        Path jsOut = tempDir.resolve("bsj-js");
        CompilationResult js = driver.compile(src, jsOut, Target.JS);
        assertTrue(js.success(), "JS compile: " + js.diagnostics().getDiagnostics());
        ProcessBuilder jb = new ProcessBuilder(TestJdk.onPath("node"), jsOut.resolve("Default.mjs").toString());
        jb.redirectErrorStream(true);
        Process jp = jb.start();
        String jout = new String(jp.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
            .replace("\r\n", "\n").trim();
        assertEquals(0, jp.waitFor(), "JS exit, output: " + jout);
        assertEquals(EXPECTED, jout, "JS");
    }
}
