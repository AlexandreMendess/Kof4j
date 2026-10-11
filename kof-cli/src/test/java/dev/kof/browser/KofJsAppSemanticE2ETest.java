package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §11 fase 4/6 — a aceitação "Kof test → Playwright → Chromium → REAL app":
 * um programa kof.ui DE VERDADE é compilado para o alvo JS (KofJS), servido
 * por HTTP local (módulos ESM não carregam via file://, precedente
 * `KofJsBrowserE2ETest`) e dirigido pelas faces semânticas do §6.5 —
 * preencher → CLIQUE REAL (não o submit programático do seed) → afirmar a
 * mutação que o HANDLER Kof produziu no DOM. O laço inteiro fica provado:
 * fonte Kof → KofJS → DOM real → interação real → handler real → asserção
 * semântica real.
 *
 * <p>RED-first: pre-fatia nenhum E2E compilava um app Kof e o dirigia com
 * as faces do §6.5 — o seed só dumpa o DOM (render), sem interação real.
 */
class KofJsAppSemanticE2ETest {

    private static void assumeManagedChromiumForRealApp() {
        assumeTrue(PlaywrightLibProvider.findPlaywrightChromium() != null,
                "sem o Chromium do Playwright — rode npx playwright install chromium");
    }

    /** O app real: o onSubmit do Form muta o placeholder do Input — se o
     *  DOM final traz "depois", o handler KofJS RODOU a partir do clique. */
    private static Path realApp(Path tempDir) throws IOException {
        Path source = tempDir.resolve("App.kf");
        Files.writeString(source, """
            main() {
                var campo = Input("")
                campo.setPlaceholder("antes")
                var botao = Button("enviar")
                var f = Form(listOf(campo, botao))
                f.onSubmit(() -> campo.setPlaceholder("depois"))
                var w = Window("AppSemanticTest")
                w.bind(f)
                w.show()
            }
            """);
        CompilerDriver driver = new CompilerDriver();
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.JS);
        assertTrue(result.success(),
                "a compilação KofJS do app real deve passar: "
                        + result.diagnostics().getDiagnostics());
        assertTrue(Files.exists(outDir.resolve("index.html")),
                "o backend JS gera o index.html");
        return outDir;
    }

    private static HttpServer serve(Path dir) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/")) path = "/index.html";
            Path file = dir.resolve(path.substring(1)).normalize();
            if (!file.startsWith(dir) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] body = Files.readAllBytes(file);
            String name = file.getFileName().toString().toLowerCase();
            String mime = name.endsWith(".html") ? "text/html; charset=utf-8"
                    : name.endsWith(".mjs") || name.endsWith(".js")
                    ? "text/javascript; charset=utf-8"
                    : "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", mime);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void theRealKofJsAppAnswersARealClickThroughTheSemanticFaces(@TempDir Path tempDir)
            throws Exception {
        assumeManagedChromiumForRealApp();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        HttpServer server = serve(realApp(tempDir));
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/index.html";
            // o laço completo num ÚNICO fluxo (uma vida de página): o
            // preenchimento, o clique REAL no botão do form e a leitura do
            // placeholder que só o handler KofJS pode ter mudado
            String placeholder = p.flow("chromium", url, java.util.List.of(
                    "css=.kof-input :: fill=Kof",
                    "role=button[name=\"enviar\"] :: click",
                    "css=.kof-input :: attr=placeholder"));
            assertEquals("depois", placeholder,
                    "o handler do onSubmit não rodou a partir do CLIQUE REAL "
                            + "(o placeholder ficou '" + placeholder + "')");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void theRealAppRendersAndIsSemanticallyAssertable(@TempDir Path tempDir)
            throws Exception {
        assumeManagedChromiumForRealApp();
        PlaywrightLibProvider p = new PlaywrightLibProvider();
        HttpServer server = serve(realApp(tempDir));
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/index.html";
            // o app renderizado satisfaz os locators semânticos (o engine
            // nativo do backend resolve role/text sobre o DOM do KofJS real)
            assertEquals("true", p.assertOn(url, "role=button[name=\"enviar\"]",
                    "visible"));
            assertEquals("antes", p.assertOn(url, "css=.kof-input",
                    "attr=placeholder"));
        } finally {
            server.stop(0);
        }
    }
}
