package dev.kof.browser;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §6.3 Cypress provider (o §11 fase 5 do plano testing-platform) — E2E REAL:
 * cada face executa um spec ad-hoc GERADO através de `cypress run --headless`
 * (Chromium varrido ou Electron), sem stub e sem falso verde. O gate é o
 * probe honesto: sem binário Cypress (cache do Kof, PATH, node_modules do
 * projeto), o suíte E2E PULA com o motivo nomeado — a convenção ambiental.
 *
 * <p>RED-first: pré-fatia NÃO existia CypressProvider — a superfície §6.3
 * (probe + dumpDom + screenshot via spec gerado) não existia.
 */
class CypressProviderTest {

    private HttpServer server;
    private String url;

    @BeforeEach
    void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = "<html><body><h1 id=hdr>kof-cypress-content</h1></body></html>"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static void assumeCypress() {
        String probe = new CypressProvider().probe();
        assumeTrue(probe != null,
                "sem binário Cypress (instale via npm — o repo nunca embute o provider)");
    }

    @Test
    void probeIsHonestAboutTheBinary() {
        Path bin = CypressProvider.findCypress();
        if (bin == null) {
            assertEquals(null, new CypressProvider().probe(),
                    "sem binário o probe TEM de ser null (nunca falso verde)");
            return;
        }
        String probe = new CypressProvider().probe();
        assertNotNull(probe, "com o binário presente o probe não é null");
        assertTrue(probe.startsWith("cypress "), "o probe imprime a versão: " + probe);
    }

    @Test
    void dumpDomIsRealRenderedHtmlFromACypressRun() throws Exception {
        assumeCypress();
        CypressProvider p = new CypressProvider();
        String dom = p.dumpDom(url);
        assertTrue(dom.contains("kof-cypress-content"),
                "o DOM renderizado pelo Cypress não contém o título:\n" + dom);
        assertTrue(dom.contains("<html"), "dump não é HTML: " + dom);
    }

    @Test
    void screenshotIsRealPngFromACypressRun(@TempDir Path dir) throws Exception {
        assumeCypress();
        CypressProvider p = new CypressProvider();
        Path shot = dir.resolve("shot.png");
        p.screenshot(url, shot.toString());
        byte[] head = Files.readAllBytes(shot);
        assertTrue(head.length > 8, "screenshot não vazio");
        assertEquals(0x89, head[0] & 0xFF, "PNG magic");
        assertEquals(0x50, head[1] & 0xFF, "PNG magic 'P'");
    }

    @Test
    void screenshotOnMissingBinaryIsAnHonestFailure() {
        Path bin = CypressProvider.findCypress();
        if (bin != null) return; // com binário presente a recusa não é exercitável aqui
        CypressProvider p = new CypressProvider();
        assertThrows(Exception.class, () -> p.dumpDom("http://127.0.0.1:1/"),
                "sem binário a chamada falha com diagnóstico, nunca silêncio");
    }
}
