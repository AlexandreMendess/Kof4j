package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof.buffer / nominal {@code Buffer(U8)} (D-R3-BUFFER, maintainer 21/09).
 * Incremental slice (R6-SCOPE): {@code buffer.alloc(Int)} + {@code Buffer.bytes()}
 * on the JVM and (21/09) on the JS target; Native stays an honest gap (never a
 * silent stub).
 */
class BufferE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void allocAndBytesJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("buf.kf");
        Files.writeString(src, """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf"), Target.JVM);
        assertTrue(r.success(), "buffer.alloc must bind on JVM: " + r.diagnostics().getDiagnostics());
        assertEquals("Buffer[4]\n[0, 0, 0, 0]", runJvm(dir.resolve("out-buf")),
                "alloc(4) is zero-filled and 4 bytes long");
    }

    @Test
    void allocZeroAndNegativeClampJvm(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("buf0.kf");
        Files.writeString(src, """
                main() {
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-buf0"), Target.JVM);
        assertTrue(r.success(), "edge sizes must compile: " + r.diagnostics().getDiagnostics());
        assertEquals("Buffer[0]\nBuffer[0]", runJvm(dir.resolve("out-buf0")),
                "0 stays 0; negative clamps to 0 (no crash, no huge alloc)");
    }

    @Test
    void allocNativeStaysFfi001(@TempDir Path dir) throws IOException {
        Path src = dir.resolve("bufnat.kf");
        Files.writeString(src, """
                main() {
                    println(buffer.alloc(4))
                }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-bufnat"), Target.NATIVE);
        assertFalse(r.success(), "Native buffer ABI is a later slice — must stay unbound");
        assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "expected FFI001 on Native, got: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void allocAndBytesJsParity(@TempDir Path dir) throws IOException {
        // kof.buffer no JS (D-R3-BUFFER, 21/09): mesmo contrato do JVM —
        // zero-filled, clamp de tamanho, bytes() materializa Byte[].
        String kof = """
                main() {
                    val b = buffer.alloc(4)
                    println(b)
                    println(b.bytes())
                    println(buffer.alloc(0))
                    println(buffer.alloc(-3))
                }
                """;
        Path jvmSrc = dir.resolve("bufjs-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufjs-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-bufjs-jvm"));
        assertEquals("Buffer[4]\n[0, 0, 0, 0]\nBuffer[0]\nBuffer[0]", jvm, "JVM golden");

        Path jsSrc = dir.resolve("bufjs-js.kf");
        Files.writeString(jsSrc, kof);
        CompilationResult rjs = driver.compile(jsSrc, dir.resolve("out-bufjs-js"), Target.JS);
        assertTrue(rjs.success(), "buffer.alloc must bind on JS (21/09): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(dir.resolve("out-bufjs-js"));
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity (kof.buffer)");
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "-cp", outDir.toString(),
                    "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
