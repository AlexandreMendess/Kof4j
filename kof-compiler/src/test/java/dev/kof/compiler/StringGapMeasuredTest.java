package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Medição comportamental do {@code known-bugs.md §424} (frente de revisão, 21/09
 * — Fatia 11; CORRIGIDO 21/09 §424). Não lê a fonte nem a matriz: compila um
 * programa Kof mínimo que usa um dos métodos de {@code String} sem lowering e
 * MEDE o resultado do compile, provando o mecanismo.
 *
 * <p>Era um teste de CARACTERIZAÇÃO de bug (JS caía no default → membro JS
 * inexistente; Native link-fail com {@code java_lang_String_matches}). Com a
 * correção §424 (gate honesto {@code STR003}) o arquivo foi mudado de propósito:
 * agora os dois alvos RECUSAM em compile-time.
 */
class StringGapMeasuredTest {

    private static final String SRC = """
        main() {
            println("abc".matches("a.*"))
        }
        """;

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    @DisplayName("§424 JS: recusa honesta STR003 (nao mais o membro JS silencioso)")
    void jsRefusesWithStr003(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC);
        CompilationResult result = driver.compile(file, tmp.resolve("out-JS"), Target.JS);
        assertFalse(result.success(),
                "§424 JS: deve falhar em compile-time (STR003), nunca compilar limpo"
                        + " e explodir em runtime (TypeError / replaceAll literal)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("STR003"), "§424 JS: esperado STR003. Diag: " + diag);
    }

    @Test
    @DisplayName("§424 Native: recusa honesta STR003 (nao mais o ld do simbolo sintetizado)")
    void nativeRefusesWithStr003(@TempDir Path tmp) throws Exception {
        Path file = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, SRC);
        CompilationResult result = driver.compile(file, tmp.resolve("out-NATIVE"), Target.NATIVE);
        assertFalse(result.success(), "§424 Native: o compile tem de FALHAR");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("STR003"), "§424 Native: esperado STR003. Diag: " + diag);
        assertFalse(diag.contains("java_lang_String_matches"),
                "§424 Native: o gate STR003 substitui o link-fail sintetizado. Diag: " + diag);
    }
}
