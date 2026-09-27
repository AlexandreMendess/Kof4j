package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D6-2 / 3.7 array {@code T[]}→C {@code ptr} no Native x86-64, com copy-in por
 * chamada (o array Kof nunca é mutado pela C — paridade com o JVM). A largura de
 * slot de cada elemento Kof iguala a largura C ({@code Long}→{@code long} 8 B,
 * {@code Double}→{@code double} 8 B, {@code Int}→{@code int} 4 B,
 * {@code Float}→{@code float} 4 B, {@code Bool}→{@code bool} 1 B), então o pack
 * é um {@code memcpy} com o tamanho do elemento. {@code String[]} (array de
 * ponteiros) e qualquer array no cross seguem {@code FFI001}.
 *
 * <p>Oráculo regra 5: o MESMO fonte roda no JVM (FFM) e no binário nativo e a
 * saída é byte-a-byte igual — a fixture C é um {@code .so} real compilado com
 * cc/gcc. Sem toolchain → skip honesto.
 */
class FfiNativeArrayE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String C_SRC = """
            #include <stdbool.h>
            long suml(long* xs, long n) { long s = 0; for (long i = 0; i < n; i++) s += xs[i]; return s; }
            double sumd(double* xs, long n) { double s = 0; for (long i = 0; i < n; i++) s += xs[i]; return s; }
            long sumi(int* xs, long n) { long s = 0; for (long i = 0; i < n; i++) s += xs[i]; return s; }
            double sumf(float* xs, long n) { double s = 0; for (long i = 0; i < n; i++) s += xs[i]; return s; }
            long sumb(bool* xs, long n) { long s = 0; for (long i = 0; i < n; i++) s += xs[i]; return s; }
            """;

    private static final String KOF = """
            extern "%1$s" suml(Long[] xs, Long n): Long
            extern "%1$s" sumd(Double[] xs, Long n): Double
            extern "%1$s" sumi(Int[] xs, Long n): Int
            extern "%1$s" sumf(Float[] xs, Long n): Double
            extern "%1$s" sumb(Bool[] xs, Long n): Int

            main() {
                var ls = new Long[3]
                ls[0] = 10
                ls[1] = 20
                ls[2] = 12
                println(suml(ls, 3))
                var neg = new Long[2]
                neg[0] = -5
                neg[1] = 7
                println(suml(neg, 2))
                println(suml(new Long[0], 0))
                var ds = new Double[2]
                ds[0] = 1.5
                ds[1] = 2.5
                println(sumd(ds, 2))
                var is = new Int[3]
                is[0] = 1
                is[1] = 2
                is[2] = 3
                println(sumi(is, 3))
                var fs = new Float[2]
                fs[0] = 1.5 as Float
                fs[1] = 2.5 as Float
                println(sumf(fs, 2))
                var bs = new Bool[3]
                bs[0] = true
                bs[1] = false
                bs[2] = true
                println(sumb(bs, 3))
            }
            """;

    private static final String GOLDEN = String.join("\n", "42", "2", "0", "4.0", "6", "4.0", "2");

    private static String buildHostLib(Path dir) throws IOException, InterruptedException {
        assumeTrue(System.getProperty("os.name", "").toLowerCase().contains("linux"),
                "FFI nativo usa um .so real (Linux + cc)");
        Path src = dir.resolve("libkofarray.c");
        Files.writeString(src, C_SRC);
        Path so = dir.resolve("libkofarray.so");
        String cc = null;
        for (String cand : new String[] {"/usr/bin/cc", "/usr/bin/gcc", "cc", "gcc"}) {
            try {
                Process p = new ProcessBuilder(cand, "--version").redirectErrorStream(true).start();
                p.getInputStream().readAllBytes();
                if (p.waitFor(15, TimeUnit.SECONDS) && p.exitValue() == 0) { cc = cand; break; }
            } catch (Exception ignored) { /* tenta o próximo */ }
        }
        assumeTrue(cc != null, "sem toolchain C (cc/gcc) para a fixture FFI");
        Process p = new ProcessBuilder(cc, "-shared", "-fPIC", "-O2",
                "-o", so.toString(), src.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes());
        assumeTrue(p.waitFor(60, TimeUnit.SECONDS) && p.exitValue() == 0, "cc falhou: " + out);
        return so.toString();
    }

    private String runNative(Path dir, String kof) throws IOException {
        Path src = dir.resolve("Main-nat.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-nat");
        CompilationResult r = driver.compile(src, out, Target.NATIVE);
        assertTrue(r.success(), () -> "NATIVE array extern must bind: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary " + bin + " deve existir");
        try {
            Process p = new ProcessBuilder(bin.toString()).directory(dir.toFile())
                    .redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "NATIVE run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private String runJvmOracle(Path dir, String kof) throws IOException {
        Path src = dir.resolve("Main-jvm.kf");
        Files.writeString(src, kof);
        Path out = dir.resolve("out-jvm");
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), () -> "JVM compile: " + r.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "--enable-native-access=ALL-UNNAMED", "-cp", out.toString(), "Default.Main")
                    .directory(dir.toFile()).redirectErrorStream(true).start();
            String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                    .replace("\r\n", "\n");
            assertEquals(0, p.waitFor(), () -> "JVM run exit code, output: " + o);
            return o.trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    @Test
    void scalarArrayBindsOnNativeAndMatchesJvmOracle(@TempDir Path dir) throws Exception {
        String so = buildHostLib(dir);
        String kof = KOF.formatted(so);
        assertEquals(GOLDEN, runNative(dir, kof), "Long[]/Double[] copy-in no Native x86-64");
        assertEquals(GOLDEN, runJvmOracle(dir, kof),
                "oráculo JVM (mesma fixture) deve concordar byte-a-byte (regra 5)");
    }

    @Test
    void stringArrayAndCrossArrayStayFfi001(@TempDir Path dir) throws IOException {
        // `String[]` é array de ponteiros (distinto do copy-in escalar) e o
        // cross ainda não tem o pack — ambos seguem FFI001 honesto (R6).
        Path str = dir.resolve("StringArray.kf");
        Files.writeString(str, """
                extern "libc.so.6" probe(String[] xs, Long n): Int
                main() { println("gap") }
                """);
        CompilationResult rs = driver.compile(str, dir.resolve("out-str"), Target.NATIVE);
        assertFalse(rs.success(), "String[] não pode virar silêncio no Native");
        assertTrue(rs.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "String[] → FFI001 honesto: " + rs.diagnostics().getDiagnostics());

        Path cross = dir.resolve("CrossArray.kf");
        Files.writeString(cross, """
                extern "libc.so.6" probe(Long[] xs, Long n): Long
                main() { println("gap") }
                """);
        CompilationResult rc = driver.compile(cross, dir.resolve("out-cross"), Target.NATIVE_RISCV64);
        assertFalse(rc.success(), "array no cross não pode virar silêncio");
        assertTrue(rc.diagnostics().getDiagnostics().toString().contains("FFI001"),
                "array cross → FFI001 honesto: " + rc.diagnostics().getDiagnostics());
    }
}
