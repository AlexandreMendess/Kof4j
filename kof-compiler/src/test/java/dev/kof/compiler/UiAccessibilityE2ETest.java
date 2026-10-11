package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-UI-A11Y (issue #789) — acessibilidade intent-based do kof.ui.
 *
 * Superfície aprovada pela mantenedora (11/10): {@code setAccessibleName(String)}
 * (nome acessível, distinto de descrição) e {@code setAnnounce(Bool)} (região
 * live polite), mais defaults automáticos — Icon decorativo, Window main, Canvas
 * imagem, alvo de clique não-Button operável por teclado. Real só no KofJS;
 * JVM/Native no-op (mesmo contrato cross-target do resto do kof.ui). O Kof nunca
 * vê vocabulário ARIA/HTML: o runtime faz o mapeamento.
 */
class UiAccessibilityE2ETest extends UiSupport {

    private static final String PROGRAM = """
            main() {
                var icon = Icon("trash")
                icon.setAccessibleName("Remover item")
                var deco = Icon("search")
                var chart = Canvas(300, 150)
                chart.setAccessibleName("Vendas por mes")
                var status = Label("")
                status.setAnnounce(true)
                var campo = Input("")
                campo.setAccessibleName("Email")
                var acao = View(Style(Palette.black, Palette.white, 8, 4))
                acao.on("click", () -> println("acao"))
                var col = Column(listOf(icon, deco, chart, status, campo, acao))
                var w = Window("Acessivel")
                w.bind(col)
                w.show()
                println("ok")
            }
            """;

    @Test
    void accessibilityLinksOnAllTargets(@TempDir Path tempDir) throws IOException {
        // A superfície é aditiva e cross-target: o mesmo programa compila e roda
        // em JVM e Native (no-op, como setId/setDisabled) sem erro.
        both(tempDir, "a11y", PROGRAM, "ok");
    }

    @Test
    void accessibilityAttributesRenderedInKofJs(@TempDir Path tempDir) throws IOException {
        Path src = tempDir.resolve("a11y-js.kf");
        Files.writeString(src, PROGRAM);
        CompilationResult js = driver.compile(src, tempDir.resolve("js"), Target.JS);
        assertTrue(js.success(), "JS compilation should succeed: " + js.diagnostics().getDiagnostics());
        String html = dev.kof.runtime.KofJsRunner.runCaptureHtml(
                tempDir.resolve("js").resolve("Default.mjs"), new java.io.ByteArrayOutputStream(),
                new java.io.ByteArrayInputStream(new byte[0]), new java.io.ByteArrayOutputStream());
        assertNotNull(html, "HTML should be captured");
        // Nome acessível por widget (Icon/Canvas/Input) — o nome é distinto da
        // descrição e sobrevive à serialização do DOM.
        assertTrue(html.contains("aria-label=\"Remover item\""), "icon accessible name: " + html);
        assertTrue(html.contains("aria-label=\"Vendas por mes\""), "canvas accessible name: " + html);
        assertTrue(html.contains("aria-label=\"Email\""), "input accessible name: " + html);
        // Canvas é uma imagem sem texto alternativo natural → role="img".
        assertTrue(html.contains("role=\"img\""), "canvas role=img: " + html);
        // Região live: updates relevantes anunciados com polidez.
        assertTrue(html.contains("aria-live=\"polite\""), "announce region: " + html);
        // Alvo de clique não-Button (View) entra na ordem de tabulação.
        assertTrue(html.contains("role=\"button\""), "clickable role=button: " + html);
        assertTrue(html.contains("tabindex=\"0\""), "clickable tabindex: " + html);
        // A primeira janela é o marco "main" da aplicação.
        assertTrue(html.contains("role=\"main\""), "window role=main: " + html);
        // Icon decorativo (sem nome) continua escondido do leitor de tela; um
        // Icon NOMEADO deixa de ser decorativo (aria-hidden vira "false").
        assertTrue(html.contains("aria-hidden=\"true\""), "decorative icon hidden: " + html);
        assertTrue(html.contains("aria-hidden=\"false\""), "named icon not hidden: " + html);
    }
}
