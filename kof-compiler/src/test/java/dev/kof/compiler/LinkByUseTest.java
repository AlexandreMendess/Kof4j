package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * R2 (fatia 1, 20/09) — "capability/link by use" generalizado ao `libm` do
 * x86-64: assim como sqlite/pthread so entram no link quando a fonte
 * usa a capacidade (usesDb/usesConcurrency), `-lm` deve entrar
 * somente quando ha chamada real a `kof_math_pow`. Antes desta fatia o ld
 * x86 ligava libm SEMPRE (comentario "sempre ligado" em NativeAssembler) —
 * o shim `call pow` do monolito virou fraco (`.weak pow`), entao o link sem
 * libm fecha e o simbolo NUNCA e alcancado (o unicos call-sites nascem do
 * scan usesPow no NativeBackend). Prova = seccao dinamica do ELF real
 * (readelf --dynamic), nunca texto de fonte.
 */
class LinkByUseTest {

    private static final String PLAIN = """
            main() {
                val x = 3
                println("plain " + x)
            }
            """;

    private static final String POW = """
            main() {
                println(math.pow(2.0, 10.0))
                println(math.pow(9.0, 0.5))
            }
            """;

    private static boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path buildNative(Path tempDir, String program) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(result.success(), "native compile deve passar: "
                + result.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.isRegularFile(bin), "ELF deve existir");
        return bin;
    }

    private String dynamicSection(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder("readelf", "--dynamic", bin.toString())
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS), "readelf nao terminou");
        return out;
    }

    private String runBinary(Path bin) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + out);
        return out;
    }

    /** Golden MEDIDO no oracle JVM do MESMO programa (regra: golden real). */
    private String jvmOracle(Path tempDir, String program) throws Exception {
        CompilerDriver driver = new CompilerDriver();
        Files.createDirectories(tempDir);
        Path source = tempDir.resolve("Oracle.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out-jvm");
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "jvm compile: " + r.diagnostics().getDiagnostics());
        ProcessBuilder pb = new ProcessBuilder("java", "-cp", outDir.toString(),
                "Default.Main", "run");
        assumeTrue(hasTool("java"), "java oracle ausente");
        Process p = pb.redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida jvm: " + out);
        return out;
    }

    @Test
    void plainProgramDoesNotLinkLibm(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = buildNative(dir, PLAIN);
        String dyn = dynamicSection(bin);
        assertTrue(dyn.contains("libc"), "esperava libc na dinamica:\n" + dyn);
        assertTrue(!dyn.contains("libm"),
                "R2: programa sem pow NAO pode ligar libm (link-by-use):\n" + dyn);
        // O gate COMPLETO da fatia: o monolito x86 so pede o que usa — um
        // programa plain nao referencia nenhuma capacidade (.so) alem da libc.
        for (String absent : new String[]{"libm", "libsqlite3", "libmariadb", "libpthread"}) {
            assertTrue(!dyn.contains(absent),
                    "R2: plain nao pode ligar " + absent + " (link-by-use):\n" + dyn);
        }
        assertEquals("plain 3", runBinary(bin), "saida nao pode mudar");
    }

    /** Capacidades continuam ligadas POR USO (os tres pins positivos). */
    @Test
    void capabilitiesStillLinkWhenUsed(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        String[][] cases = {
            {"main() {\n    var c = db.connect(\"sqlite:/tmp/opencode-r2-x.db\")\n    println(c == c)\n}\n",
             "libsqlite3"},
            {"main() {\n    spawn trabalho()\n}\ntrabalho() { println(1) }\n", "libpthread"},
            {"main() {\n    println(math.pow(2.0, 3.0))\n}\n", "libm"},
        };
        for (String[] c : cases) {
            Files.createDirectories(dir);
            Path bin = buildNative(dir, c[0]);
            String dyn = dynamicSection(bin);
            assertTrue(dyn.contains(c[1]), "uso deve ligar " + c[1] + ":\n" + dyn);
            Files.deleteIfExists(bin);
            Files.deleteIfExists(dir.resolve("Main.kf"));
        }
    }

    @Test
    void powProgramLinksLibmAndMatchesJvm(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path bin = buildNative(dir, POW);
        String dyn = dynamicSection(bin);
        assertTrue(dyn.contains("libm"),
                "R2: programa com math.pow DEVE ligar libm por uso:\n" + dyn);
        String nativeOut = runBinary(bin);
        String jvm = jvmOracle(dir.resolve("jvm"), POW);
        assertEquals(jvm, nativeOut, "paridade com o oracle JVM (medido):");
    }
}
