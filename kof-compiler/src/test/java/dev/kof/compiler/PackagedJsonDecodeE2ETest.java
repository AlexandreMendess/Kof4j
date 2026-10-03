package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §571 (02/10, KofShare control/check.kf): `var d = json.decode<Record>(s)` num
 * arquivo EMPACOTADO — o typer runtime (MethodCallTyper) não conhece o namespace
 * `json` (o ramo só existia no lowerer e em BuiltinCallTyper/sa), então o local
 * do `var` ficava `ClassType("", "DeviceInfo")` e os acessos de membro
 * (`d.id()`/`d.id`) saíam com dono BARE no bytecode → o Main linkava classe
 * inexistente → `NoClassDefFoundError: DeviceInfo` no LOAD (R6: compilar limpo
 * e morrer no load é exatamente o que a suíte caça). Fix: chokepoint único em
 * {@code ExpressionTyper.inferExprType} que apaga qualificado todo ClassType de
 * pacote vazio cujo NOME resolve num símbolo do módulo com pacote (classe de
 * pacote padrão legítima — pkg "" no símbolo — intacta).
 */
class PackagedJsonDecodeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void packagedRecordDecodeMemberAccessLoadsAndRuns() throws Exception {
        Path root = tmp.resolve("src/main/kof");
        Path pkg = root.resolve("jp");
        Files.createDirectories(pkg);
        Files.writeString(pkg.resolve("model.kf"), """
                package jp

                import kof.json

                record Thing(String id, Int n)

                String decodeId(String s) {
                    var t = json.decode<Thing>(s)
                    return "id=" + t.id()
                }

                Int decodeN(String s) {
                    var t = json.decode<Thing>(s)
                    return t.n()
                }
                """);
        Files.writeString(pkg.resolve("main.kf"), """
                package jp

                import jp.model

                main() {
                    println(decodeId("{\\"id\\":\\"x1\\",\\"n\\":2}"))
                    println(decodeN("{\\"id\\":\\"x1\\",\\"n\\":2}"))
                }
                """);

        CompilationResult r = driver.compileSources(
                List.of(pkg.resolve("main.kf")), tmp.resolve("out"), Target.JVM, root);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        assertEquals("id=x1\n2", runJvm(tmp.resolve("out")));
    }

    private String runJvm(Path outDir) throws IOException {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", outDir.toString(), "Default.Main");
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
