package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * FFI callbacks com ARGUMENTO {@code String} no Native x86-64 (memory-safety M1
 * unidade-4, face `S` de {@code nativeCallbackDesc}): o C entrega um {@code char*}
 * que o trampolim converte via {@code kof_ffi_from_cstr} ANTES do {@code invoke}
 * Kof — o mesmo bridge que a JVM faz no upcall (3.4-C3.4). Contrato
 * síncrono/não-escapante. Oráculo = o MESMO fonte na JVM (FFM).
 *
 * <p>Faces honestas que ficam de fora (pinned abaixo): um retorno {@code String}
 * (ownership do {@code char*} não observável no contrato síncrono) e o cross
 * riscv64/aarch64 (o trampolim cross ainda não monta o frame do bridge).
 */
class FfiNativeCallbackStrE2ETest {

    private static final String C_SRC = """
            typedef int (*si)(const char*);
            int kof_cb_strlen(const char* s, si cb) { return cb(s); }
            typedef long (*sij)(const char*, int);
            long kof_cb_strint(const char* s, int n, sij cb) { return cb(s, n); }
            typedef int (*ss)(const char*, const char*);
            int kof_cb_two(const char* a, const char* b, ss cb) { return cb(a, b); }
            """;

    private static final String CALLBACK_KOF = """
            extern "%1$s" kof_cb_strlen(s: String, cb: (String) -> Int): Int
            extern "%1$s" kof_cb_strint(s: String, n: Int, cb: (String, Int) -> Long): Long
            extern "%1$s" kof_cb_two(a: String, b: String, cb: (String, String) -> Int): Int

            main() {
                println(kof_cb_strlen("hello", (x: String) -> x.length))
                println(kof_cb_strint("abc", 10, (x: String, n: Int) -> x.length as Long + n as Long))
                println(kof_cb_two("foo", "barbar", (x: String, y: String) -> x.length + y.length))
            }
            """;

    private final CompilerDriver driver = new CompilerDriver();

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "callback host usa um .so nativo (Linux)");
        Path src = dir.resolve("libkofstr.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofstr.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para o host de callback");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0,
                "cc/gcc falhou: " + out);
        return so.toString();
    }

    @Test
    void nativeStringArgCallbacksMatchJvm(@TempDir Path dir) throws Exception {
        String lib = buildHostLib(dir);
        String kof = CALLBACK_KOF.formatted(lib);

        Path jvmSrc = dir.resolve("cbs-jvm.kf");
        Files.writeString(jvmSrc, kof);
        Path jvmOut = dir.resolve("out-cbs-jvm");
        CompilationResult rj = driver.compile(jvmSrc, jvmOut, Target.JVM);
        assertTrue(rj.success(), () -> "JVM oracle compile: " + rj.diagnostics().getDiagnostics());
        String jvm = runJvm(jvmOut);
        assertEquals("5\n13\n9", jvm, "JVM golden (String-arg callbacks)");

        Path src = dir.resolve("cbs-native.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-cbs-native");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE String-arg callbacks must bind (M1 face S): "
                + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        Process p = new ProcessBuilder(bin.toString()).directory(dir.toFile())
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), () -> "NATIVE run exit code, output: " + o);
        assertEquals(jvm, o, "JVM↔Native String-arg callback parity byte-for-byte");
    }

    /** Um retorno {@code String} continua {@code FFI001} (ownership do `char*`). */
    @Test
    void strCbReturnStaysFfi001NativeStrFile(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("ret.kf");
        Files.writeString(src, """
                extern "c" kof_cb_ret(cb: (Int) -> String): Int
                main() { println(kof_cb_ret((n: Int) -> "x")) }
                """);
        CompilationResult r = driver.compile(src, dir.resolve("out-ret"), Target.NATIVE);
        assertFalse(r.success(), "String-return callback deve ser recusado no Native");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "FFI001".equals(d.code())),
                () -> "esperado FFI001: " + r.diagnostics().getDiagnostics());
    }

    /** O cross riscv64/aarch64 ainda não monta o frame do bridge `S` → honesto FFI001. */
    @Test
    void strCbArgStaysFfi001OnCross(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("cross.kf");
        Files.writeString(src, """
                extern "c" kof_cb_strlen(s: String, cb: (String) -> Int): Int
                main() { println(kof_cb_strlen("hello", (x: String) -> x.length)) }
                """);
        for (Target t : new Target[] {Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(src, dir.resolve("out-" + t), t);
            assertFalse(r.success(), "String-arg callback deve ser recusado no cross " + t);
            assertTrue(r.diagnostics().getDiagnostics().stream()
                            .anyMatch(d -> "FFI001".equals(d.code())),
                    () -> "esperado FFI001 em " + t + ": " + r.diagnostics().getDiagnostics());
        }
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
            String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }
}
