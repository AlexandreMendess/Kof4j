package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FFI out-buffer (D6-3 / D-R3-BUFFER, slice B2 + #651 fatia A2): the nominal
 * {@code Buffer(U8)} crosses an {@code extern} as an INOUT pointer — copy-in /
 * call / copy-back on the JVM and (21/09) on the JS target. On Native x86-64
 * (fatia A2) the Kof Buffer payload is contiguous memory, so the C function
 * receives the payload address {@code obj+24} directly (the C write is already
 * the copy-back). Proven with a real C shim and the JVM oracle; riscv64/aarch64
 * keep the honest {@code FFI001} gap (R6).
 */
class BufferFfiE2ETest {

    private static final String C_SRC = """
            int bump(unsigned char* buf, int n) {
                int s = 0;
                for (int i = 0; i < n; i++) { buf[i] = buf[i] + 10; s += buf[i]; }
                return s;
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void bufferInoutCopyInCopyBackJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("bufinout.kf");
        Files.writeString(src, """
                extern "%s" bump(Buffer(U8) buf, Int n): Int

                main() {
                    var b = buffer.alloc(2)
                    println(bump(b, 2))
                    println(b.bytes())
                    println(bump(b, 2))
                    println(b.bytes())
                }
                """.formatted(so));

        Path out = dir.resolve("out-jvm-bufinout");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM Buffer(U8) extern param must bind (B2): "
                + r.diagnostics().getDiagnostics());
        assertEquals("20\n[10, 10]\n40\n[20, 20]", runJvm(out),
                "copy-in reads the buffer, copy-back writes it: 10s accumulate on the 2nd call");
    }

    @Test
    void bareBufferSpellingBindsJvm(@TempDir Path dir) throws Exception {
        String so = compileHostLib(dir);
        Path src = dir.resolve("bufbare.kf");
        Files.writeString(src, """
                extern "%s" bump(Buffer buf, Int n): Int

                main() {
                    var b = buffer.alloc(1)
                    println(bump(b, 1))
                }
                """.formatted(so));

        Path out = dir.resolve("out-jvm-bufbare");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "bare `Buffer` is accepted as Buffer(U8): "
                + r.diagnostics().getDiagnostics());
        assertEquals("10", runJvm(out));
    }

    @Test
    void bufferInoutCopyInCopyBackNativeParity(@TempDir Path dir) throws Exception {
        // D6-3 fatia A2: no Native x86-64 o Buffer Kof é memória contígua, então
        // o extern recebe o payload (obj+24) direto — a escrita da C JÁ é o
        // copy-back (sem arena intermediária). O MESMO fonte roda no JVM (FFM,
        // copy-in/copy-back) e a saída é byte-a-byte igual (oráculo regra 5).
        String so = compileHostLib(dir);
        String kof = """
                extern "%s" bump(Buffer(U8) buf, Int n): Int

                main() {
                    var b = buffer.alloc(2)
                    println(bump(b, 2))
                    println(b.bytes())
                    println(bump(b, 2))
                    println(b.bytes())
                }
                """.formatted(so);
        String expected = "20\n[10, 10]\n40\n[20, 20]";

        assertEquals(expected, runNative(dir, kof), "Native x86-64 Buffer INOUT golden");

        Path jvmSrc = dir.resolve("bufinout-native-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufinout-native-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        assertEquals(expected, runJvm(dir.resolve("out-bufinout-native-jvm")), "JVM golden (buffer INOUT)");
    }

    @Test
    void bufferParamCrossStaysFfi001(@TempDir Path dir) throws IOException {
        // A fatia A2 abre só o x86-64; riscv64/aarch64 seguem FFI001 honesto (R6).
        for (Target t : new Target[] {Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            Path src = dir.resolve("bufnat-" + t + ".kf");
            Files.writeString(src, """
                    extern "libc.so.6" f(Buffer(U8) buf, Int n): Int

                    main() {
                        println("hi")
                    }
                    """);
            CompilationResult r = driver.compile(src, dir.resolve("out-buf-fnat-" + t), t);
            assertFalse(r.success(), "cross buffer ABI stays unbound on " + t);
            assertTrue(r.diagnostics().getDiagnostics().toString().contains("FFI001"),
                    "expected FFI001 on " + t + ", got: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void bufferInoutCopyInCopyBackJsParity(@TempDir Path dir) throws Exception {
        // D6-3 no JS (bridge 21/09): `Buffer(U8)` INOUT — o Marshal copia os bytes
        // do `Uint8Array` do guest para a arena, chama, e devolve o resultado via
        // copy-back. Mesma semântica do JVM (acumula +10 a cada chamada).
        String so = compileHostLib(dir);
        String kof = """
                extern "%s" bump(Buffer(U8) buf, Int n): Int

                main() {
                    var b = buffer.alloc(2)
                    println(bump(b, 2))
                    println(b.bytes())
                    println(bump(b, 2))
                    println(b.bytes())
                }
                """.formatted(so);
        String expected = "20\n[10, 10]\n40\n[20, 20]";

        Path jvmSrc = dir.resolve("bufinout-jvm.kf");
        Files.writeString(jvmSrc, kof);
        CompilationResult rj = driver.compile(jvmSrc, dir.resolve("out-bufinout-jvm"), Target.JVM);
        assertTrue(rj.success(), "JVM compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(dir.resolve("out-bufinout-jvm"));
        assertEquals(expected, jvm, "JVM golden (buffer INOUT)");

        Path jsSrc = dir.resolve("bufinout-js.kf");
        Files.writeString(jsSrc, kof);
        CompilationResult rjs = driver.compile(jsSrc, dir.resolve("out-bufinout-js"), Target.JS);
        assertTrue(rjs.success(), "JS Buffer INOUT must bind (bridge 21/09): "
                + rjs.diagnostics().getDiagnostics());
        String js = runJs(dir.resolve("out-bufinout-js"));
        assertEquals(expected, js, "JS golden (buffer INOUT, copy-back)");
        assertEquals(jvm, js, "JVM==JS byte-for-byte parity (buffer INOUT)");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String compileHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "buffer host lib usa um .so nativo (Linux)");
        Path c = dir.resolve("libkofbuf.c");
        Files.writeString(c, C_SRC);
        Path so = dir.resolve("libkofbuf.so");
        String cc = firstPresent("/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc");
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de buffer");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), c.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou ao compilar o host de buffer: " + out);
        return so.toString();
    }

    private static String firstPresent(String... candidates) {
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) return c;
            } catch (Exception ignored) {
                // tenta o próximo candidato
            }
        }
        return null;
    }

    private String runNative(Path dir, String kof) throws IOException {
        Path src = dir.resolve("bufinout-native.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-bufinout-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE Buffer(U8) extern must bind (A2): "
                + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " must exist");
        try {
            Process p = new ProcessBuilder(bin.toString()).directory(dir.toFile())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "NATIVE run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
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
                    "--enable-native-access=ALL-UNNAMED",
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
