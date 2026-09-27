package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §218/#148 — formatadores numéricos de receiver PRIMITIVO
 * (n.toHexString()/toBinaryString()).
 *
 * Antes: o lowering de instance call não conhecia os formatadores e caía no
 * fallback de método-resolvido ausente com owner "" no Methodref
 * (`invokevirtual "".toHexString:()Ljava/lang/Object;`) →
 * `ClassFormatError: Illegal class name ""` no load da classe (a classe nunca
 * carrega; o erro não tem stack Kof). Fix: rotear para os estáticos JDK reais
 * `java/lang/Integer|Long.toHexString|toBinaryString` (mesma superfície do
 * §89 toInt); JS mapeia para toString(radix) com máscara unsigned
 * (>>> 0 / BigInt.asUintN(64)) — o JDK é unsigned de 32/64 bits.
 *
 * Q3 coberto: positivo (255→ff), negativo (-42→ffffffd6, JDK unsigned),
 * zero, Long (4294967296, -1→ffffffffffffffff), toBinaryString, não-Int/Long
 * (Double → SEM052, nunca owner vazio), paridade JVM×JS (mesma saída).
 */
class NumericFormatterE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(TestJdk.javaBin(), "-cp", outDir.toString(), "Default.Main");
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

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int exitCode = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, exitCode, "JS exit code, output: " + out);
        return out.toString().trim();
    }

    @Test
    void intFormattersJvmAndJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("int_hex.kf");
        Files.writeString(src, """
                main() {
                    var n = 255
                    println(n.toHexString())
                    println(n.toBinaryString())
                    var neg = -42
                    println(neg.toHexString())
                    println(neg.toBinaryString())
                    var z = 0
                    println(z.toHexString())
                    println(z.toBinaryString())
                }
                """);
        Path outJvm = tempDir.resolve("int_hex-jvm");
        Path outJs = tempDir.resolve("int_hex-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        // golden: JDK Integer.toHexString/toBinaryString são UNSIGNED de 32
        // bits (medidos: 255→ff/11111111; -42→ffffffd6/32 bits de 2; 0→0/0)
        String expected = "ff\n11111111\nffffffd6\n11111111111111111111111111010110\n0\n0";
        assertEquals(expected, runJvm(outJvm), "Int formatters JVM output mismatch");
        assertEquals(expected, runJs(outJs), "Int formatters JS output mismatch");
    }

    @Test
    void longFormattersJvmAndJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("long_hex.kf");
        Files.writeString(src, """
                main() {
                    var big: Long = 4294967296
                    println(big.toHexString())
                    println(big.toBinaryString())
                    var m1: Long = -1
                    println(m1.toHexString())
                }
                """);
        Path outJvm = tempDir.resolve("long_hex-jvm");
        Path outJs = tempDir.resolve("long_hex-js");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertTrue(rjvm.success(), "JVM compile failed: " + rjvm.diagnostics().getDiagnostics());
        CompilationResult rjs = driver.compile(src, outJs, Target.JS);
        assertTrue(rjs.success(), "JS compile failed: " + rjs.diagnostics().getDiagnostics());
        // golden: Long unsigned de 64 bits (medidos: 2^32→100000000; -1→64 f)
        String expected = "100000000\n100000000000000000000000000000000\nffffffffffffffff";
        assertEquals(expected, runJvm(outJvm), "Long formatters JVM output mismatch");
        assertEquals(expected, runJs(outJs), "Long formatters JS output mismatch");
    }

    @Test
    void nonIntegralReceiverGetsSem052(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("dbl_hex.kf");
        Files.writeString(src, """
                main() {
                    var d = 3.14
                    println(d.toHexString())
                }
                """);
        Path outJvm = tempDir.resolve("dbl_hex-jvm");
        CompilationResult rjvm = driver.compile(src, outJvm, Target.JVM);
        assertFalse(rjvm.success(),
                "Double receiver NÃO compila (SEM052) — nunca owner vazio/ClassFormatError");
        assertTrue(rjvm.diagnostics().getDiagnostics().stream().anyMatch(dg ->
                        "SEM052".equals(dg.code())),
                "diagnóstico deve ser SEM052: " + rjvm.diagnostics().getDiagnostics());
    }
}
