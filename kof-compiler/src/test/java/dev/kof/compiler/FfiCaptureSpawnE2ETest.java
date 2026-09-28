package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Fase 5 / unidade 1 (#666, D-MEMORY-SAFETY): a interseção FFI x spawn/captura
 * nunca fora exercitada — medido 28/09 (zero `spawn` em FfiE2ETest/BufferFfiE2ETest;
 * MemorySafetyE2ETest sem extern; SpawnE2ETest sem ffi). Goldens SÃO O QUE A ÁRVORE
 * PRODUZ (sonda 28/09: JVM/JS/Native = 3.0/5; MEM021 x extern = ERROR), não promessa.
 *
 * Faces NÃO pinadas de propósito (escaladas, regra 6): Script x extern morre em
 * runtime com `KofRuntime.kof_ffi/4` sem diagnóstico (#667); duas escritas FFI no
 * MESMO Buffer via spawn sem sync compilam clean sem MEM020 (#668). Um green
 * silencioso dessas faces engessaria o gap como contrato.
 */
class FfiCaptureSpawnE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase().contains("linux");
    }

    // FFI dentro da task spawn, lendo captura read-only (por valor, sem caixa).
    private static final String FFI_SPAWN_READ = """
            extern "libm.so.6" sqrt(Double x): Double

            main() {
                var v = 9.0
                var h = spawn { return sqrt(v) }
                println(await h)
            }
            """;

    // FFI dentro da task spawn sem nenhuma captura.
    private static final String FFI_SPAWN_NO_CAPTURE = """
            extern "libc.so.6" abs(Int x): Int

            main() {
                var h = spawn { return abs(-5) }
                println(await h)
            }
            """;

    private String runJvm(Path outDir) throws IOException {
        try {
            String javaHome = System.getProperty("java.home");
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(javaHome, "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private String runJs(Path outDir) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int ec = dev.kof.runtime.KofJsRunner.run(outDir.resolve("Default.mjs"), out,
                new java.io.ByteArrayInputStream(new byte[0]), out);
        assertEquals(0, ec, "JS exit code, output: " + out);
        return out.toString(java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
    }

    private String runNative(Path outDir) throws IOException {
        try {
            Process p = new ProcessBuilder(outDir.resolve("Default/Main").toString())
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: '" + output + "'");
            return output;
        } catch (InterruptedException e) {
            throw new IOException(e);
        }
    }

    private void compileExpectOk(Path src, Path out, Target t) throws IOException {
        CompilationResult r = driver.compile(src, out, t);
        assertTrue(r.success(), t + " compile: " + r.diagnostics().getDiagnostics());
    }

    private static final String MEM021_VIA_FFI = """
            extern "libc.so.6" atoi(String x): Int

            main() {
                var n = 1
                var h = spawn { n = atoi("2"); return n }
                n = 3
                println(await h)
            }
            """;

    @Test
    void jvmFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-jvm-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-jvm-read"), Target.JVM);
        assertEquals("3.0", runJvm(t.resolve("out-jvm-read")));
    }

    @Test
    void jsFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-js-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-js-read"), Target.JS);
        assertEquals("3.0", runJs(t.resolve("out-js-read")), "JS host-FFM bridge sob spawn");
    }

    @Test
    void nativeFfiInsideSpawnAwaitReturnsValue(@TempDir Path t) throws IOException {
        assumeTrue(isLinux(), "link direto por PLT exige toolchain linux (precedente FfiNativeE2ETest)");
        Path s = t.resolve("Main-nat-read.kf");
        Files.writeString(s, FFI_SPAWN_READ);
        compileExpectOk(s, t.resolve("out-nat-read"), Target.NATIVE);
        assertEquals("3.0", runNative(t.resolve("out-nat-read")), "pthread worker + call@PLT");
    }

    @Test
    void jvmFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-jvm-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-jvm-nocap"), Target.JVM);
        assertEquals("5", runJvm(t.resolve("out-jvm-nocap")));
    }

    @Test
    void jsFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        Path s = t.resolve("Main-js-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-js-nocap"), Target.JS);
        assertEquals("5", runJs(t.resolve("out-js-nocap")));
    }

    @Test
    void nativeFfiInsideSpawnNoCapture(@TempDir Path t) throws IOException {
        assumeTrue(isLinux(), "link direto por PLT exige toolchain linux");
        Path s = t.resolve("Main-nat-nocap.kf");
        Files.writeString(s, FFI_SPAWN_NO_CAPTURE);
        compileExpectOk(s, t.resolve("out-nat-nocap"), Target.NATIVE);
        assertEquals("5", runNative(t.resolve("out-nat-nocap")));
    }

    @Test
    void ffiDoesNotBypassMem021(@TempDir Path t) throws IOException {
        // A corrida é a mesma com ou sem FFI no meio: o extern NAO e uma raiz de
        // escape do OwnershipPass — pino da interação D-MEM021-SCALAR x fase 5.
        Path s = t.resolve("Main-mem021.kf");
        Files.writeString(s, MEM021_VIA_FFI);
        for (Target target : List.of(Target.JVM, Target.NATIVE, Target.JS)) {
            CompilationResult r = driver.compile(s, t.resolve("out-mem021-" + target), target);
            assertFalse(r.success(), target + ": escrita do pai pos-spawn com extern deve falhar");
            String diags = r.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("MEM021"), target + ": esperado MEM021, obtido " + diags);
        }
    }
}
