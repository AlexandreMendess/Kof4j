package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §396 — {@code println} CRU de um record NULL era SIGSEGV no Native x86-64
 * (a JVM imprime "null"). Descoberto 20/09 pela lane GAPS-DB no oráculo de
 * {@code orm.find} com miss (o slice devolve 0 correto; o emissor de
 * {@code String.valueOf(record)} despachava o vtable sem guard de null e o
 * {@code movq 8(%rax)} dereferenciava 0 — exit 139 medido).
 *
 * <p>Fix (emissor, mesmo sítio do dispatch de toString): guard {@code testq}
 * antes do load da vtable. O ramo nulo APENAS pula o dispatch e deixa o 0
 * como resultado — o {@code kof_print_string} do runtime já converte 0 em
 * "null" ({@code .Lkof_null_str}), mesma conversão de {@code
 * String.valueOf(null)} que o host usa. Zero mudança no caminho não-nulo
 * (freeze regra 2 — aditivo puro).
 *
 * <p>Q3: hit (caso dourado do F2b — {@code Point[x=3, y=4]}) + miss (§396) +
 * dois nulls e um hit (idempotência do label único por sítio) — JVM ==
 * Native byte-a-byte.
 */
class RecordNullPrintE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private static final String SRC = """
            record Point(Int x, Int y)

            Point? findMissing() { return null }

            Point? findHit() { return Point(3, 4) }

            main() {
                var miss = findMissing()
                println(miss)
                var hit = findHit()
                println(hit)
            }
            """;

    private static final String SRC_MULTI = """
            record Point(Int x, Int y)

            Point? findMissing() { return null }

            Point? findHit() { return Point(3, 4) }

            main() {
                var a = findMissing()
                var b = findHit()
                var c = findMissing()
                println(a)
                println(b)
                println(c)
            }
            """;

    private static final String EXPECTED = "null\nPoint[x=3, y=4]";
    private static final String EXPECTED_MULTI = "null\nPoint[x=3, y=4]\nnull";

    private String runJvm(Path source, Path outDir, String expected) throws IOException {
        CompilationResult result = driver.compile(source, outDir, Target.JVM);
        assertTrue(result.success(), "JVM deve compilar: " + result.diagnostics().getDiagnostics());
        try {
            ProcessBuilder pb = new ProcessBuilder("java", "-Dfile.encoding=UTF-8",
                    "-Dstdout.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED",
                    "-cp", outDir.toString(), "Default.Main");
            pb.redirectError(java.io.File.createTempFile("s396-stderr", ".txt"));
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: '" + output + "'");
            assertEquals(expected, output, "JVM golden (§396 oracle medido antes do fix)");
            return output;
        } catch (InterruptedException e) {
            throw new IOException("Interrupted", e);
        }
    }

    private String runNative(Path source, Path outDir, String expected) throws Exception {
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "Native deve compilar: " + result.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder(outDir.resolve("Default/Main").toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        int ec = p.waitFor();
        assertEquals(0, ec, "Native nao pode SIGSEGV nem falhar (era exit 139 pre-fix). output: '"
                + out + "'");
        assertEquals(expected, out, "Native golden §396");
        return out;
    }

    @Test
    void nullRecordPrintByteParityJvmNative(@TempDir Path tmp) throws Exception {
        Path jvmSource = tmp.resolve("JvmMain.kf");
        Files.writeString(jvmSource, SRC);
        runJvm(jvmSource, tmp.resolve("jvm-out"), EXPECTED);
        Path natSource = tmp.resolve("NativeMain.kf");
        Files.writeString(natSource, SRC);
        runNative(natSource, tmp.resolve("native-out"), EXPECTED);
    }

    @Test
    void twoNullSitesAndHitStayUniqueLabelsAndParity(@TempDir Path tmp) throws Exception {
        // Q3 idempotencia: dois sítios null no MESMO programa — o label com
        // contador por emissao nao pode colidir nem fundir as saidas.
        Path jvmSource = tmp.resolve("JvmMain.kf");
        Files.writeString(jvmSource, SRC_MULTI);
        runJvm(jvmSource, tmp.resolve("jvm-out"), EXPECTED_MULTI);
        Path natSource = tmp.resolve("NativeMain.kf");
        Files.writeString(natSource, SRC_MULTI);
        runNative(natSource, tmp.resolve("native-out"), EXPECTED_MULTI);
    }
}
